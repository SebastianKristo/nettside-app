package no.kristo.cfanalytics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import no.kristo.cfanalytics.data.CountryStat
import java.util.Locale

/** Equirectangular dot matrix (64 × 26): per row, inclusive column ranges that are land. */
private val LAND = listOf(
    listOf(14 to 20, 22 to 28, 50 to 56), listOf(10 to 20, 22 to 29, 44 to 58),
    listOf(3 to 20, 23 to 28, 33 to 36, 40 to 63), listOf(2 to 19, 23 to 27, 32 to 36, 38 to 63),
    listOf(5 to 8, 10 to 21, 31 to 32, 33 to 62), listOf(9 to 23, 31 to 60), listOf(10 to 24, 31 to 58),
    listOf(11 to 22, 31 to 35, 36 to 57), listOf(12 to 21, 31 to 55), listOf(13 to 20, 29 to 54),
    listOf(14 to 19, 29 to 53), listOf(15 to 19, 29 to 40, 44 to 48, 49 to 52),
    listOf(16 to 19, 29 to 40, 45 to 47, 49 to 52), listOf(18 to 20, 28 to 41, 50 to 51),
    listOf(18 to 23, 30 to 40, 50 to 53), listOf(18 to 25, 33 to 39, 49 to 58), listOf(18 to 26, 34 to 40, 52 to 58),
    listOf(19 to 26, 34 to 39, 53 to 58), listOf(20 to 25, 34 to 38, 39 to 39, 52 to 59),
    listOf(20 to 24, 34 to 37, 39 to 39, 52 to 59), listOf(20 to 23, 34 to 37, 52 to 59),
    listOf(20 to 22, 35 to 36, 53 to 58), listOf(20 to 22, 56 to 57, 61 to 62), listOf(20 to 21, 61 to 61), listOf(20 to 21),
)

/** Approximate country centroids (lat, lon) by ISO 3166-1 alpha-2. */
val CENTROIDS: Map<String, Pair<Double, Double>> = mapOf(
    "US" to (39.0 to -98.0), "CA" to (56.0 to -106.0), "MX" to (23.0 to -102.0), "BR" to (-10.0 to -53.0),
    "AR" to (-34.0 to -64.0), "CL" to (-30.0 to -71.0), "CO" to (4.0 to -72.0), "PE" to (-10.0 to -76.0),
    "VE" to (8.0 to -66.0), "EC" to (-2.0 to -78.0), "UY" to (-33.0 to -56.0), "CU" to (22.0 to -80.0),
    "NO" to (61.0 to 9.0), "SE" to (62.0 to 15.0), "DK" to (56.0 to 10.0), "FI" to (64.0 to 26.0),
    "IS" to (65.0 to -18.0), "GB" to (54.0 to -2.0), "IE" to (53.0 to -8.0), "FR" to (46.0 to 2.0),
    "DE" to (51.0 to 10.0), "NL" to (52.5 to 5.75), "BE" to (50.8 to 4.0), "LU" to (49.75 to 6.17),
    "CH" to (47.0 to 8.0), "AT" to (47.3 to 13.3), "IT" to (42.8 to 12.8), "ES" to (40.0 to -4.0),
    "PT" to (39.5 to -8.0), "PL" to (52.0 to 20.0), "CZ" to (49.75 to 15.5), "SK" to (48.7 to 19.5),
    "HU" to (47.0 to 20.0), "RO" to (46.0 to 25.0), "BG" to (43.0 to 25.0), "GR" to (39.0 to 22.0),
    "TR" to (39.0 to 35.0), "UA" to (49.0 to 32.0), "RU" to (60.0 to 100.0), "BY" to (53.0 to 28.0),
    "LT" to (56.0 to 24.0), "LV" to (57.0 to 25.0), "EE" to (59.0 to 26.0), "RS" to (44.0 to 21.0),
    "HR" to (45.2 to 15.5), "SI" to (46.1 to 14.8), "IL" to (31.5 to 34.75), "SA" to (25.0 to 45.0),
    "AE" to (24.0 to 54.0), "IR" to (32.0 to 53.0), "IQ" to (33.0 to 44.0), "EG" to (27.0 to 30.0),
    "MA" to (32.0 to -5.0), "DZ" to (28.0 to 3.0), "TN" to (34.0 to 9.0), "NG" to (10.0 to 8.0),
    "GH" to (8.0 to -2.0), "KE" to (1.0 to 38.0), "ET" to (8.0 to 38.0), "ZA" to (-29.0 to 24.0),
    "IN" to (20.0 to 77.0), "PK" to (30.0 to 70.0), "BD" to (24.0 to 90.0), "CN" to (35.0 to 105.0),
    "JP" to (36.0 to 138.0), "KR" to (36.0 to 128.0), "TW" to (23.5 to 121.0), "HK" to (22.25 to 114.17),
    "SG" to (1.37 to 103.8), "MY" to (2.5 to 112.5), "TH" to (15.0 to 100.0), "VN" to (16.0 to 106.0),
    "PH" to (13.0 to 122.0), "ID" to (-5.0 to 120.0), "AU" to (-25.0 to 134.0), "NZ" to (-41.0 to 174.0),
    "KZ" to (48.0 to 68.0), "MN" to (46.0 to 105.0),
)

fun countryName(code: String): String =
    Locale("", code).getDisplayCountry(NB).takeIf { it.isNotBlank() && it != code } ?: code

private fun project(lat: Double, lon: Double, w: Float, h: Float) =
    Offset(((lon + 180) / 360 * w).toFloat(), ((78 - lat) / 138 * h).toFloat())

@Composable
fun WorldMap(
    countries: List<CountryStat>,
    selected: Int,
    onSelect: (Int) -> Unit,
    dotColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxV = countries.maxOfOrNull { it.visits }?.coerceAtLeast(1L) ?: 1L
    fun radiusPx(v: Long) = with(density) { (10 + v.toFloat() / maxV * 16).dp.toPx() / 2 }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(64f / 26f)
            .pointerInput(countries) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val hit = countries.withIndex()
                        .mapNotNull { (i, c) -> CENTROIDS[c.code]?.let { i to (project(it.first, it.second, w, h) - tap).getDistance() - radiusPx(c.visits) } }
                        .minByOrNull { it.second }
                    if (hit != null && hit.second < with(density) { 12.dp.toPx() }) onSelect(hit.first)
                }
            }
    ) {
        val cw = size.width / 64f
        val ch = size.height / 26f
        LAND.forEachIndexed { r, ranges ->
            ranges.forEach { (a, b) ->
                for (c in a..b) drawCircle(dotColor, cw * 0.32f, Offset((c + 0.5f) * cw, (r + 0.5f) * ch))
            }
        }
        countries.forEachIndexed { i, c ->
            val (lat, lon) = CENTROIDS[c.code] ?: return@forEachIndexed
            val center = project(lat, lon, size.width, size.height)
            val r = radiusPx(c.visits)
            drawCircle(if (i == selected) accent else accent.copy(alpha = 0.3f), r, center)
            drawCircle(accent, r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}
