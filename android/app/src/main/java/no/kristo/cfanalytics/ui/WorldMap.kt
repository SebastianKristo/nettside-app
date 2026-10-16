package no.kristo.cfanalytics.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.kristo.cfanalytics.data.CountryStat
import java.util.Locale

/** One country from shared/countries.json (world-atlas 110m). `rings` are flat [lon, lat, …] lists. */
@Serializable
data class CountryShape(val code: String, val name: String, val lat: Double, val lon: Double, val rings: List<List<Double>>)

object WorldData {
    @Volatile private var cache: List<CountryShape>? = null

    fun load(context: Context): List<CountryShape> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val text = context.assets.open("countries.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<List<CountryShape>>(text)
        }.getOrDefault(emptyList()).also { cache = it }
    }

    fun find(code: String): CountryShape? = cache?.firstOrNull { it.code == code }
}

fun countryName(code: String): String =
    WorldData.find(code)?.name
        ?: Locale("", code).getDisplayCountry(NB).takeIf { it.isNotBlank() && it != code }
        ?: code

/** Equirectangular projection matching the design: lon −180…180, lat 78…−60 (360 × 138). */
private fun project(lon: Double, lat: Double, w: Float, h: Float) =
    Offset(((lon + 180) / 360 * w).toFloat(), ((78 - lat) / 138 * h).toFloat())

private fun ringPoints(ring: List<Double>, w: Float, h: Float): List<Offset> =
    (0 until ring.size / 2).map { project(ring[it * 2], ring[it * 2 + 1], w, h) }

private fun contains(points: List<Offset>, p: Offset): Boolean {
    var inside = false
    var j = points.lastIndex
    for (i in points.indices) {
        val a = points[i]; val b = points[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/**
 * Choropleth world map: countries with traffic in [traffic] (alpha 0.35–0.85 by share), the selected one in [accent],
 * the rest in [land]. Bubbles sized by visits sit on top; tapping a country or a bubble selects it.
 */
@Composable
fun WorldMap(
    countries: List<CountryStat>,
    selected: Int,
    onSelect: (Int) -> Unit,
    land: Color,
    traffic: Color,
    accent: Color,
    border: Color,
    modifier: Modifier = Modifier,
) {
    val shapes = WorldData.load(LocalContext.current)
    val density = LocalDensity.current
    val maxV = countries.maxOfOrNull { it.visits }?.coerceAtLeast(1L) ?: 1L
    val index = remember(countries) { countries.withIndex().associate { (i, c) -> c.code to i } }
    fun radiusPx(v: Long) = with(density) { (10 + v.toFloat() / maxV * 16).dp.toPx() / 2 }
    fun centre(code: String, w: Float, h: Float) = WorldData.find(code)?.let { project(it.lon, it.lat, w, h) }

    var cachedSize = Size.Zero
    var paths: List<Pair<CountryShape, Path>> = emptyList()
    fun pathsFor(size: Size): List<Pair<CountryShape, Path>> {
        if (size != cachedSize) {
            cachedSize = size
            paths = shapes.map { shape ->
                shape to Path().apply {
                    shape.rings.forEach { ring ->
                        val pts = ringPoints(ring, size.width, size.height)
                        if (pts.size >= 3) {
                            moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                    }
                }
            }
        }
        return paths
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(360f / 138f)
            .pointerInput(countries) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val bubble = countries.withIndex()
                        .mapNotNull { (i, c) -> centre(c.code, w, h)?.let { i to (it - tap).getDistance() - radiusPx(c.visits) } }
                        .minByOrNull { it.second }
                    if (bubble != null && bubble.second < 8.dp.toPx()) {
                        onSelect(bubble.first); return@detectTapGestures
                    }
                    val hit = shapes.firstOrNull { s -> s.rings.any { contains(ringPoints(it, w, h), tap) } }
                    hit?.let { index[it.code] }?.let(onSelect)
                }
            }
    ) {
        val strokeW = 0.3.dp.toPx()
        pathsFor(size).forEach { (shape, path) ->
            val i = index[shape.code]
            val fill = when {
                i == null -> land
                i == selected -> accent
                else -> traffic.copy(alpha = 0.35f + countries[i].visits.toFloat() / maxV * 0.5f)
            }
            drawPath(path, fill)
            drawPath(path, border, style = Stroke(strokeW))
        }
        countries.forEachIndexed { i, c ->
            val center = centre(c.code, size.width, size.height) ?: return@forEachIndexed
            val r = radiusPx(c.visits)
            drawCircle(if (i == selected) accent else accent.copy(alpha = 0.3f), r, center)
            drawCircle(accent, r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}
