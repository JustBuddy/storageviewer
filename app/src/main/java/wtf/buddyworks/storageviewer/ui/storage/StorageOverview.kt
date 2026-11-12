package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.system.Thumbnails
import wtf.buddyworks.storageviewer.ui.categoryColor
import wtf.buddyworks.storageviewer.ui.categoryIcon
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.BarPart
import wtf.buddyworks.storageviewer.ui.components.InsetDivider
import wtf.buddyworks.storageviewer.ui.components.LegendDot
import wtf.buddyworks.storageviewer.ui.components.OverviewSectionTitle
import wtf.buddyworks.storageviewer.ui.components.SegmentedBar
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.components.Treemap
import wtf.buddyworks.storageviewer.ui.components.TreemapChild
import wtf.buddyworks.storageviewer.ui.components.TreemapItem
import wtf.buddyworks.storageviewer.ui.components.TreemapMinTileHeight
import wtf.buddyworks.storageviewer.ui.components.TreemapMinTileWidth
import wtf.buddyworks.storageviewer.ui.components.inkFor
import wtf.buddyworks.storageviewer.ui.components.planTreemap
import wtf.buddyworks.storageviewer.ui.components.treemapWorthShowing
import wtf.buddyworks.storageviewer.ui.theme.OneUi
import wtf.buddyworks.storageviewer.ui.tint

/** Candidates for treemap tiles; whatever does not fit a label joins the "other items" tile. */
private const val MaxTiles = 40

/**
 * The treemap is only shown when its labelled tiles cover at least this share of the folder.
 * Below that (many similar files, like a camera folder) the size-sorted list says more.
 */
private const val MinCoverage = 0.4

private const val MaxPreviewBlocks = 30

/** Contents rows shown at first, and how many more each "Show more" adds. */
private const val ContentsPage = 40

/**
 * Overview of one folder: a treemap of everything in it (tap a folder tile to go inside, a
 * file tile to open it), the folder's contents as a list, its mix of file types, and its
 * largest files anywhere below. Lazy, so folders with thousands of files scroll smoothly.
 */
@Composable
fun StorageOverview(
    node: StorageNode,
    rootPath: String,
    onFocus: (StorageNode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val colors = OneUi.colors
    val icons = rememberItemIcons()
    val rootName = stringResource(R.string.internal_storage)
    val title = if (node.isRoot) rootName else node.name
    val listState = rememberLazyListState()
    // Keyed by path rather than node: a rescan replaces every node but should not reset the pane.
    val path = remember(node) { node.path }
    LaunchedEffect(path) { listState.scrollToItem(0) }
    var shown by remember(path) { mutableIntStateOf(ContentsPage) }
    val contents = node.children
    // Another folder starts empty; a rescan of the same folder keeps the list until the new one is ready.
    var largest by remember(path) { mutableStateOf(emptyList<StorageNode>()) }
    LaunchedEffect(node) {
        largest = if (node.folderCount > 0) withContext(Dispatchers.Default) { node.largestFiles(8) } else emptyList()
    }
    val openItem: (StorageNode) -> Unit = { item ->
        if (item.isDirectory) onFocus(item) else Handoff.openFile(context, item.path)
    }

    BoxWithConstraints(modifier) {
        val paneHeight = maxHeight
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (!node.isRoot) {
                item(key = "crumbs", contentType = "crumbs") {
                    Breadcrumbs(node, rootName, onFocus)
                    Spacer(Modifier.height(16.dp))
                }
            }
            item(key = "header", contentType = "header") {
                Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = OneUi.type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                R.string.folder_stats,
                                Format.bytes(node.size),
                                pluralStringResource(R.plurals.files, node.fileCount, Format.count(node.fileCount)) + " · " +
                                    pluralStringResource(R.plurals.folders, node.folderCount, Format.count(node.folderCount)),
                            ),
                            style = OneUi.type.itemSubtitle,
                            color = colors.textSecondary,
                        )
                    }
                    ToolbarIcon(
                        Icons.Outlined.FolderOpen,
                        stringResource(R.string.action_open_in_files),
                        onClick = { Handoff.openFolder(context, path) },
                    )
                }
                // For the whole storage the summary card already shows the type split.
                val categories = node.categoryBytes
                if (!node.isRoot && categories != null && node.size > 0) {
                    Spacer(Modifier.height(12.dp))
                    FileTypeStrip(categories, node.size)
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "treemap", contentType = "treemap") {
                BoxWithConstraints(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    val height = minOf(maxWidth * 0.75f, paneHeight * 0.55f).coerceIn(180.dp, 400.dp)
                    val width = maxWidth
                    Crossfade(targetState = node, animationSpec = tween(260), label = "treemap") { current ->
                        FolderTreemap(
                            node = current,
                            width = width,
                            height = height,
                            onFolder = { if (!it.restricted) onFocus(it) },
                            onFile = { Handoff.openFile(context, it.path) },
                        )
                    }
                }
            }

            if (contents.isNotEmpty()) {
                item(key = "contents-title", contentType = "section") {
                    OverviewSectionTitle(
                        stringResource(R.string.contents) + " · " +
                            pluralStringResource(R.plurals.items, contents.size, Format.count(contents.size)),
                    )
                }
                val visible = contents.size.coerceAtMost(shown)
                items(visible, key = { "item:" + contents[it].name }, contentType = { "item" }) { index ->
                    val item = contents[index]
                    ContentRow(item, node.size, icons, isFirst = index == 0) { openItem(item) }
                }
                if (contents.size > shown) {
                    item(key = "contents-more", contentType = "more") {
                        val hidden = contents.size - shown
                        val hiddenBytes = contents.drop(shown).sumOf { it.size }
                        Text(
                            pluralStringResource(R.plurals.show_more, hidden, Format.count(hidden), Format.bytes(hiddenBytes)),
                            style = OneUi.type.itemSubtitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { shown += ContentsPage * 2 }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
            }

            if (largest.isNotEmpty()) {
                item(key = "largest-title", contentType = "section") {
                    OverviewSectionTitle(stringResource(R.string.largest_files))
                }
                items(largest.size, key = { "largest:" + largest[it].path }, contentType = { "file" }) { index ->
                    val file = largest[index]
                    LocatedRow(file, rootPath, icons, isFirst = index == 0, onClick = { Handoff.openFile(context, file.path) })
                }
            }
        }
    }
}

/** One entry of the contents list: same look as a tree row, without the tree. */
@Composable
private fun ContentRow(item: StorageNode, parentSize: Long, icons: ItemIcons, isFirst: Boolean, onClick: () -> Unit) {
    Column {
        if (!isFirst) InsetDivider(start = 78.dp, end = 24.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 68.dp)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemContent(item, parentSize, icons)
        }
    }
}

/**
 * Treemap of [node]: only items big enough to carry their name and size get a tile, the rest
 * share one "other items" tile. Shows nothing when the labelled tiles would explain too
 * little of the folder.
 */
@Composable
private fun FolderTreemap(
    node: StorageNode,
    width: Dp,
    height: Dp,
    onFolder: (StorageNode) -> Unit,
    onFile: (StorageNode) -> Unit,
) {
    val colors = OneUi.colors
    val density = LocalDensity.current
    val kids = remember(node) { node.children.filter { it.size > 0 } }
    if (kids.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(min(height, 120.dp))
                .clip(OneUi.cardShape)
                .background(colors.track),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (node.restricted) R.string.protected_folder else R.string.empty_folder),
                style = OneUi.type.body,
                color = colors.textSecondary,
            )
        }
        return
    }
    val candidates = kids.take(MaxTiles)
    val plan = remember(node, width, height, density) {
        with(density) {
            planTreemap(
                values = candidates.map { it.size.toDouble() },
                total = node.size.toDouble(),
                bounds = Rect(0f, 0f, width.toPx(), height.toPx()),
                minWidth = TreemapMinTileWidth.toPx(),
                minHeight = TreemapMinTileHeight.toPx(),
            )
        }
    } ?: return
    val shown = candidates.take(plan.tiles)
    val shownBytes = shown.sumOf { it.size }
    if (shownBytes.toDouble() / node.size < MinCoverage || !treemapWorthShowing(kids.size, shown.size)) return
    val restCount = kids.size - shown.size
    val restBytes = node.size - shownBytes

    val items = shown.map { child ->
        val color = categoryColor(child.dominantCategory)
        if (child.isDirectory) {
            val grandkids = child.children.filter { it.size > 0 }
            val blocks = grandkids.take(MaxPreviewBlocks)
            val smaller = child.size - blocks.sumOf { it.size }
            val preview = blocks.map { TreemapChild(it.size, tint(categoryColor(it.dominantCategory), 0.12f), it.name) } +
                listOfNotNull(
                    if (smaller > 0) TreemapChild(smaller, tint(color, 0.32f), count = grandkids.size - blocks.size) else null,
                )
            TreemapItem(
                key = child,
                value = child.size,
                color = tint(color, 0.62f),
                label = child.name,
                detail = Format.bytes(child.size) + " · " +
                    pluralStringResource(R.plurals.items, child.itemCount, Format.count(child.itemCount)),
                isFolder = true,
                children = preview,
            )
        } else {
            TreemapItem(
                key = child,
                value = child.size,
                color = color,
                label = child.name,
                detail = Format.bytes(child.size),
                icon = if (Thumbnails.kindOf(child) != null) ({ TileThumbnail(child, color) }) else null,
                iconOnlyWhenSmall = false,
            )
        }
    } + listOfNotNull(
        if (plan.hasRest && restCount > 0) {
            TreemapItem(
                key = "other",
                value = restBytes,
                color = colors.neutral,
                label = pluralStringResource(R.plurals.other_items, restCount, Format.count(restCount)),
                detail = Format.bytes(restBytes),
                clickable = false,
            )
        } else {
            null
        },
    )

    Treemap(
        items,
        Modifier
            .fillMaxWidth()
            .height(height),
    ) { item ->
        val target = item.key as? StorageNode ?: return@Treemap
        if (target.isDirectory) onFolder(target) else onFile(target)
    }
}

/** A file tile's preview, or its type icon in the tile's ink until (or unless) there is one. */
@Composable
private fun TileThumbnail(file: StorageNode, tileColor: Color) {
    FileThumbnail(file, TileIconSize) {
        Box(
            Modifier
                .size(TileIconSize)
                .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(TileIconSize * 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon(file.category), contentDescription = null, tint = inkFor(tileColor), modifier = Modifier.size(18.dp))
        }
    }
}

private val TileIconSize = 30.dp

/**
 * What a folder is made of: a slim bar plus one chip per file type. It sits under the title
 * and doubles as the colour key for the treemap below it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTypeStrip(bytes: LongArray, total: Long) {
    val colors = OneUi.colors
    // Declaration order, so neighbouring bar segments keep their validated colour contrast.
    val present = FileCategory.entries.filter { bytes[it.ordinal] > 0 }
    val description = present.map { "${categoryLabel(it)} ${Format.bytes(bytes[it.ordinal])}" }.joinToString()
    Column(Modifier.padding(horizontal = 24.dp)) {
        SegmentedBar(
            parts = FileCategory.entries.map { BarPart(it, bytes[it.ordinal], categoryColor(it)) },
            total = total,
            description = description,
            height = 8.dp,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            present.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(categoryColor(category), size = 10.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(categoryLabel(category), style = OneUi.type.itemSubtitle, color = colors.textPrimary)
                    Spacer(Modifier.width(5.dp))
                    Text(Format.percent(bytes[category.ordinal], total), style = OneUi.type.itemSubtitle, color = colors.textSecondary)
                }
            }
        }
    }
}

/** My Files-style breadcrumb pill. Every part goes to that folder. */
@Composable
private fun Breadcrumbs(node: StorageNode, rootName: String, onFocus: (StorageNode) -> Unit) {
    val colors = OneUi.colors
    val scroll = rememberScrollState()
    val lineage = node.lineage()
    LaunchedEffect(node) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.pill)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lineage.forEachIndexed { index, crumb ->
            val last = index == lineage.lastIndex
            Text(
                if (crumb.isRoot) rootName else crumb.name,
                style = OneUi.type.itemSubtitle.copy(fontWeight = if (last) FontWeight.Bold else FontWeight.Normal),
                color = if (last) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !last) { onFocus(crumb) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
            if (!last) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
