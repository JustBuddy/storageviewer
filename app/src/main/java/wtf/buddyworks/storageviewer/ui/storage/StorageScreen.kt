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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.AppEntry
import wtf.buddyworks.storageviewer.data.DeviceStorage
import wtf.buddyworks.storageviewer.data.DuplicatesState
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.FileSelection
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.ScanResult
import wtf.buddyworks.storageviewer.data.ScanState
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.data.largeOldFiles
import wtf.buddyworks.storageviewer.data.leftoverFolders
import wtf.buddyworks.storageviewer.data.searchTree
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.CardMargin
import wtf.buddyworks.storageviewer.ui.PaneConfig
import wtf.buddyworks.storageviewer.ui.NoticeCard
import wtf.buddyworks.storageviewer.ui.SearchField
import wtf.buddyworks.storageviewer.ui.appsColor
import wtf.buddyworks.storageviewer.ui.categoryColor
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.BarPart
import wtf.buddyworks.storageviewer.ui.components.CollapsingPane
import wtf.buddyworks.storageviewer.ui.components.GroupCard
import wtf.buddyworks.storageviewer.ui.components.IconBadge
import wtf.buddyworks.storageviewer.ui.components.InsetDivider
import wtf.buddyworks.storageviewer.ui.components.LegendDot
import wtf.buddyworks.storageviewer.ui.components.SectionHeader
import wtf.buddyworks.storageviewer.ui.components.SegmentedBar
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.components.ToolbarProgress
import wtf.buddyworks.storageviewer.ui.components.groupItem
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** One visible line of the tree. */
sealed interface TreeRow {
    val key: Any

    /** [path] is the row's key: unlike the node it stays the same across rescans. */
    class Item(
        val node: StorageNode,
        val path: String,
        val depth: Int,
        val expanded: Boolean,
        val canExpand: Boolean,
        val parentSize: Long,
    ) : TreeRow {
        override val key: Any get() = path
    }

    class More(
        val parent: StorageNode,
        val parentPath: String,
        val depth: Int,
        val hidden: Int,
        val hiddenBytes: Long,
    ) : TreeRow {
        override val key: Any get() = "more:$parentPath"
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
    // Paths are built once here on the way down, rather than walking up from every node.
    fun add(parent: StorageNode, parentPath: String, depth: Int) {
        val kids = kidsOf(parent)
        val limit = limits[parentPath] ?: StorageViewModel.PAGE_SIZE
        val shown = min(limit, kids.size)
        for (i in 0 until shown) {
            val child = kids[i]
            val path = "$parentPath/${child.name}"
            val canExpand = child.isDirectory && kidsOf(child).isNotEmpty()
            val isExpanded = canExpand && path in expanded
            rows += TreeRow.Item(child, path, depth, isExpanded, canExpand, parent.size)
            if (isExpanded) add(child, path, depth + 1)
        }
        if (kids.size > shown) {
            var hiddenBytes = 0L
            for (i in shown until kids.size) hiddenBytes += kids[i].size
            rows += TreeRow.More(parent, parentPath, depth, kids.size - shown, hiddenBytes)
        }
    }
    add(root, root.path, 0)
    return rows
}

@Composable
fun StorageListPane(
    vm: StorageViewModel,
    appsBytes: Long?,
    /** Unused apps, or null when that is not known (no usage access or apps not loaded yet). */
    unusedApps: List<AppEntry>?,
    config: PaneConfig,
    onShowOverview: () -> Unit,
    onShowApps: (unusedOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val duplicates by vm.duplicates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val icons = rememberItemIcons()
    // The last complete scan stays on screen while a rescan runs.
    val result = scan.result
    val root = result?.root
    val rescanning = scan is ScanState.Scanning && result != null
    val foldersOnly = config.twoPane
    val rows = remember(result, vm.expanded, vm.childLimits, foldersOnly) {
        result?.let { buildTreeRows(it.root, vm.expanded, vm.childLimits, foldersOnly) } ?: emptyList()
    }
    val subtitle = device?.let {
        stringResource(R.string.used_subtitle, Format.bytes(it.usedBytes), Format.bytes(it.totalBytes))
    }

    var largeOld by remember { mutableStateOf<FileSelection?>(null) }
    LaunchedEffect(root) {
        if (root != null) largeOld = withContext(Dispatchers.Default) { largeOldFiles(root, System.currentTimeMillis()) }
    }
    val owners = LocalFolderOwners.current
    val leftovers = remember(root, owners) { root?.let { leftoverFolders(it, owners) } ?: emptyList() }

    val query = vm.query.trim()
    val showResults = vm.searching && query.isNotEmpty() && root != null
    var results by remember { mutableStateOf<List<StorageNode>?>(null) }
    var resultsShown by remember(query) { mutableIntStateOf(ResultsPage) }
    LaunchedEffect(root, query, vm.searching) {
        if (!vm.searching || root == null || query.isEmpty()) {
            results = null
            return@LaunchedEffect
        }
        delay(150) // Typing replaces the search before it runs.
        results = withContext(Dispatchers.Default) { searchTree(root, query) }
    }

    val openPanel: (StoragePanel) -> Unit = { panel ->
        vm.openPanel(panel)
        if (!config.twoPane) onShowOverview()
    }
    val openFolder: (StorageNode) -> Unit = { folder ->
        vm.focus(folder)
        if (!config.twoPane) onShowOverview()
    }

    val failed = scan as? ScanState.Failed
    // Items before the tree: large title, search field, summary, error card, finds, section header.
    val treeOffset = (if (config.expandableTitle) 1 else 0) + (if (vm.searching) 1 else 0) + 1 +
        (if (failed != null) 1 else 0) + (if (result != null) 2 else 0) + 1

    LaunchedEffect(vm.revealRequest) {
        if (vm.revealRequest == 0 || showResults) return@LaunchedEffect
        val target = vm.focusedPath ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is TreeRow.Item && it.path == target }
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
            if (result != null) {
                ToolbarIcon(Icons.Outlined.Search, stringResource(R.string.action_search), {
                    if (vm.searching) vm.closeSearch() else vm.searching = true
                })
            }
            if (!config.twoPane && result != null) {
                ToolbarIcon(Icons.Outlined.SpaceDashboard, stringResource(R.string.action_overview), onShowOverview)
            }
            if (rescanning) {
                ToolbarProgress(stringResource(R.string.refreshing))
            } else if (vm.hasAccess) {
                ToolbarIcon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh), vm::rescan)
            }
        },
    ) {
        if (vm.searching) {
            item(key = "search", contentType = "search") {
                SearchField(vm.query, stringResource(R.string.search_files), onChange = { vm.query = it }, onClose = vm::closeSearch)
            }
        }
        if (showResults) {
            searchResults(results, resultsShown, vm.rootPath, icons, openFolder) { resultsShown += ResultsPage }
            return@CollapsingPane
        }
        item(key = "summary", contentType = "summary") {
            // Progress shows here only for the first scan; a rescan keeps the legend.
            StorageSummaryCard(
                device = device,
                result = result,
                appsBytes = appsBytes,
                scanning = (scan as? ScanState.Scanning)?.takeIf { result == null },
                onCategory = { openPanel(StoragePanel.Type(it)) },
                onApps = { onShowApps(false) },
            )
        }
        if (!vm.hasAccess) {
            item(key = "access", contentType = "access") {
                NoticeCard(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.access_files_title),
                    body = stringResource(R.string.access_files_body),
                    button = stringResource(R.string.access_files_button),
                    onClick = { Handoff.openSettings(context, Access.allFilesAccessIntents(context)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            return@CollapsingPane
        }
        if (failed != null) {
            item(key = "failed", contentType = "access") {
                NoticeCard(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.scan_failed_title),
                    body = failed.message,
                    button = stringResource(R.string.action_try_again),
                    onClick = vm::rescan,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        if (result != null) {
            item(key = "finds-header", contentType = "section") {
                SectionHeader(stringResource(R.string.worth_checking))
            }
            item(key = "finds", contentType = "finds") {
                FindsCard(
                    duplicates = duplicates,
                    largeOld = largeOld,
                    unusedApps = unusedApps,
                    leftovers = leftovers,
                    onOpen = openPanel,
                    onUnusedApps = { onShowApps(true) },
                )
            }
            treeItems(
                result = result,
                rows = rows,
                focusedPath = vm.focusedPath,
                icons = icons,
                vm = vm,
                config = config,
                onShowOverview = onShowOverview,
            )
        }
    }
}

/** Search results shown at first, and how many more each "Show more" adds. */
private const val ResultsPage = 100

private fun LazyListScope.searchResults(
    results: List<StorageNode>?,
    shown: Int,
    rootPath: String,
    icons: ItemIcons,
    onFolder: (StorageNode) -> Unit,
    onShowMore: () -> Unit,
) {
    if (results == null) return
    item(key = "results-header", contentType = "section") {
        SectionHeader(pluralStringResource(R.plurals.matches, results.size, Format.count(results.size)))
    }
    if (results.isEmpty()) {
        item(key = "no-results", contentType = "empty") {
            Text(
                stringResource(R.string.no_matches),
                style = OneUi.type.body,
                color = OneUi.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
        }
        return
    }
    val visible = results.size.coerceAtMost(shown)
    val hidden = results.size - visible
    items(visible, key = { "result:" + results[it].path }, contentType = { "result" }) { index ->
        val node = results[index]
        val context = LocalContext.current
        LocatedRow(
            node = node,
            rootPath = rootPath,
            icons = icons,
            isFirst = index == 0,
            modifier = Modifier
                .padding(horizontal = CardMargin)
                .groupItem(index == 0, index == visible - 1 && hidden == 0, OneUi.colors.card),
            horizontalPadding = 20.dp,
            onClick = { if (node.isDirectory) onFolder(node) else Handoff.openFile(context, node.path) },
            onOverview = if (node.isDirectory) ({ onFolder(node) }) else null,
        )
    }
    if (hidden > 0) {
        item(key = "results-more", contentType = "more") {
            var hiddenBytes = 0L
            for (i in visible until results.size) hiddenBytes += results[i].size
            Text(
                pluralStringResource(R.plurals.show_more, hidden, Format.count(hidden), Format.bytes(hiddenBytes)),
                style = OneUi.type.itemSubtitle.copy(fontWeight = FontWeight.SemiBold),
                color = OneUi.colors.accent,
                modifier = Modifier
                    .padding(horizontal = CardMargin)
                    .fillMaxWidth()
                    .groupItem(isFirst = false, isLast = true, OneUi.colors.card)
                    .clickable(onClick = onShowMore)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

/** One line of the "Worth checking" card. Without [onClick] it only reports (nothing to show yet). */
private class Find(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val value: String?,
    val busy: Boolean,
    val onClick: (() -> Unit)?,
)

/**
 * Where space can be won back: duplicates, large old files, unused apps and folders of
 * uninstalled apps. Each line opens its list; nothing here changes anything.
 */
@Composable
private fun FindsCard(
    duplicates: DuplicatesState,
    largeOld: FileSelection?,
    unusedApps: List<AppEntry>?,
    leftovers: List<StorageNode>,
    onOpen: (StoragePanel) -> Unit,
    onUnusedApps: () -> Unit,
) {
    val groups = duplicates.groups
    val finds = buildList {
        // Idle only for the moment between a scan finishing and its check starting.
        val busy = duplicates !is DuplicatesState.Done
        add(
            Find(
                icon = Icons.Outlined.ContentCopy,
                title = stringResource(R.string.find_duplicates),
                subtitle = when {
                    groups.isNullOrEmpty() && busy ->
                        (duplicates as? DuplicatesState.Checking)?.let { checkingText(it) } ?: stringResource(R.string.checking_files)
                    groups.isNullOrEmpty() -> stringResource(R.string.none_found)
                    else -> pluralStringResource(R.plurals.files_with_copies, groups.size, Format.count(groups.size))
                },
                value = groups?.takeIf { it.isNotEmpty() }?.let { found -> Format.bytes(found.sumOf { it.extraBytes }) },
                busy = busy,
                onClick = if (!groups.isNullOrEmpty()) ({ onOpen(StoragePanel.Duplicates) }) else null,
            ),
        )
        if (largeOld != null && largeOld.files.isNotEmpty()) {
            add(
                Find(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.find_large_old),
                    subtitle = pluralStringResource(R.plurals.files, largeOld.files.size, Format.count(largeOld.files.size)),
                    value = Format.bytes(largeOld.totalBytes),
                    busy = false,
                    onClick = { onOpen(StoragePanel.LargeOld) },
                ),
            )
        }
        if (!unusedApps.isNullOrEmpty()) {
            add(
                Find(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.find_unused_apps),
                    subtitle = pluralStringResource(R.plurals.apps_count, unusedApps.size, Format.count(unusedApps.size)),
                    value = Format.bytes(unusedApps.sumOf { it.totalBytes }),
                    busy = false,
                    onClick = onUnusedApps,
                ),
            )
        }
        if (leftovers.isNotEmpty()) {
            add(
                Find(
                    icon = Icons.Outlined.FolderOff,
                    title = stringResource(R.string.find_leftovers),
                    subtitle = pluralStringResource(R.plurals.folders, leftovers.size, Format.count(leftovers.size)),
                    value = Format.bytes(leftovers.sumOf { it.size }),
                    busy = false,
                    onClick = { onOpen(StoragePanel.Leftovers) },
                ),
            )
        }
    }
    GroupCard(Modifier.padding(horizontal = CardMargin)) {
        finds.forEachIndexed { index, find ->
            if (index > 0) InsetDivider(start = 74.dp)
            FindRow(find)
        }
    }
}

@Composable
private fun FindRow(find: Find) {
    val colors = OneUi.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (find.onClick != null) Modifier.clickable(onClick = find.onClick) else Modifier)
            .heightIn(min = 68.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(find.icon, colors.accent, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(find.title, style = OneUi.type.itemTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(find.subtitle, style = OneUi.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (find.value != null) {
            Spacer(Modifier.width(12.dp))
            Text(find.value, style = OneUi.type.itemValue, color = colors.textSecondary, maxLines = 1)
        }
        if (find.busy) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
                trackColor = colors.track,
            )
        }
    }
}

private fun LazyListScope.treeItems(
    result: ScanResult,
    rows: List<TreeRow>,
    focusedPath: String?,
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
                    focused = config.twoPane && row.path == focusedPath,
                    icons = icons,
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
    val expandState = if (row.canExpand) stringResource(if (row.expanded) R.string.state_expanded else R.string.state_collapsed) else null
    val chevronLabel = stringResource(if (row.expanded) R.string.action_collapse else R.string.action_expand)
    val openLabel = if (node.isDirectory) null else stringResource(R.string.action_open)
    val menuLabel = stringResource(R.string.action_more_options)

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .groupItem(isFirst, isLast, colors.card)
                .background(if (focused) colors.highlight else Color.Transparent)
                .combinedClickable(
                    onClickLabel = openLabel,
                    onLongClickLabel = menuLabel,
                    onClick = { if (node.isDirectory) onClick() else Handoff.openFile(context, row.path) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .semantics { if (expandState != null) stateDescription = expandState }
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
                            Modifier
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = 20.dp),
                                    role = Role.Button,
                                    onClickLabel = chevronLabel,
                                    onClick = onChevron,
                                )
                                .semantics { contentDescription = "$chevronLabel ${node.name}" }
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
            ItemContent(node, row.parentSize, icons, row.path)
        }
        if (menuOpen) {
            ItemMenu(node = node, path = row.path, onDismiss = { menuOpen = false }, onOverview = onOverview)
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

/** Device usage split into apps, file types and system, like Device care's storage card. */
@Composable
private fun StorageSummaryCard(
    device: DeviceStorage?,
    result: ScanResult?,
    appsBytes: Long?,
    scanning: ScanState.Scanning?,
    onCategory: (FileCategory) -> Unit,
    onApps: () -> Unit,
) {
    val colors = OneUi.colors
    val used = device?.usedBytes ?: 0L
    val total = device?.totalBytes ?: 0L

    /** [onClick] opens what the entry is made of; System and Free have nothing to list. */
    class Entry(val label: String, val bytes: Long, val color: Color, val onClick: (() -> Unit)? = null)

    val entries = buildList {
        if (result != null) {
            val fileBytes = result.categoryBytes.sum()
            if (appsBytes != null) add(Entry(stringResource(R.string.cat_apps), appsBytes, appsColor(), onApps))
            FileCategory.entries.forEach { category ->
                add(Entry(categoryLabel(category), result.categoryBytes[category.ordinal], categoryColor(category)) { onCategory(category) })
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
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { entry ->
                                LegendCell(entry.label, entry.bytes, entry.color, inline, entry.onClick, Modifier.weight(1f))
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
private fun LegendCell(label: String, bytes: Long, color: Color, inline: Boolean, onClick: (() -> Unit)?, modifier: Modifier) {
    val colors = OneUi.colors
    val dot: Modifier = if (color == colors.track) {
        Modifier.border(1.dp, colors.textSection.copy(alpha = 0.35f), CircleShape)
    } else {
        Modifier
    }
    Row(
        modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(end = 4.dp, top = if (inline) 5.dp else 6.dp, bottom = if (inline) 5.dp else 6.dp),
        verticalAlignment = if (inline) Alignment.CenterVertically else Alignment.Top) {
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
