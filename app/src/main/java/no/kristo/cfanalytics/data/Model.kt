package no.kristo.cfanalytics.data

import kotlinx.serialization.Serializable

@Serializable
data class Domain(val id: String, val name: String, val zoneId: String)

enum class Range { LAST_24H, TODAY }

enum class Period(val days: Int, val bars: Int) {
    D7(7, 7), D30(30, 30), D90(90, 45), Y1(364, 52);

    val bucketDays: Int get() = days / bars
}

/** How a metric is combined over time: summed counts, or point values like rates/latency. */
enum class Aggregate { SUM, MAX }

enum class Metric(val aggregate: Aggregate, val decimals: Int = 0) {
    VISITS(Aggregate.SUM),
    REQUESTS(Aggregate.SUM),
    BYTES_MB(Aggregate.SUM, 2),
    COUNTRIES(Aggregate.MAX),
    PAGE_VIEWS(Aggregate.SUM),
    UNIQUES(Aggregate.SUM),
    P50_MS(Aggregate.MAX),
    P95_MS(Aggregate.MAX),
    RATE_4XX(Aggregate.MAX, 2),
    RATE_5XX(Aggregate.MAX, 2),
    CACHED_REQUESTS(Aggregate.SUM),
    CACHED_MB(Aggregate.SUM, 2),
    CACHE_HIT_RATE(Aggregate.MAX, 1),
    BANDWIDTH_SAVED(Aggregate.MAX, 2),
    DNS_QUERIES(Aggregate.SUM),
    BLOCKED(Aggregate.SUM),
    NXDOMAIN_RATE(Aggregate.MAX, 2),
}

@Serializable
data class CountryStat(val code: String, val visits: Long)

@Serializable
data class PageStat(val path: String, val count: Long)

@Serializable
data class QueryError(val section: String, val message: String)

/** Live numbers for one domain and range. A metric missing from [values] means its query failed. */
@Serializable
data class Snapshot(
    val fetchedAt: Long,
    val values: Map<Metric, Double>,
    val hourly: List<Long>,
    val countries: List<CountryStat>,
    val pages: List<PageStat>,
    val okQueries: Int,
    val failedQueries: Int,
    val errors: List<QueryError>,
)
