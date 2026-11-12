package wtf.buddyworks.storageviewer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.ui.theme.OneUi

@Immutable
data class BarPart(val key: Any, val value: Long, val color: Color)

/**
 * One whole split into parts, with a 2dp card-coloured gap between parts and fully rounded
 * ends. The part of [total] not covered by [parts] shows as the track (free space).
 */
@Composable
fun SegmentedBar(
    parts: List<BarPart>,
    total: Long,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    animate: Boolean = true,
    /** Tiny non-zero parts are widened to this so they stay visible; 0 drops them instead. */
    minPartWidth: Dp = height * 0.6f,
    gap: Dp = 2.dp,
) {
    val colors = OneUi.colors
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (description.isNotEmpty()) Modifier.semantics { contentDescription = description } else Modifier)
            .drawWithCache {
                val radius = CornerRadius(size.height / 2f)
                val track = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
                val fills = ArrayList<Pair<Path, Color>>()
                val gaps = ArrayList<Float>()
                if (total > 0L) {
                    // Tiny non-zero parts get a minimum visible width, taken from the largest part.
                    val minWidth = minPartWidth.toPx()
                    val dropBelow = if (minWidth <= 0f) gap.toPx() * 1.5f else 0f
                    val widths = parts.map {
                        val w = if (it.value <= 0L) 0f else size.width * (it.value.toFloat() / total)
                        if (w < dropBelow) 0f else w
                    }.toMutableList()
                    var deficit = 0f
                    widths.indices.forEach { i ->
                        if (widths[i] > 0f && widths[i] < minWidth) {
                            deficit += minWidth - widths[i]
                            widths[i] = minWidth
                        }
                    }
                    val largest = widths.indices.maxByOrNull { widths[it] }
                    if (largest != null && deficit > 0f) widths[largest] = (widths[largest] - deficit).coerceAtLeast(minWidth)
                    var x = 0f
                    val p = progress.value
                    parts.forEachIndexed { i, part ->
                        if (widths[i] <= 0f) return@forEachIndexed
                        val w = widths[i] * p
                        val rect = Path().apply { addRect(Rect(x, 0f, x + w, size.height)) }
                        // Only parts touching a rounded end need clipping to the track shape.
                        val shape = if (x < radius.x || x + w > size.width - radius.x) {
                            Path().apply { op(rect, track, PathOperation.Intersect) }
                        } else {
                            rect
                        }
                        fills += shape to part.color
                        x += w
                        if (x < size.width - 0.5f) gaps += x
                    }
                }
                val gapPx = gap.toPx()
                onDrawBehind {
                    drawPath(track, colors.track)
                    fills.forEach { (path, color) -> drawPath(path, color) }
                    gaps.forEach { b ->
                        drawRect(colors.card, topLeft = Offset(b - gapPx / 2f, 0f), size = Size(gapPx, size.height))
                    }
                }
            },
    )
}
