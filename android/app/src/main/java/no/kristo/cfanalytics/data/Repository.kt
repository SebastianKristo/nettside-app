package no.kristo.cfanalytics.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class Repository(
    private val api: CloudflareApi,
    private val dao: AnalyticsDao,
    val prefs: PrefsStore,
    private val tokens: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // --- domains ---------------------------------------------------------------

    /** Validates against Cloudflare and stores the domain; throws [ApiException] with a user-facing message. */
    suspend fun addDomain(name: String, zoneId: String, token: String): Domain {
        api.verifyToken(token)
        val now = Instant.now()
        api.zoneQuery(token, CloudflareApi.PING_QUERY, timeVars(zoneId, now.minus(1, ChronoUnit.HOURS), now))
        val domain = Domain(UUID.randomUUID().toString(), name, zoneId)
        tokens.put(domain.id, token)
        val current = prefs.current().domains
        prefs.setDomains(current + domain)
        prefs.setSelected(domain.id)
        return domain
    }

    suspend fun removeDomain(domain: Domain) {
        val p = prefs.current()
        val left = p.domains.filterNot { it.id == domain.id }
        prefs.setDomains(left)
        if (p.selectedId == domain.id) prefs.setSelected(left.firstOrNull()?.id)
        tokens.remove(domain.id)
        dao.deletePoints(domain.id)
        dao.deleteSnapshots(domain.id)
    }

    // --- cache -----------------------------------------------------------------

    suspend fun cachedSnapshot(domainId: String, range: Range): Snapshot? =
        dao.snapshot(domainId, range.name)?.let { runCatching { json.decodeFromString<Snapshot>(it.json) }.getOrNull() }

    /** All stored daily values for the last [days] days, keyed by metric and date. */
    suspend fun history(domainId: String, days: Int): Map<Metric, Map<LocalDate, Double>> {
        val from = LocalDate.now(zone).minusDays(days.toLong())
        return dao.points(domainId, from.toString())
            .mapNotNull { p -> runCatching { Metric.valueOf(p.metric) }.getOrNull()?.let { it to p } }
            .groupBy({ it.first }, { LocalDate.parse(it.second.date) to it.second.value })
            .mapValues { (_, v) -> v.toMap() }
    }

    // --- network ---------------------------------------------------------------

    /** Fetches live numbers plus daily history, stores both, and returns the snapshot. */
    suspend fun refresh(domain: Domain, range: Range): Snapshot {
        val token = tokens.get(domain.id) ?: throw ApiException("Mangler API-token for ${domain.name}")
        val snapshot = fetchSnapshot(domain, token, range)
        dao.upsertSnapshot(SnapshotCache(domain.id, range.name, json.encodeToString(snapshot)))

        runCatching { fetchDaily(domain, token) }
        val today = LocalDate.now(zone).toString()
        // Today's point comes from the "I dag" numbers; for the 24 h view we only keep metrics that are rates/latency.
        val points = snapshot.values
            .filter { (m, _) -> range == Range.TODAY || m.aggregate == Aggregate.MAX }
            .map { (m, v) -> DailyPoint(domain.id, today, m.name, v) }
        dao.upsertPoints(points)
        return snapshot
    }

    private suspend fun fetchDaily(domain: Domain, token: String) {
        val to = LocalDate.now(zone)
        val from = to.minusDays(Period.Y1.days.toLong())
        val vars = buildJsonObject {
            put("zone", domain.zoneId); put("from", from.toString()); put("to", to.toString())
        }
        val z = api.zoneQuery(token, CloudflareApi.DAILY_QUERY, vars)
        val points = z["days"].rows().flatMap { row ->
            val date = row.path("dimensions", "date").str() ?: return@flatMap emptyList()
            fun p(m: Metric, v: Double?) = v?.let { DailyPoint(domain.id, date, m.name, it) }
            val requests = row.path("sum", "requests").num()
            val cached = row.path("sum", "cachedRequests").num()
            val bytes = row.path("sum", "bytes").num()
            val cachedBytes = row.path("sum", "cachedBytes").num()
            listOfNotNull(
                p(Metric.REQUESTS, requests),
                p(Metric.BYTES_MB, bytes?.div(MB)),
                p(Metric.PAGE_VIEWS, row.path("sum", "pageViews").num()),
                p(Metric.UNIQUES, row.path("uniq", "uniques").num()),
                p(Metric.CACHED_REQUESTS, cached),
                p(Metric.CACHED_MB, cachedBytes?.div(MB)),
                p(Metric.BLOCKED, row.path("sum", "threats").num()),
                p(Metric.CACHE_HIT_RATE, if (requests != null && cached != null && requests > 0) cached / requests * 100 else null),
            )
        }
        dao.upsertPoints(points)
    }

    private suspend fun fetchSnapshot(domain: Domain, token: String, range: Range): Snapshot = coroutineScope {
        val until = Instant.now()
        val since = when (range) {
            Range.LAST_24H -> until.minus(24, ChronoUnit.HOURS)
            Range.TODAY -> LocalDate.now(zone).atStartOfDay(zone).toInstant()
        }
        val vars = timeVars(domain.zoneId, since, until)
        val http = async { runCatching { api.zoneQuery(token, CloudflareApi.HTTP_QUERY, vars) } }
        val perf = async { runCatching { api.zoneQuery(token, CloudflareApi.PERF_QUERY, vars) } }
        val dns = async { runCatching { api.zoneQuery(token, CloudflareApi.DNS_QUERY, vars) } }
        val fw = async { runCatching { api.zoneQuery(token, CloudflareApi.FIREWALL_QUERY, vars) } }

        val values = mutableMapOf<Metric, Double>()
        val errors = mutableListOf<QueryError>()
        var hourly = emptyList<Long>()
        var countries = emptyList<CountryStat>()
        var pages = emptyList<PageStat>()

        http.await().onSuccess { z ->
            val requests = z["totals"].rows().firstOrNull()?.get("count").num() ?: 0.0
            val bytes = z["totals"].rows().firstOrNull().path("sum", "edgeResponseBytes").num() ?: 0.0
            values[Metric.REQUESTS] = requests
            values[Metric.BYTES_MB] = bytes / MB
            values[Metric.VISITS] = z["totals"].rows().firstOrNull().path("sum", "visits").num() ?: 0.0
            values[Metric.UNIQUES] = values.getValue(Metric.VISITS)
            values[Metric.PAGE_VIEWS] = z["pageviews"].rows().firstOrNull()?.get("count").num() ?: 0.0

            hourly = z["hourly"].rows().map { it.path("sum", "visits").num()?.toLong() ?: 0L }
            val allCountries = z["countries"].rows().mapNotNull { r ->
                val code = r.path("dimensions", "clientCountryName").str()?.takeIf { it.isNotBlank() && it != "XX" && it != "T1" }
                code?.let { CountryStat(it, r.path("sum", "visits").num()?.toLong() ?: 0L) }
            }.filter { it.visits > 0 }
            values[Metric.COUNTRIES] = allCountries.size.toDouble()
            countries = allCountries.take(10)
            pages = z["pages"].rows().mapNotNull { r ->
                r.path("dimensions", "clientRequestPath").str()?.let { PageStat(it, r["count"].num()?.toLong() ?: 0L) }
            }

            val status = z["status"].rows().map { (it.path("dimensions", "edgeResponseStatus").num() ?: 0.0).toInt() to (it["count"].num() ?: 0.0) }
            val statusTotal = status.sumOf { it.second }
            if (statusTotal > 0) {
                values[Metric.RATE_4XX] = status.filter { it.first in 400..499 }.sumOf { it.second } / statusTotal * 100
                values[Metric.RATE_5XX] = status.filter { it.first in 500..599 }.sumOf { it.second } / statusTotal * 100
            } else {
                values[Metric.RATE_4XX] = 0.0; values[Metric.RATE_5XX] = 0.0
            }

            val cache = z["cache"].rows().map { Triple(it.path("dimensions", "cacheStatus").str().orEmpty(), it["count"].num() ?: 0.0, it.path("sum", "edgeResponseBytes").num() ?: 0.0) }
            val cacheTotal = cache.sumOf { it.second }
            val cacheBytesTotal = cache.sumOf { it.third }
            val cached = cache.filter { it.first in CACHED_STATUSES }
            values[Metric.CACHED_REQUESTS] = cached.sumOf { it.second }
            values[Metric.CACHED_MB] = cached.sumOf { it.third } / MB
            values[Metric.CACHE_HIT_RATE] = if (cacheTotal > 0) cache.filter { it.first == "hit" }.sumOf { it.second } / cacheTotal * 100 else 0.0
            values[Metric.BANDWIDTH_SAVED] = if (cacheBytesTotal > 0) cached.sumOf { it.third } / cacheBytesTotal * 100 else 0.0
        }.onFailure { errors += QueryError("Trafikk", it.message.orEmpty()) }

        perf.await().onSuccess { z ->
            val q = z["perf"].rows().firstOrNull()?.get("quantiles")
            q.path("edgeTimeToFirstByteMsP50").num()?.let { values[Metric.P50_MS] = it }
            q.path("edgeTimeToFirstByteMsP95").num()?.let { values[Metric.P95_MS] = it }
        }.onFailure { errors += QueryError("Svartid", it.message.orEmpty()) }

        dns.await().onSuccess { z ->
            val rows = z["dns"].rows().map { it.path("dimensions", "responseCode").str().orEmpty() to (it["count"].num() ?: 0.0) }
            val total = rows.sumOf { it.second }
            values[Metric.DNS_QUERIES] = total
            values[Metric.NXDOMAIN_RATE] = if (total > 0) rows.filter { it.first == "NXDOMAIN" }.sumOf { it.second } / total * 100 else 0.0
        }.onFailure { errors += QueryError("DNS-oppslag", it.message.orEmpty()) }

        fw.await().onSuccess { z ->
            values[Metric.BLOCKED] = z["firewall"].rows().firstOrNull()?.get("count").num() ?: 0.0
        }.onFailure { errors += QueryError("Stoppede forespørsler", it.message.orEmpty()) }

        if (errors.size == 4) throw ApiException(errors.first().message)

        Snapshot(
            fetchedAt = System.currentTimeMillis(),
            values = values,
            hourly = hourly,
            countries = countries,
            pages = pages,
            okQueries = 4 - errors.size,
            failedQueries = errors.size,
            errors = errors,
        )
    }

    private fun timeVars(zoneId: String, since: Instant, until: Instant): JsonObject = buildJsonObject {
        put("zone", zoneId)
        put("since", since.truncatedTo(ChronoUnit.SECONDS).toString())
        put("until", until.truncatedTo(ChronoUnit.SECONDS).toString())
    }

    private companion object {
        const val MB = 1_000_000.0
        val CACHED_STATUSES = setOf("hit", "stale", "revalidated", "updating")
    }
}
