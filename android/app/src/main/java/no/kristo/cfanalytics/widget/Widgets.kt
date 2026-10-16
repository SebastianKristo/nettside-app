package no.kristo.cfanalytics.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import no.kristo.cfanalytics.MainActivity
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.work.RefreshWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fallback colours from the design; Android 12+ uses the wallpaper (Material You) instead. */
private val FallbackColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF9C4A1A), primaryContainer = Color(0xFFFFB68A), onPrimaryContainer = Color(0xFF331200),
        tertiaryContainer = Color(0xFF4A3C5C), onTertiaryContainer = Color(0xFFECDCFF),
        surface = Color(0xFF2B2930), onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF36343B), onSurfaceVariant = Color(0xFFCAC4D0),
        secondaryContainer = Color(0xFF4A4458),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB68A), primaryContainer = Color(0xFFFFB68A), onPrimaryContainer = Color(0xFF331200),
        tertiaryContainer = Color(0xFF4A3C5C), onTertiaryContainer = Color(0xFFECDCFF),
        surface = Color(0xFF2B2930), onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF36343B), onSurfaceVariant = Color(0xFFCAC4D0),
        secondaryContainer = Color(0xFF4A4458),
    ),
)

@Composable
private fun WidgetTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme(content = content)
    else GlanceTheme(colors = FallbackColors, content = content)
}

private val DomainKey = ActionParameters.Key<String>(MainActivity.EXTRA_DOMAIN_ID)

private fun openApp(data: WidgetData) =
    actionStartActivity<MainActivity>(actionParametersOf(DomainKey to (data.domain?.id ?: "")))

private fun time(ms: Long) = SimpleDateFormat("HH:mm", Locale.forLanguageTag("nb-NO")).format(Date(ms))

@Composable
private fun Empty(context: Context) {
    Box(
        GlanceModifier.fillMaxSize().padding(16.dp)
            .background(ImageProvider(R.drawable.widget_bg_round), colorFilter = ColorFilter.tint(GlanceTheme.colors.surface)),
        contentAlignment = Alignment.Center,
    ) {
        Text(context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
    }
}

@Composable
private fun Updated(data: WidgetData, color: ColorProvider) {
    val at = data.snapshot?.fetchedAt ?: return
    if (data.isStale) Text("Oppdatert ${time(at)}", style = TextStyle(color = color, fontSize = 10.sp))
}

// MARK: Besøk (2×2) / Oversikt (4×2)

class VisitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context, id)
        provideContent {
            WidgetTheme {
                when {
                    data.domain == null -> Empty(context)
                    LocalSize.current.width >= WIDE.width -> Overview(data)
                    else -> VisitsCard(data)
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun VisitsCard(data: WidgetData) {
    val on = GlanceTheme.colors.onPrimaryContainer
    Column(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_asym), colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer))
            .padding(16.dp)
            .clickable(openApp(data)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_w_language), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(on))
            Spacer(GlanceModifier.width(6.dp))
            Text(data.domain?.name.orEmpty(), maxLines = 1, style = TextStyle(color = on, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(data.value(Metric.VISITS)?.let(::compact) ?: "—", style = TextStyle(color = on, fontSize = 44.sp, fontWeight = FontWeight.Bold))
        Text("besøk siste 24t", style = TextStyle(color = on, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Updated(data, on)
    }
}

@Composable
private fun Overview(data: WidgetData) {
    val c = GlanceTheme.colors
    Column(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_round), colorFilter = ColorFilter.tint(c.surface))
            .padding(18.dp)
            .clickable(openApp(data)),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_w_insights), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(c.primary))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                data.domain?.name.orEmpty(), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                "7d",
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_chip), colorFilter = ColorFilter.tint(c.secondaryContainer))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = c.onSurface, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Image(
                ImageProvider(R.drawable.ic_w_refresh), "Oppdater",
                GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshCallback>()),
                colorFilter = ColorFilter.tint(c.onSurface),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Bars(data.week, GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            MetricTile("Forespørsler", data.value(Metric.REQUESTS)?.let(::compact), R.drawable.widget_tile_start, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            MetricTile("Båndbredde", data.value(Metric.BYTES_MB)?.let(::compactBytes), R.drawable.widget_tile_mid, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            MetricTile("Trusler", data.value(Metric.BLOCKED)?.let(::compact), R.drawable.widget_tile_end, GlanceModifier.defaultWeight())
        }
        Updated(data, c.onSurfaceVariant)
    }
}

/** Seven rounded bars, the last one (today) in primary. */
@Composable
private fun Bars(values: List<Double>, modifier: GlanceModifier) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val c = GlanceTheme.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val fraction = (v / max).coerceIn(0.08, 1.0)
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Bottom) {
                Box(
                    GlanceModifier.fillMaxWidth().height((64 * fraction).dp)
                        .background(
                            ImageProvider(R.drawable.widget_bar),
                            colorFilter = ColorFilter.tint(if (i == values.lastIndex) c.primary else c.secondaryContainer),
                        ),
                ) {}
            }
            if (i < values.lastIndex) Spacer(GlanceModifier.width(6.dp))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String?, shape: Int, modifier: GlanceModifier) {
    val c = GlanceTheme.colors
    Column(
        modifier.background(ImageProvider(shape), colorFilter = ColorFilter.tint(c.surfaceVariant)).padding(10.dp),
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = c.onSurfaceVariant, fontSize = 11.sp))
        Text(value ?: "—", maxLines = 1, style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

// MARK: Cache-treff (2×2, sirkel)

class CacheWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(VisitsWidget.SMALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context, id)
        provideContent {
            WidgetTheme {
                if (data.domain == null) Empty(context) else CacheCircle(data)
            }
        }
    }
}

@Composable
private fun CacheCircle(data: WidgetData) {
    val on = GlanceTheme.colors.onTertiaryContainer
    Column(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_circle), colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiaryContainer))
            .clickable(openApp(data)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(R.drawable.ic_w_bolt), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(on))
        Text(data.cacheShare?.let { "${no.kristo.cfanalytics.ui.nf(it)} %" } ?: "—", style = TextStyle(color = on, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Text("cache-treff", style = TextStyle(color = on, fontSize = 12.sp))
        Updated(data, on)
    }
}

// MARK: Receivers and actions

class VisitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VisitsWidget()
}

class CacheWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CacheWidget()
}

/** Refresh button: runs the refresh worker once; it updates all widgets when done. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RefreshWorker>().build())
    }
}
