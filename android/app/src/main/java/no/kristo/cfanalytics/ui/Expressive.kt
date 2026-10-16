package no.kristo.cfanalytics.ui

import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

// Material 3 Expressive look built on stable Material 3: the official Expressive components are
// internal in material3 1.4.0, and 1.5 needs a newer Android Gradle plugin than this project uses.

private fun RoundedPolygon.normalizedPath(size: Size): androidx.compose.ui.graphics.Path {
    val path = toPath()
    // Polygons are built around (0,0) with radius 1; scale them into the box.
    path.transform(Matrix().apply { postTranslate(1f, 1f); postScale(size.width / 2f, size.height / 2f) })
    return path.asComposePath()
}

/** A [RoundedPolygon] used as a Compose [Shape] (for avatars). */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(polygon.normalizedPath(size))
}

private val soft = CornerRounding(0.3f, smoothing = 0.6f)

/** Expressive shape set (cookie, clover, sunny, pill-ish, gem, flower) for domain avatars. */
val AVATAR_SHAPES: List<Shape> = listOf(
    RoundedPolygon.star(9, innerRadius = 0.8f, rounding = soft),
    RoundedPolygon.star(4, innerRadius = 0.5f, rounding = CornerRounding(0.4f)),
    RoundedPolygon.star(8, innerRadius = 0.85f, rounding = CornerRounding(0.1f)),
    RoundedPolygon(4, rounding = CornerRounding(0.6f)),
    RoundedPolygon(6, rounding = CornerRounding(0.25f)),
    RoundedPolygon.star(6, innerRadius = 0.65f, rounding = soft),
).map(::PolygonShape)

/** Loading indicator that morphs between a soft star and a circle while turning. */
@Composable
fun MorphingLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val morph = remember {
        Morph(RoundedPolygon.star(7, innerRadius = 0.7f, rounding = soft), RoundedPolygon.circle(numVertices = 7))
    }
    val t = rememberInfiniteTransition(label = "loading")
    val progress by t.animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "morph")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin")
    Canvas(modifier.rotate(angle)) {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { postTranslate(1f, 1f); postScale(size.width / 2f, size.height / 2f) })
        drawPath(path.asComposePath(), color)
    }
}

/**
 * Connected button group: items sit 2 dp apart with small inner corners; the selected one springs
 * into a full pill and shows a check. Labels never wrap.
 */
@Composable
fun <T> ConnectedGroup(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Row(modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            val checked = item == selected
            val bouncy = spring<androidx.compose.ui.unit.Dp>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
            val outer = 20.dp
            val inner by animateDpAsState(if (checked) 20.dp else 6.dp, bouncy, label = "inner")
            val start = if (i == 0) outer else inner
            val end = if (i == items.lastIndex) outer else inner
            Surface(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = if (fill) Modifier.weight(1f).height(40.dp) else Modifier.height(40.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (checked) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label(item), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
