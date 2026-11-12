package wtf.buddyworks.storageviewer.ui.storage

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.DuplicateGroup
import wtf.buddyworks.storageviewer.data.DuplicatesState
import wtf.buddyworks.storageviewer.data.FileSelection
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.LARGE_FILE_BYTES
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.data.filesOfCategory
import wtf.buddyworks.storageviewer.data.largeOldFiles
import wtf.buddyworks.storageviewer.data.leftoverFolders
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.OverviewSectionTitle
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.theme.OneUi
import java.text.SimpleDateFormat

/** Rows shown at first in a panel, and how many more each "Show more" adds. */
private const val PanelPage = 100

/** Duplicate groups shown at first, and how many more each "Show more" adds. */
private const val GroupPage = 30

/**
 * A list shown in place of the folder overview: every file of a type, duplicates, large old
 * files or folders of uninstalled apps. Files open in their viewer; folders go to their
 * overview. Long-press offers My Files.
 */
@Composable
fun StoragePanelView(
    panel: StoragePanel,
    root: StorageNode,
    rootPath: String,
    duplicates: DuplicatesState,
    showClose: Boolean,
    onClose: () -> Unit,
    onFocusFolder: (StorageNode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val header: @Composable (title: String, lines: List<String>) -> Unit = { title, lines ->
        PanelHeader(title, lines, showClose, onClose)
    }
    // Keyed so that switching between two file types starts with a fresh list and scroll position.
    key(panel.key) {
        when (panel) {
            is StoragePanel.Type -> FileListPanel(
                title = categoryLabel(panel.category),
                root = root,
                rootPath = rootPath,
                select = { filesOfCategory(it, panel.category) },
                extraLines = { selection ->
                    listOfNotNull(
                        if (selection.appFolderBytes > 0) {
                            stringResource(R.string.in_app_folders, Format.bytes(selection.appFolderBytes))
                        } else {
                            null
                        },
                    )
                },
                caption = null,
                emptyText = stringResource(R.string.no_files_of_type),
                header = header,
                modifier = modifier,
                contentPadding = contentPadding,
            )

            StoragePanel.LargeOld -> {
                val month = rememberDateFormat("MMMyyyy")
                val rule = stringResource(R.string.large_old_rule, Format.count(LARGE_FILE_BYTES / 1_000_000) + " MB")
                FileListPanel(
                    title = stringResource(R.string.find_large_old),
                    root = root,
                    rootPath = rootPath,
                    select = { largeOldFiles(it, System.currentTimeMillis()) },
                    extraLines = { listOf(rule) },
                    caption = { file -> locationOf(file, rootPath) + " · " + month.format(file.lastModified) },
                    emptyText = stringResource(R.string.none_found),
                    header = header,
                    modifier = modifier,
                    contentPadding = contentPadding,
                )
            }

            StoragePanel.Duplicates -> DuplicatesPanel(duplicates, rootPath, header, modifier, contentPadding)

            StoragePanel.Leftovers -> LeftoversPanel(root, rootPath, onFocusFolder, header, modifier, contentPadding)
        }
    }
}

@Composable
private fun PanelHeader(title: String, lines: List<String>, showClose: Boolean, onClose: () -> Unit) {
    val colors = OneUi.colors
    Row(Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OneUi.type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            lines.forEach { Text(it, style = OneUi.type.itemSubtitle, color = colors.textSecondary) }
        }
        if (showClose) ToolbarIcon(Icons.Rounded.Close, stringResource(R.string.action_close), onClose)
    }
}

/** Files picked from the tree by [select], largest first, with a "Show more" row. */
@Composable
private fun FileListPanel(
    title: String,
    root: StorageNode,
    rootPath: String,
    select: (StorageNode) -> FileSelection,
    extraLines: @Composable (FileSelection) -> List<String>,
    caption: (@Composable (StorageNode) -> String)?,
    emptyText: String,
    header: @Composable (String, List<String>) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val icons = rememberItemIcons()
    // A rescan keeps the current list on screen until the new one is ready.
    var selection by remember { mutableStateOf<FileSelection?>(null) }
    LaunchedEffect(root) { selection = withContext(Dispatchers.Default) { select(root) } }
    var shown by remember { mutableIntStateOf(PanelPage) }
    val current = selection
    val lines = if (current == null) {
        emptyList()
    } else {
        listOf(
            stringResource(
                R.string.tree_summary,
                pluralStringResource(R.plurals.files, current.files.size, Format.count(current.files.size)),
                Format.bytes(current.totalBytes),
            ),
        ) + extraLines(current)
    }

    LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = contentPadding) {
        item(key = "header", contentType = "header") { header(title, lines) }
        if (current == null) return@LazyColumn
        if (current.files.isEmpty()) {
            item(key = "empty", contentType = "empty") { EmptyText(emptyText) }
            return@LazyColumn
        }
        val files = current.files
        val visible = files.size.coerceAtMost(shown)
        items(visible, key = { "file:" + files[it].path }, contentType = { "file" }) { index ->
            val file = files[index]
            LocatedRow(
                node = file,
                rootPath = rootPath,
                icons = icons,
                isFirst = index == 0,
                caption = caption?.invoke(file),
                onClick = { Handoff.openFile(context, file.path) },
            )
        }
        var hiddenBytes = 0L
        for (i in visible until files.size) hiddenBytes += files[i].size
        showMoreItem(files.size - visible, hiddenBytes) { shown += PanelPage }
    }
}

@Composable
private fun DuplicatesPanel(
    state: DuplicatesState,
    rootPath: String,
    header: @Composable (String, List<String>) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val icons = rememberItemIcons()
    val date = rememberDateFormat("yMMMd")
    var shownGroups by remember { mutableIntStateOf(GroupPage) }
    val groups = state.groups
    val lines = buildList {
        if (state is DuplicatesState.Checking) add(checkingText(state))
        if (!groups.isNullOrEmpty()) add(duplicatesSummary(groups))
    }

    LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = contentPadding) {
        item(key = "header", contentType = "header") { header(stringResource(R.string.find_duplicates), lines) }
        if (groups == null) return@LazyColumn
        if (groups.isEmpty()) {
            if (state is DuplicatesState.Done) item(key = "empty", contentType = "empty") { EmptyText(stringResource(R.string.none_found)) }
            return@LazyColumn
        }
        val visible = groups.take(shownGroups)
        visible.forEach { group ->
            val first = group.files[0]
            item(key = "group:" + first.path, contentType = "section") {
                OverviewSectionTitle(
                    pluralStringResource(
                        R.plurals.identical_files,
                        group.files.size,
                        Format.count(group.files.size),
                        Format.bytes(group.extraBytes),
                    ),
                )
            }
            items(group.files.size, key = { "copy:" + group.files[it].path }, contentType = { "file" }) { index ->
                val file = group.files[index]
                LocatedRow(
                    node = file,
                    rootPath = rootPath,
                    icons = icons,
                    isFirst = index == 0,
                    caption = locationOf(file, rootPath) + " · " + date.format(file.lastModified),
                    showSize = false,
                    onClick = { Handoff.openFile(context, file.path) },
                )
            }
        }
        val hidden = groups.drop(visible.size)
        showMoreItem(hidden.size, hidden.sumOf { it.extraBytes }) { shownGroups += GroupPage }
    }
}

@Composable
private fun LeftoversPanel(
    root: StorageNode,
    rootPath: String,
    onFocusFolder: (StorageNode) -> Unit,
    header: @Composable (String, List<String>) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val icons = rememberItemIcons()
    val owners = LocalFolderOwners.current
    val folders = remember(root, owners) { leftoverFolders(root, owners) }
    val total = folders.sumOf { it.size }
    LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = contentPadding) {
        item(key = "header", contentType = "header") {
            header(
                stringResource(R.string.find_leftovers),
                listOf(
                    stringResource(
                        R.string.tree_summary,
                        pluralStringResource(R.plurals.folders, folders.size, Format.count(folders.size)),
                        Format.bytes(total),
                    ),
                ),
            )
        }
        if (folders.isEmpty()) {
            item(key = "empty", contentType = "empty") { EmptyText(stringResource(R.string.none_found)) }
        }
        items(folders.size, key = { "folder:" + folders[it].path }, contentType = { "folder" }) { index ->
            val folder = folders[index]
            LocatedRow(
                node = folder,
                rootPath = rootPath,
                icons = icons,
                isFirst = index == 0,
                onClick = { onFocusFolder(folder) },
                onOverview = { onFocusFolder(folder) },
            )
        }
    }
}

/** "Show 120 more · 4.1 GB", when anything is hidden. */
private fun LazyListScope.showMoreItem(hidden: Int, hiddenBytes: Long, onClick: () -> Unit) {
    if (hidden <= 0) return
    item(key = "more", contentType = "more") {
        Text(
            pluralStringResource(R.plurals.show_more, hidden, Format.count(hidden), Format.bytes(hiddenBytes)),
            style = OneUi.type.itemSubtitle.copy(fontWeight = FontWeight.SemiBold),
            color = OneUi.colors.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Text(
            text,
            style = OneUi.type.body,
            color = OneUi.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
    }
}

/** "Checking files… 34 %", or without a share until the amount to read is known. */
@Composable
fun checkingText(state: DuplicatesState.Checking): String =
    if (state.totalBytes > 0) {
        stringResource(R.string.checking_files_percent, Format.percent(state.checkedBytes, state.totalBytes))
    } else {
        stringResource(R.string.checking_files)
    }

/** "12 files with copies · 1.4 GB extra". */
@Composable
fun duplicatesSummary(groups: List<DuplicateGroup>): String =
    pluralStringResource(R.plurals.files_with_copies, groups.size, Format.count(groups.size)) + " · " +
        stringResource(R.string.extra_bytes, Format.bytes(groups.sumOf { it.extraBytes }))

/** A date format for [skeleton] in the current locale, such as "Mar 2023" for "MMMyyyy". */
@Composable
private fun rememberDateFormat(skeleton: String): SimpleDateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, skeleton) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
}
