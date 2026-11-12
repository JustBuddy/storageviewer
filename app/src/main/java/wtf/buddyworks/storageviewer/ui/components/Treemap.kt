package wtf.buddyworks.storageviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A block inside a folder tile's preview. [count] is how many items it stands for. */
@Immutable
data class TreemapChild(val value: Long, val color: Color, val label: String? = null, val count: Int = 1)

@Immutable
data class TreemapItem(
    val key: Any,
    val value: Long,
    val color: Color,
    val label: String,
    val detail: String,
    val isFolder: Boolean = false,
    /** Contents drawn as a preview inside the tile (folders only), largest first. */
    val children: List<TreemapChild> = emptyList(),
    val icon: (@Composable () -> Unit)? = null,
    /**
     * Small tiles show only the [icon] when true (apps, recognisable by icon). False keeps the
     * name instead and shows the icon only on tiles tall enough for both (files).
     */
    val iconOnlyWhenSmall: Boolean = true,
    val clickable: Boolean = true,
)

/** Smallest tile that still fits a name and a size line (including the 3dp gap). */
val TreemapMinTileWidth = 70.dp
val TreemapMinTileHeight = 54.dp

/** Smallest tile for items with an icon: the icon alone identifies them. */
val TreemapMinIconTile = 46.dp

/**
 * Whether a treemap says more than the list it sits above. Not for a handful of items (the list
 * shows each one with its share bar) and not when only one item gets a tile of its own (one
 * block beside "N other items" says nothing the list does not).
 */
fun treemapWorthShowing(itemCount: Int, ownTiles: Int): Boolean = itemCount > TreemapMinItems && ownTiles >= 2

private const val TreemapMinItems = 6

/** Smallest preview block that still fits a one-line name. */
private val PreviewMinWidth = 62.dp
private val PreviewMinHeight = 26.dp

/**
 * How a set of values fits into a treemap so that every tile can carry a label: the largest
 * [tiles] values get their own tile, everything else shares one "rest" tile ([hasRest]).
 */
data class TreemapPlan(val tiles: Int, val hasRest: Boolean)

/**
 * Finds the most tiles (from the front of [values], sorted descending, at most all of them)
 * that can all be at least [minWidth] x [minHeight] in [bounds] when the remainder of [total]
 * becomes one extra tile. The rest tile has to fit too unless [restMustFit] is false.
 * Returns null when not even one tile fits.
 */
fun planTreemap(
    values: List<Double>,
    total: Double,
    bounds: Rect,
    minWidth: Float,
    minHeight: Float,
    restMustFit: Boolean = true,
): TreemapPlan? {
    if (total <= 0.0 || bounds.width < minWidth || bounds.height < minHeight) return null
    var head = values.sum()
    for (n in values.size downTo 1) {
        if (n < values.size) head -= values[n]
        val rest = total - head
        val hasRest = rest > total * 0.0005
        val layout = if (hasRest) values.subList(0, n) + rest else values.subList(0, n)
        val rects = layoutBySize(layout, bounds)
        val fits = rects.indices.all { i ->
            (hasRest && i == n && !restMustFit) || (rects[i].width >= minWidth && rects[i].height >= minHeight)
        }
        if (fits) return TreemapPlan(n, hasRest)
    }
    return null
}

/**
 * [squarify] with the values taken largest first, whatever order they come in, so a big
 * "rest" tile at the end does not squeeze the others into slivers. Rectangles are returned
 * in the order of [values].
 */
fun layoutBySize(values: List<Double>, bounds: Rect): List<Rect> {
    val order = values.indices.sortedByDescending { values[it] }
    val sorted = squarify(order.map { values[it] }, bounds)
    val result = arrayOfNulls<Rect>(values.size)
    order.forEachIndexed { position, index -> result[index] = sorted[position] }
    return result.map { it ?: Rect.Zero }
}

/**
 * Squarified treemap: every item is a rounded tile whose area is proportional to its value,
 * separated by 3dp gaps. Callers pass items that fit labelled (see [planTreemap]); folder
 * tiles preview their contents, again only with blocks big enough to be named.
 */
@Composable
fun Treemap(
    items: List<TreemapItem>,
    modifier: Modifier = Modifier,
    onClick: (TreemapItem) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val rects = remember(items, widthPx, heightPx) {
            layoutBySize(items.map { it.value.toDouble() }, Rect(0f, 0f, widthPx, heightPx))
        }
        val gap = with(density) { 3.dp.toPx() }
        items.forEachIndexed { index, item ->
            val r = rects.getOrNull(index) ?: return@forEachIndexed
            val x = r.left + gap / 2f
            val y = r.top + gap / 2f
            val w = r.width - gap
            val h = r.height - gap
            if (w < 1f || h < 1f) return@forEachIndexed
            val wDp = with(density) { w.toDp() }
            val hDp = with(density) { h.toDp() }
            val radius = min(12f, min(wDp.value, hDp.value) / 4f).dp
            // Keyed so a tile's state (such as a loaded icon) never carries over to another item
            // when the list changes, for example after a filter or search.
            key(item.key) {
                Tile(
                    item = item,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(wDp, hDp)
                        .clip(RoundedCornerShape(radius))
                        .then(if (item.clickable) Modifier.clickable { onClick(item) } else Modifier)
                        .semantics { contentDescription = "${item.label}, ${item.detail}" },
                    widthDp = wDp.value,
                    heightDp = hDp.value,
                )
            }
        }
    }
}

private class PreviewBlock(val rect: Rect, val color: Color, val label: TextLayoutResult?)

@Composable
private fun Tile(item: TreemapItem, modifier: Modifier, widthDp: Float, heightDp: Float) {
    val ink = inkFor(item.color)
    val measurer = rememberTextMeasurer()
    // Small tiles of items with an icon (apps) show just the icon, plus the size if it fits.
    if (item.icon != null && item.iconOnlyWhenSmall && (widthDp < 64f || heightDp < 84f)) {
        Box(modifier.background(item.color), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                item.icon.invoke()
                if (heightDp >= 66f) {
                    Spacer(Modifier.size(4.dp))
                    Text(item.detail, style = TileDetail, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }
    val showLabel = widthDp >= 52f && heightDp >= 30f
    val showDetail = showLabel && heightDp >= 48f && widthDp >= 60f
    val showIcon = item.icon != null && widthDp >= 52f && heightDp >= 84f
    val headerDp = when {
        showIcon -> 0f
        showDetail -> 50f
        showLabel -> 30f
        else -> 0f
    }

    Box(
        modifier
            .background(item.color)
            .then(
                if (item.children.isEmpty()) {
                    Modifier
                } else {
                    Modifier.drawWithCache {
                        val inset = 5.dp.toPx()
                        val area = Rect(inset, headerDp.dp.toPx(), size.width - inset, size.height - inset)
                        val blocks = previewBlocks(item, area, measurer, PreviewMinWidth.toPx(), PreviewMinHeight.toPx(), this)
                        val corner = 5.dp.toPx()
                        val pad = 6.dp.toPx()
                        onDrawBehind {
                            blocks.forEach { b ->
                                val c = min(corner, min(b.rect.width, b.rect.height) / 3f)
                                drawRoundRect(b.color, b.rect.topLeft, b.rect.size, CornerRadius(c, c))
                                b.label?.let { drawText(it, topLeft = b.rect.topLeft + Offset(pad, pad * 0.7f)) }
                            }
                        }
                    }
                },
            ),
    ) {
        if (showLabel) {
            Column(Modifier.padding(horizontal = 9.dp, vertical = if (showDetail) 7.dp else 5.dp)) {
                if (showIcon) {
                    item.icon.invoke()
                    Spacer(Modifier.size(6.dp))
                }
                Row {
                    if (item.isFolder && widthDp >= 88f) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = ink.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(15.dp)
                                .offset(y = 1.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        item.label,
                        style = TileLabel,
                        color = ink,
                        maxLines = if (item.children.isEmpty() && heightDp >= 110f) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showDetail) {
                    Text(item.detail, style = TileDetail, color = ink.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Blocks for a folder tile's preview. Only blocks that fit a name are drawn on their own; the
 * rest share one block labelled "+N". Nothing is drawn when no block fits a name.
 */
private fun previewBlocks(
    item: TreemapItem,
    area: Rect,
    measurer: TextMeasurer,
    minWidth: Float,
    minHeight: Float,
    density: Density,
): List<PreviewBlock> {
    if (area.width <= minWidth || area.height <= minHeight) return emptyList()
    val values = item.children.map { it.value.toDouble() }
    val plan = planTreemap(values, values.sum(), area, minWidth, minHeight, restMustFit = false) ?: return emptyList()
    val gap = with(density) { 2.dp.toPx() }
    val pad = with(density) { 6.dp.toPx() }
    val shown = item.children.subList(0, plan.tiles)
    val restCount = item.children.drop(plan.tiles).sumOf { it.count }
    val layout = values.subList(0, plan.tiles) + if (plan.hasRest) listOf(values.drop(plan.tiles).sum()) else emptyList()
    val rects = layoutBySize(layout, area)
    val restColor = lerp(item.color, inkFor(item.color), 0.10f)
    return rects.mapIndexed { i, r ->
        val inner = Rect(r.left + gap / 2f, r.top + gap / 2f, r.right - gap / 2f, r.bottom - gap / 2f)
        val child = shown.getOrNull(i)
        val color = child?.color ?: restColor
        val text = when {
            child != null -> child.label
            restCount > 0 && inner.width >= minWidth && inner.height >= minHeight -> "+$restCount"
            else -> null
        }
        val label = text?.let {
            measurer.measure(
                it,
                style = PreviewLabel.copy(color = inkFor(color)),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (inner.width - pad * 2).toInt().coerceAtLeast(0)),
            )
        }
        PreviewBlock(inner, color, label)
    }
}

private val TileLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
private val TileDetail = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
private val PreviewLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)

/** White or near-black text on a coloured fill, whichever reads better. */
fun inkFor(fill: Color): Color = if (fill.luminance() > 0.45f) Color(0xFF141414) else Color.White

/**
 * Squarified treemap layout (Bruls, Huizing & van Wijk). [values] should be sorted descending;
 * returns one rectangle per value, in the same order.
 */
fun squarify(values: List<Double>, bounds: Rect): List<Rect> {
    val result = ArrayList<Rect>(values.size)
    val total = values.sum()
    if (total <= 0.0 || bounds.width <= 0f || bounds.height <= 0f) {
        repeat(values.size) { result += Rect.Zero }
        return result
    }
    val scale = bounds.width.toDouble() * bounds.height / total
    val areas = values.map { it * scale }
    var left = bounds.left.toDouble()
    var top = bounds.top.toDouble()
    var width = bounds.width.toDouble()
    var height = bounds.height.toDouble()
    var i = 0
    while (i < areas.size) {
        val side = min(width, height)
        var rowSum = areas[i]
        var rowMin = areas[i]
        var rowMax = areas[i]
        var end = i + 1
        while (end < areas.size) {
            val next = areas[end]
            val newSum = rowSum + next
            val current = worst(rowSum, rowMin, rowMax, side)
            val candidate = worst(newSum, min(rowMin, next), max(rowMax, next), side)
            if (candidate > current) break
            rowSum = newSum
            rowMin = min(rowMin, next)
            rowMax = max(rowMax, next)
            end++
        }
        if (width >= height) {
            val columnWidth = if (height > 0) rowSum / height else 0.0
            var y = top
            for (k in i until end) {
                val h = if (columnWidth > 0) areas[k] / columnWidth else 0.0
                result += Rect(left.toFloat(), y.toFloat(), (left + columnWidth).toFloat(), (y + h).toFloat())
                y += h
            }
            left += columnWidth
            width -= columnWidth
        } else {
            val rowHeight = if (width > 0) rowSum / width else 0.0
            var x = left
            for (k in i until end) {
                val w = if (rowHeight > 0) areas[k] / rowHeight else 0.0
                result += Rect(x.toFloat(), top.toFloat(), (x + w).toFloat(), (top + rowHeight).toFloat())
                x += w
            }
            top += rowHeight
            height -= rowHeight
        }
        i = end
    }
    return result
}

private fun worst(sum: Double, minArea: Double, maxArea: Double, side: Double): Double {
    if (sum <= 0.0 || minArea <= 0.0) return Double.MAX_VALUE
    val s2 = sum * sum
    val w2 = side * side
    return max(w2 * maxArea / s2, s2 / (w2 * minArea))
}
