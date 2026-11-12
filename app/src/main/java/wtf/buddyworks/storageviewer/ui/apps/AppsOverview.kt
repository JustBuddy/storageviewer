package wtf.buddyworks.storageviewer.ui.apps

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
        val paneHeight = maxHeight
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
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
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                val height = minOf(maxWidth * 0.86f, paneHeight - 140.dp).coerceIn(200.dp, 460.dp)
                val items = tiles(maxWidth, height)
                if (items != null) {
                    Column {
                        Treemap(
                            items,
                            Modifier
                                .fillMaxWidth()
                                .height(height),
                        ) { item -> (item.key as? String)?.let { Handoff.openAppInfo(context, it) } }
                        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
                            LegendChip(downloadedColor, stringResource(R.string.filter_downloaded))
                            Spacer(Modifier.width(20.dp))
                            LegendChip(systemColor, stringResource(R.string.filter_system))
                        }
                    }
                }
            }

            if (withStats) {
                val code = apps.sumOf { it.appBytes }
                val data = apps.sumOf { it.userDataBytes }
                val cache = apps.sumOf { it.cacheBytes }
                val (codeColor, dataColor, cacheColor) = appPartColors()
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cache_hint, Format.bytes(cache)),
                        style = OneUi.type.caption,
                        color = colors.textSecondary,
                    )
                }
            }

            Text(
                stringResource(R.string.read_only_note),
                style = OneUi.type.caption,
                color = colors.textSection,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            )
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
