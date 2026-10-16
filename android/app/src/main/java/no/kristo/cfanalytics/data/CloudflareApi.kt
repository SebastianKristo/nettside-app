package no.kristo.cfanalytics.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

class ApiException(message: String) : Exception(message)

class CloudflareApi(private val client: HttpClient = HttpClient(OkHttp)) {
    private val base = "https://api.cloudflare.com/client/v4"

    /** GET /user/tokens/verify — succeeds only for an active token. */
    suspend fun verifyToken(token: String) {
        val body = runCatching { client.get("$base/user/tokens/verify") { bearerAuth(token) }.bodyAsText() }
            .getOrElse { throw ApiException("Nettverksfeil: ${it.message}") }
        val json = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: throw ApiException("Uventet svar fra Cloudflare")
        val ok = (json["success"] as? JsonPrimitive)?.booleanOrNull == true &&
            json.path("result", "status").str() == "active"
        if (!ok) throw ApiException(firstError(json) ?: "Token er ugyldig eller ikke aktiv")
    }

    /**
     * Runs one GraphQL query and returns the first zone object.
     * Throws [ApiException] when the query (or the zone) returned errors.
     */
    suspend fun zoneQuery(token: String, query: String, variables: JsonObject): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val body = runCatching {
            client.post("$base/graphql") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }.bodyAsText()
        }.getOrElse { throw ApiException("Nettverksfeil: ${it.message}") }
        val json = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
            ?: throw ApiException("Uventet svar fra Cloudflare")
        firstError(json)?.let { throw ApiException(it) }
        val zones = json.path("data", "viewer", "zones") as? JsonArray
        return zones?.firstOrNull() as? JsonObject ?: throw ApiException("Fant ikke sonen – sjekk Zone ID og token")
    }

    private fun firstError(json: JsonObject): String? {
        val errors = json["errors"] as? JsonArray ?: return null
        return errors.firstNotNullOfOrNull { (it as? JsonObject)?.get("message")?.str() }
    }

    companion object {
        val HTTP_QUERY = """
query(${'$'}zone: String!, ${'$'}since: Time!, ${'$'}until: Time!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    totals: httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      count sum { edgeResponseBytes visits }
    }
    hourly: httpRequestsAdaptiveGroups(limit: 48, orderBy: [datetimeHour_ASC], filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      count dimensions { datetimeHour } sum { visits }
    }
    countries: httpRequestsAdaptiveGroups(limit: 250, orderBy: [sum_visits_DESC], filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      sum { visits } dimensions { clientCountryName }
    }
    pages: httpRequestsAdaptiveGroups(limit: 5, orderBy: [count_DESC], filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until, requestSource: "eyeball" }) {
      count dimensions { clientRequestPath }
    }
    pageviews: httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until, edgeResponseContentTypeName: "html" }) {
      count
    }
    status: httpRequestsAdaptiveGroups(limit: 100, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      count dimensions { edgeResponseStatus }
    }
    cache: httpRequestsAdaptiveGroups(limit: 20, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      count sum { edgeResponseBytes } dimensions { cacheStatus }
    }
  } }
}"""

        val PERF_QUERY = """
query(${'$'}zone: String!, ${'$'}since: Time!, ${'$'}until: Time!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    perf: httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      quantiles { edgeTimeToFirstByteMsP50 edgeTimeToFirstByteMsP95 }
    }
  } }
}"""

        val DNS_QUERY = """
query(${'$'}zone: String!, ${'$'}since: Time!, ${'$'}until: Time!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    dns: dnsAnalyticsAdaptiveGroups(limit: 20, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) {
      count dimensions { responseCode }
    }
  } }
}"""

        val FIREWALL_QUERY = """
query(${'$'}zone: String!, ${'$'}since: Time!, ${'$'}until: Time!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    firewall: firewallEventsAdaptiveGroups(limit: 1, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until, action_in: ["block", "challenge", "managed_challenge", "js_challenge"] }) {
      count
    }
  } }
}"""

        val DAILY_QUERY = """
query(${'$'}zone: String!, ${'$'}from: Date!, ${'$'}to: Date!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    days: httpRequests1dGroups(limit: 400, orderBy: [date_ASC], filter: { date_geq: ${'$'}from, date_leq: ${'$'}to }) {
      dimensions { date }
      sum { requests bytes cachedRequests cachedBytes pageViews threats }
      uniq { uniques }
    }
  } }
}"""

        val PING_QUERY = """
query(${'$'}zone: String!, ${'$'}since: Time!, ${'$'}until: Time!) {
  viewer { zones(filter: { zoneTag: ${'$'}zone }) {
    httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: ${'$'}since, datetime_lt: ${'$'}until }) { count }
  } }
}"""
    }
}

// --- small JSON helpers -------------------------------------------------------

fun JsonElement?.path(vararg keys: String): JsonElement? {
    var cur = this
    for (k in keys) cur = (cur as? JsonObject)?.get(k) ?: return null
    return cur
}

fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
fun JsonElement?.rows(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
