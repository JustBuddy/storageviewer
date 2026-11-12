package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.DeviceStorage
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.ScanResult
import wtf.buddyworks.storageviewer.data.ScanState
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.AccessCard
import wtf.buddyworks.storageviewer.ui.CardMargin
import wtf.buddyworks.storageviewer.ui.PaneConfig
import wtf.buddyworks.storageviewer.ui.ProgressCard
import wtf.buddyworks.storageviewer.ui.appsColor
import wtf.buddyworks.storageviewer.ui.categoryColor
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.BarPart
import wtf.buddyworks.storageviewer.ui.components.CollapsingPane
import wtf.buddyworks.storageviewer.ui.components.GroupCard
import wtf.buddyworks.storageviewer.ui.components.LegendDot
import wtf.buddyworks.storageviewer.ui.components.SectionHeader
import wtf.buddyworks.storageviewer.ui.components.SegmentedBar
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.components.groupItem
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** One visible line of the tree. */
sealed interface TreeRow {
    val key: Any

    class Item(
        val node: StorageNode,
        val depth: Int,
        val expanded: Boolean,
        val canExpand: Boolean,
        val parentSize: Long,
    ) : TreeRow {
        override val key: Any get() = node.id
    }

    class More(val parent: StorageNode, val depth: Int, val hidden: Int, val hiddenBytes: Long) : TreeRow {
        override val key: Any get() = "more:${parent.id}"
    }
}

/**
 * Flattens the expanded part of the tree into rows, largest first at every level. With
 * [foldersOnly] (two panes) files are left out: they are listed in the overview instead.
 */
fun buildTreeRows(
    root: StorageNode,
    expanded: Set<String>,
    limits: Map<String, Int>,
    foldersOnly: Boolean,
): List<TreeRow> {
    val rows = ArrayList<TreeRow>()
    fun kidsOf(node: StorageNode) = if (foldersOnly) node.subfolders else node.children
    fun add(parent: StorageNode, depth: Int) {
        val kids = kidsOf(parent)
        val limit = limits[parent.path] ?: StorageViewModel.PAGE_SIZE
        val shown = min(limit, kids.size)
        for (i in 0 until shown) {
            val child = kids[i]
            val canExpand = child.isDirectory && kidsOf(child).isNotEmpty()
            val isExpanded = canExpand && child.path in expanded
            rows += TreeRow.Item(child, depth, isExpanded, canExpand, parent.size)
            if (isExpanded) add(child, depth + 1)
        }
        if (kids.size > shown) {
            var hiddenBytes = 0L
            for (i in shown until kids.size) hiddenBytes += kids[i].size
            rows += TreeRow.More(parent, depth, kids.size - shown, hiddenBytes)
        }
    }
    add(root, 0)
    return rows
}

@Composable
fun StorageListPane(
    vm: StorageViewModel,
    appsBytes: Long?,
    config: PaneConfig,
    onShowOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val icons = rememberItemIcons()
    val result = (scan as? ScanState.Done)?.result
    val foldersOnly = config.twoPane
    val rows = remember(result, vm.expanded, vm.childLimits, foldersOnly) {
        result?.let { buildTreeRows(it.root, vm.expanded, vm.childLimits, foldersOnly) } ?: emptyList()
    }
    val subtitle = device?.let {
        stringResource(R.string.used_subtitle, Format.bytes(it.usedBytes), Format.bytes(it.totalBytes))
    }
    val focusedId = remember(result, vm.focusedPath) {
        result?.root?.let { root -> vm.focusedPath?.let(root::find)?.id }
    }
    // Items before the tree: large title (optional), summary, section header.
    val treeOffset = (if (config.expandableTitle) 1 else 0) + 2

    LaunchedEffect(vm.revealRequest) {
        if (vm.revealRequest == 0) return@LaunchedEffect
        val target = vm.focusedPath ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is TreeRow.Item && it.node.path == target }
        if (index < 0) return@LaunchedEffect
        val key = rows[index].key
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == key }
        if (!visible) listState.animateScrollToItem(treeOffset + index, scrollOffset = -48)
    }

    CollapsingPane(
        title = stringResource(R.string.storage_title),
        subtitle = subtitle,
        expandable = config.expandableTitle,
        listState = listState,
        topInset = config.topInset,
        bottomPadding = config.bottomPadding,
        modifier = modifier,
        actions = {
            if (!config.twoPane && result != null) {
                ToolbarIcon(Icons.Outlined.SpaceDashboard, stringResource(R.string.action_overview), onShowOverview)
            }
            if (vm.hasAccess) {
                ToolbarIcon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh), vm::rescan)
            }
        },
    ) {
        item(key = "summary", contentType = "summary") {
            StorageSummaryCard(device, result, appsBytes, scan as? ScanState.Scanning)
        }
        if (!vm.hasAccess) {
            item(key = "access", contentType = "access") {
                AccessCard(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.access_files_title),
                    body = stringResource(R.string.access_files_body),
                    button = stringResource(R.string.access_files_button),
                    onAllow = { Handoff.openSettings(context, Access.allFilesAccessIntents(context)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            return@CollapsingPane
        }
        when (val s = scan) {
            is ScanState.Done -> treeItems(
                result = s.result,
                rows = rows,
                focusedId = focusedId,
                icons = icons,
                vm = vm,
                config = config,
                onShowOverview = onShowOverview,
            )
            is ScanState.Failed -> item(key = "failed") {
                ProgressCard(stringResource(R.string.scan_failed, s.message), null, Modifier.padding(top = 16.dp))
            }
            else -> Unit
        }
    }
}

private fun LazyListScope.treeItems(
    result: ScanResult,
    rows: List<TreeRow>,
    focusedId: Int?,
    icons: ItemIcons,
    vm: StorageViewModel,
    config: PaneConfig,
    onShowOverview: () -> Unit,
) {
    val root = result.root
    item(key = "tree-header", contentType = "section") {
        SectionHeader(
            text = stringResource(R.string.internal_storage),
            trailing = stringResource(
                R.string.tree_summary,
                Format.bytes(root.size),
                pluralStringResource(R.plurals.files, root.fileCount, Format.count(root.fileCount)),
            ),
            onClick = if (config.twoPane) ({ vm.focusRoot(root) }) else null,
        )
    }
    itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row::class }) { index, row ->
        val isFirst = index == 0
        val isLast = index == rows.lastIndex
        Box(Modifier.animateItem().padding(horizontal = CardMargin)) {
            when (row) {
                is TreeRow.Item -> TreeNodeRow(
                    row = row,
                    isFirst = isFirst,
                    isLast = isLast,
                    focused = config.twoPane && row.node.id == focusedId,
                    icons = icons,
                    rootPath = vm.rootPath,
                    onClick = { if (config.twoPane) vm.select(row.node) else vm.toggle(row.node) },
                    onChevron = { vm.toggleExpanded(row.node) },
                    onOverview = {
                        vm.focus(row.node)
                        if (!config.twoPane) onShowOverview()
                    },
                )
                is TreeRow.More -> MoreRow(row, isFirst, isLast, icons) { vm.showMore(row.parent) }
            }
        }
    }
}

private val IndentStep = 18.dp
private const val MaxIndentLevels = 7
private val ChevronBox = 32.dp

private fun rowStart(depth: Int): Dp = 10.dp + IndentStep * min(depth, MaxIndentLevels)

/** Indent guides through the row, one per ancestor level. */
private fun Modifier.indentGuides(levels: Int, color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    for (level in 0 until levels) {
        val x = (10.dp + IndentStep * level + ChevronBox / 2).toPx()
        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeNodeRow(
    row: TreeRow.Item,
    isFirst: Boolean,
    isLast: Boolean,
    focused: Boolean,
    icons: ItemIcons,
    rootPath: String,
    onClick: () -> Unit,
    onChevron: () -> Unit,
    onOverview: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val colors = OneUi.colors
    val node = row.node
    var menuOpen by remember { mutableStateOf(false) }
    val start = rowStart(row.depth)
    val textStart = start + ChevronBox + ItemIconSize + 14.dp
    val chevronRotation by animateFloatAsState(if (row.expanded) 90f else 0f, label = "chevron")

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .groupItem(isFirst, isLast, colors.card)
                .background(if (focused) colors.highlight else Color.Transparent)
                .combinedClickable(
                    onClick = { if (node.isDirectory) onClick() else Handoff.openFile(context, node.path) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .indentGuides(min(row.depth, MaxIndentLevels), colors.divider)
                .drawBehind {
                    if (!isFirst) {
                        drawLine(
                            colors.divider,
                            Offset(textStart.toPx(), 0f),
                            Offset(size.width - 20.dp.toPx(), 0f),
                            1.dp.toPx(),
                        )
                    }
                }
                .heightIn(min = 72.dp)
                .padding(start = start, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(ChevronBox)
                    .then(
                        if (row.canExpand) {
                            Modifier.clickable(
                                interactionSource = null,
                                indication = ripple(bounded = false, radius = 20.dp),
                                role = Role.Button,
                                onClick = onChevron,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (row.canExpand) {
                    Icon(
                        icons.chevron,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation),
                    )
                }
            }
            ItemContent(node, row.parentSize, icons)
        }
        if (menuOpen) {
            RowMenu(onDismiss = { menuOpen = false }, node = node, rootPath = rootPath, onOverview = onOverview)
        }
    }
}

@Composable
private fun MoreRow(row: TreeRow.More, isFirst: Boolean, isLast: Boolean, icons: ItemIcons, onClick: () -> Unit) {
    val colors = OneUi.colors
    Row(
        Modifier
            .fillMaxWidth()
            .groupItem(isFirst, isLast, colors.card)
            .clickable(onClick = onClick)
            .indentGuides(min(row.depth, MaxIndentLevels), colors.divider)
            .heightIn(min = 56.dp)
            .padding(start = rowStart(row.depth), end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ChevronBox), contentAlignment = Alignment.Center) {
            Icon(icons.more, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(ItemIconSize + 14.dp))
        Text(
            pluralStringResource(R.plurals.show_more, row.hidden, Format.count(row.hidden), Format.bytes(row.hiddenBytes)),
            style = OneUi.type.itemSubtitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accent,
        )
    }
}

@Composable
private fun RowMenu(
    onDismiss: () -> Unit,
    node: StorageNode,
    rootPath: String,
    onOverview: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = OneUi.colors.card,
    ) {
        @Composable
        fun entry(text: String, icon: ImageVector, action: () -> Unit) {
            DropdownMenuItem(
                text = { Text(text, style = OneUi.type.body, color = OneUi.colors.textPrimary) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = OneUi.colors.textPrimary) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
        if (node.isDirectory) {
            if (!node.restricted) {
                entry(stringResource(R.string.action_overview), Icons.Outlined.SpaceDashboard, onOverview)
            }
            entry(stringResource(R.string.action_open_in_files), Icons.Outlined.FolderOpen) {
                Handoff.openFolder(context, node.path, rootPath)
            }
        } else {
            entry(stringResource(R.string.action_open), Icons.AutoMirrored.Outlined.OpenInNew) {
                Handoff.openFile(context, node.path)
            }
            node.parent?.let { parent ->
                entry(stringResource(R.string.action_show_in_files), Icons.Outlined.FolderOpen) {
                    Handoff.openFolder(context, parent.path, rootPath)
                }
            }
        }
    }
}

/** Device usage split into apps, file types and system, like Device care's storage card. */
@Composable
private fun StorageSummaryCard(
    device: DeviceStorage?,
    result: ScanResult?,
    appsBytes: Long?,
    scanning: ScanState.Scanning?,
) {
    val colors = OneUi.colors
    val used = device?.usedBytes ?: 0L
    val total = device?.totalBytes ?: 0L

    data class Entry(val label: String, val bytes: Long, val color: Color)

    val entries = buildList {
        if (result != null) {
            val fileBytes = result.categoryBytes.sum()
            if (appsBytes != null) add(Entry(stringResource(R.string.cat_apps), appsBytes, appsColor()))
            FileCategory.entries.forEach { category ->
                add(Entry(categoryLabel(category), result.categoryBytes[category.ordinal], categoryColor(category)))
            }
            val rest = (used - fileBytes - (appsBytes ?: 0L)).coerceAtLeast(0L)
            val restLabel = stringResource(if (appsBytes != null) R.string.cat_system else R.string.cat_apps_system)
            add(Entry(restLabel, rest, colors.neutral))
        } else {
            add(Entry(stringResource(R.string.cat_apps_system), used, colors.neutral))
        }
    }
    val description = entries.joinToString { "${it.label} ${Format.bytes(it.bytes)}" }

    GroupCard(Modifier.padding(horizontal = CardMargin), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.used_percent, Format.percent(used, total)),
                style = OneUi.type.headline.copy(fontSize = OneUi.type.headline.fontSize * 0.92f),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.used_of_total, Format.bytes(used), Format.bytes(total)),
                style = OneUi.type.itemSubtitle,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        SegmentedBar(
            parts = entries.map { BarPart(it.label, it.bytes, it.color) },
            total = total,
            description = description,
            height = 12.dp,
        )
        Spacer(Modifier.height(16.dp))
        if (scanning != null) {
            ProgressLine(scanning)
        } else {
            val legend = entries.filter { it.bytes > 0 } + Entry(stringResource(R.string.cat_free), device?.freeBytes ?: 0L, colors.track)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Label and value side by side when a column is wide enough, else stacked.
                val inline = maxWidth / 2 >= 200.dp
                Column {
                    legend.chunked(2).forEach { pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (inline) 5.dp else 6.dp),
                        ) {
                            pair.forEach { entry ->
                                LegendCell(entry.label, entry.bytes, entry.color, inline, Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCell(label: String, bytes: Long, color: Color, inline: Boolean, modifier: Modifier) {
    val colors = OneUi.colors
    val dot: Modifier = if (color == colors.track) {
        Modifier.border(1.dp, colors.textSection.copy(alpha = 0.35f), CircleShape)
    } else {
        Modifier
    }
    Row(modifier.padding(end = 12.dp), verticalAlignment = if (inline) Alignment.CenterVertically else Alignment.Top) {
        LegendDot(color, Modifier.offset(y = if (inline) 0.dp else 5.dp).then(dot))
        Spacer(Modifier.width(10.dp))
        if (inline) {
            Text(
                label,
                style = OneUi.type.itemSubtitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(Format.bytes(bytes), style = OneUi.type.itemSubtitle, color = colors.textSecondary, maxLines = 1)
        } else {
            Column {
                Text(label, style = OneUi.type.itemSubtitle, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Format.bytes(bytes), style = OneUi.type.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressLine(scanning: ScanState.Scanning) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = OneUi.colors.accent,
            trackColor = OneUi.colors.track,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.scanning) + "  " +
                    stringResource(R.string.scanning_progress, Format.count(scanning.files), Format.bytes(scanning.bytes)),
                style = OneUi.type.itemSubtitle,
                color = OneUi.colors.textPrimary,
            )
            Text(
                scanning.currentPath,
                style = OneUi.type.caption,
                color = OneUi.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}
