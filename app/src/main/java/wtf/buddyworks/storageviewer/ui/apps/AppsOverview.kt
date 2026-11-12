package wtf.buddyworks.storageviewer.ui.apps

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.AppEntry
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.appsColor
import wtf.buddyworks.storageviewer.ui.components.BarPart
import wtf.buddyworks.storageviewer.ui.components.LegendDot
import wtf.buddyworks.storageviewer.ui.components.OverviewSectionTitle
import wtf.buddyworks.storageviewer.ui.components.SegmentedBar
import wtf.buddyworks.storageviewer.ui.components.Treemap
import wtf.buddyworks.storageviewer.ui.components.TreemapItem
import wtf.buddyworks.storageviewer.ui.components.TreemapMinIconTile
import wtf.buddyworks.storageviewer.ui.components.planTreemap
import wtf.buddyworks.storageviewer.ui.components.treemapWorthShowing
import wtf.buddyworks.storageviewer.ui.theme.OneUi

private const val MaxTiles = 40

/** Only show the treemap when its labelled tiles cover at least this share of app storage. */
private const val MinCoverage = 0.4

/**
 * Treemap of the apps in [apps] (tap a tile for Android's App info page), coloured by
 * downloaded vs. system, plus how their storage splits into app, data and cache.
 */
@Composable
fun AppsOverview(
    apps: List<AppEntry>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val colors = OneUi.colors
    val totalBytes = apps.sumOf { it.totalBytes }
    val downloadedColor = appsColor()
    val systemColor = colors.neutral
    val withStats = apps.any { it.hasStats }
    val mixed = apps.any { it.isSystem } && apps.any { !it.isSystem }
    val candidates = apps.filter { it.totalBytes > 0 }.take(MaxTiles)

    /** Tiles that fit a label in [width] × [height]; null when the treemap would say too little. */
    @Composable
    fun tiles(width: Dp, height: Dp): List<TreemapItem>? {
        val density = LocalDensity.current
        val plan = remember(apps, width, height, density) {
            with(density) {
                planTreemap(
                    values = candidates.map { it.totalBytes.toDouble() },
                    total = totalBytes.toDouble(),
                    bounds = Rect(0f, 0f, width.toPx(), height.toPx()),
                    minWidth = TreemapMinIconTile.toPx(),
                    minHeight = TreemapMinIconTile.toPx(),
                )
            }
        } ?: return null
        val shown = candidates.take(plan.tiles)
        val shownBytes = shown.sumOf { it.totalBytes }
        if (shownBytes.toDouble() / totalBytes.coerceAtLeast(1L) < MinCoverage) return null
        if (!treemapWorthShowing(apps.count { it.totalBytes > 0 }, shown.size)) return null
        val restCount = apps.size - shown.size
        return shown.map { app ->
            TreemapItem(
                key = app.packageName,
                value = app.totalBytes,
                color = if (app.isSystem) systemColor else downloadedColor,
                label = app.label,
                detail = Format.bytes(app.totalBytes),
                icon = { AppIcon(app.packageName, 30.dp) },
            )
        } + listOfNotNull(
            if (plan.hasRest && restCount > 0) {
                TreemapItem(
                    key = "other",
                    value = totalBytes - shownBytes,
                    color = colors.track,
                    label = pluralStringResource(R.plurals.other_apps, restCount, Format.count(restCount)),
                    detail = Format.bytes(totalBytes - shownBytes),
                    clickable = false,
                )
            } else {
                null
            },
        )
    }

    BoxWithConstraints(modifier) {
        val viewport = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            FitTreemapColumn(
                viewport = viewport,
                top = {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
                        Text(stringResource(R.string.app_storage), style = OneUi.type.headline, color = colors.textPrimary)
                        Text(
                            stringResource(
                                R.string.apps_subtitle,
                                pluralStringResource(R.plurals.apps_count, apps.size, Format.count(apps.size)),
                                Format.bytes(totalBytes),
                            ),
                            style = OneUi.type.itemSubtitle,
                            color = colors.textSecondary,
                        )
                    }
                },
                legend = {
                    // A one-colour treemap (one filter selected) needs no key.
                    if (mixed) {
                        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                            LegendChip(downloadedColor, stringResource(R.string.filter_downloaded))
                            Spacer(Modifier.width(20.dp))
                            LegendChip(systemColor, stringResource(R.string.filter_system))
                        }
                    }
                },
                bottom = {
                    if (withStats) {
                        val code = apps.sumOf { it.appBytes }
                        val data = apps.sumOf { it.userDataBytes }
                        val cache = apps.sumOf { it.cacheBytes }
                        val (codeColor, dataColor, cacheColor) = appPartColors()
                        Column {
                            OverviewSectionTitle(stringResource(R.string.app_storage))
                            Column(Modifier.padding(horizontal = 24.dp)) {
                                Spacer(Modifier.height(6.dp))
                                SegmentedBar(
                                    parts = listOf(
                                        BarPart("app", code, codeColor),
                                        BarPart("data", data, dataColor),
                                        BarPart("cache", cache, cacheColor),
                                    ),
                                    total = code + data + cache,
                                    description = "",
                                    height = 12.dp,
                                )
                                Spacer(Modifier.height(12.dp))
                                PartRow(codeColor, stringResource(R.string.app_part_code), code)
                                PartRow(dataColor, stringResource(R.string.app_part_data), data)
                                PartRow(cacheColor, stringResource(R.string.app_part_cache), cache)
                            }
                        }
                    }
                },
            ) { width, height ->
                val mapWidth = width - TreemapInset * 2
                val items = tiles(mapWidth, height)
                if (items != null) {
                    Treemap(
                        items,
                        Modifier
                            .padding(horizontal = TreemapInset)
                            .size(mapWidth, height),
                    ) { item -> (item.key as? String)?.let { Handoff.openAppInfo(context, it) } }
                }
            }
        }
    }
}

private val TreemapInset = 16.dp
private val TreemapMinHeight = 200.dp
private val TreemapMaxHeight = 460.dp

private enum class Slot { Top, Legend, Bottom, Treemap }

/**
 * [top], the treemap, its [legend] and [bottom] in one column. The treemap gets whatever
 * height [viewport] has left after the rest (at least [TreemapMinHeight], at most a little
 * taller than wide), so the overview fits without scrolling whenever it can. When [treemap]
 * draws nothing the legend is left out as well.
 */
@Composable
private fun FitTreemapColumn(
    viewport: Dp,
    top: @Composable () -> Unit,
    legend: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    treemap: @Composable (width: Dp, height: Dp) -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val topParts = subcompose(Slot.Top, top).map { it.measure(loose) }
        val legendParts = subcompose(Slot.Legend, legend).map { it.measure(loose) }
        val bottomParts = subcompose(Slot.Bottom, bottom).map { it.measure(loose) }
        val fixed = (topParts + legendParts + bottomParts).sumOf { it.height }
        val width = constraints.maxWidth.toDp()
        val cap = minOf((width - TreemapInset * 2) * 0.86f, TreemapMaxHeight)
        val height = (viewport.roundToPx() - fixed).toDp().coerceAtMost(cap).coerceAtLeast(TreemapMinHeight)
        val mapParts = subcompose(Slot.Treemap) { treemap(width, height) }.map { it.measure(loose) }
        val parts = topParts + mapParts + (if (mapParts.any { it.height > 0 }) legendParts else emptyList()) + bottomParts
        layout(constraints.maxWidth, parts.sumOf { it.height }) {
            var y = 0
            parts.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color, size = 12.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = OneUi.type.itemSubtitle, color = OneUi.colors.textPrimary)
    }
}

@Composable
private fun PartRow(color: Color, label: String, bytes: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color, size = 12.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = OneUi.type.itemTitle, color = OneUi.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(Format.bytes(bytes), style = OneUi.type.itemValue, color = OneUi.colors.textPrimary)
    }
}
