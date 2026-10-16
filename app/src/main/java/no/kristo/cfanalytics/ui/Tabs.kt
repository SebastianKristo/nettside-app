package no.kristo.cfanalytics.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.ui.theme.ChartColors

data class StatSpec(
    val metric: Metric,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val unit: Int,
    val color: Color,
    val unitText: String? = null,
)

data class ProgressSpec(val metric: Metric, @StringRes val label: Int, val color: Color)

data class TabSpec(
    @StringRes val title: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
    val stats: List<StatSpec>,
    val progress: List<ProgressSpec> = emptyList(),
    val map: Boolean = false,
    val charts: List<StatSpec>,
    /** Error sections (see Repository) that belong on this tab. */
    val sections: Set<String>,
)

private val visits = StatSpec(Metric.VISITS, R.string.m_visits, Icons.Rounded.Group, R.string.u_visits, ChartColors.Pink)
private val requests = StatSpec(Metric.REQUESTS, R.string.m_requests, Icons.Rounded.SwapVert, R.string.u_none, ChartColors.Blue)
private val bytes = StatSpec(Metric.BYTES_MB, R.string.m_bytes, Icons.Rounded.CloudDownload, R.string.u_none, ChartColors.Amber, "MB")
private val pageViews = StatSpec(Metric.PAGE_VIEWS, R.string.m_pageviews, Icons.Rounded.Visibility, R.string.u_views, ChartColors.Pink)
private val uniques = StatSpec(Metric.UNIQUES, R.string.m_uniques, Icons.Rounded.Person, R.string.u_visitors, ChartColors.Purple)
private val p95 = StatSpec(Metric.P95_MS, R.string.m_p95, Icons.Rounded.Timer, R.string.u_none, ChartColors.Amber, "ms")
private val hitRate = StatSpec(Metric.CACHE_HIT_RATE, R.string.m_hit, Icons.Rounded.Bolt, R.string.u_none, ChartColors.Green, "%")
private val dns = StatSpec(Metric.DNS_QUERIES, R.string.m_dns, Icons.Rounded.Dns, R.string.u_lookups, ChartColors.Blue)

val TABS = listOf(
    TabSpec(
        R.string.tab_traffic, Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Rounded.ShowChart,
        stats = listOf(
            visits, requests, bytes,
            StatSpec(Metric.COUNTRIES, R.string.m_countries, Icons.Rounded.Public, R.string.u_none, ChartColors.Green),
        ),
        charts = listOf(visits, requests, bytes),
        sections = setOf("Trafikk"),
    ),
    TabSpec(
        R.string.tab_visits, Icons.Outlined.Group, Icons.Rounded.Group,
        stats = listOf(pageViews, uniques),
        map = true,
        charts = listOf(pageViews, uniques),
        sections = setOf("Trafikk"),
    ),
    TabSpec(
        R.string.tab_performance, Icons.Outlined.Speed, Icons.Rounded.Speed,
        stats = listOf(
            StatSpec(Metric.P50_MS, R.string.m_p50, Icons.Rounded.Timer, R.string.u_none, ChartColors.Green, "ms"),
            p95,
            StatSpec(Metric.RATE_4XX, R.string.m_4xx, Icons.Rounded.Warning, R.string.u_none, ChartColors.Amber, "%"),
            StatSpec(Metric.RATE_5XX, R.string.m_5xx, Icons.Rounded.Dangerous, R.string.u_none, ChartColors.Red, "%"),
            StatSpec(Metric.CACHED_REQUESTS, R.string.m_cached, Icons.Rounded.Bolt, R.string.u_requests, ChartColors.Green),
            StatSpec(Metric.CACHED_MB, R.string.m_cached_mb, Icons.Rounded.CloudDone, R.string.u_none, ChartColors.Blue, "MB"),
        ),
        progress = listOf(
            ProgressSpec(Metric.CACHE_HIT_RATE, R.string.m_hit_short, ChartColors.Green),
            ProgressSpec(Metric.BANDWIDTH_SAVED, R.string.m_saved, ChartColors.Blue),
        ),
        charts = listOf(p95, hitRate),
        sections = setOf("Trafikk", "Svartid"),
    ),
    TabSpec(
        R.string.tab_security, Icons.Outlined.Shield, Icons.Rounded.Shield,
        stats = listOf(
            dns,
            StatSpec(Metric.BLOCKED, R.string.m_blocked_short, Icons.Rounded.Shield, R.string.u_requests, ChartColors.Red),
        ),
        progress = listOf(ProgressSpec(Metric.NXDOMAIN_RATE, R.string.m_nx, ChartColors.Amber)),
        charts = listOf(dns),
        sections = setOf("DNS-oppslag", "Stoppede forespørsler"),
    ),
)
