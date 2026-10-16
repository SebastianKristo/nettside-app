package no.kristo.cfanalytics.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import no.kristo.cfanalytics.CfApp
import no.kristo.cfanalytics.data.Domain
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.data.Period
import no.kristo.cfanalytics.data.Range
import no.kristo.cfanalytics.data.Snapshot
import no.kristo.cfanalytics.ui.buildChart
import no.kristo.cfanalytics.ui.nf
import no.kristo.cfanalytics.ui.seriesFor

private val Context.widgetStore by preferencesDataStore("widgets")

/** Which domain each widget instance shows (per appWidgetId). */
object WidgetPrefs {
    private fun key(appWidgetId: Int) = stringPreferencesKey("domain_$appWidgetId")

    suspend fun domainId(context: Context, appWidgetId: Int): String? =
        context.widgetStore.data.first()[key(appWidgetId)]

    suspend fun setDomainId(context: Context, appWidgetId: Int, domainId: String) {
        context.widgetStore.edit { it[key(appWidgetId)] = domainId }
    }
}

/** Everything a widget draws, read from the app's cache (the worker refreshes it every 30 min). */
data class WidgetData(
    val domain: Domain?,
    val snapshot: Snapshot?,
    /** Daily visits for the last 7 days, oldest first. */
    val week: List<Double>,
) {
    val isStale: Boolean
        get() = snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 45 * 60_000

    fun value(m: Metric): Double? = snapshot?.values?.get(m)

    val cacheShare: Double?
        get() {
            val req = value(Metric.REQUESTS) ?: return null
            val cached = value(Metric.CACHED_REQUESTS) ?: return null
            return if (req > 0) cached / req * 100 else null
        }

    companion object {
        suspend fun load(context: Context, id: GlanceId): WidgetData {
            val repo = (context.applicationContext as CfApp).repository
            val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
            val prefs = repo.prefs.current()
            val chosen = appWidgetId?.let { WidgetPrefs.domainId(context, it) }
            val domain = prefs.domains.firstOrNull { it.id == chosen }
                ?: prefs.domains.firstOrNull { it.id == prefs.selectedId }
                ?: prefs.domains.firstOrNull()
                ?: return WidgetData(null, null, emptyList())
            val snapshot = repo.cachedSnapshot(domain.id, Range.LAST_24H)
            val history = repo.history(domain.id, Period.D7.days)
            val week = buildChart(seriesFor(history, Metric.VISITS), Metric.VISITS, Period.D7).bars
            return WidgetData(domain, snapshot, week)
        }

        suspend fun updateAll(context: Context) {
            VisitsWidget().updateAll(context)
            CacheWidget().updateAll(context)
        }
    }
}

/** Compact nb-NO numbers: 12,4k · 3,2 mill. */
fun compact(v: Double): String = when {
    v >= 1_000_000 -> "${nf(v / 1_000_000, 1)} mill."
    v >= 10_000 -> "${nf(v / 1_000)}k"
    v >= 1_000 -> "${nf(v / 1_000, 1)}k"
    else -> nf(v)
}

/** Megabytes as MB or GB: 3,2 GB. */
fun compactBytes(mb: Double): String = if (mb >= 1_000) "${nf(mb / 1_000, 1)} GB" else "${nf(mb, if (mb < 10) 1 else 0)} MB"
