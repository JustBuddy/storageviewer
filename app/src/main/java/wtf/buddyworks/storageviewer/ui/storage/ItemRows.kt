package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.FolderOwner
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.apps.AppIcon
import wtf.buddyworks.storageviewer.ui.categoryColor
import wtf.buddyworks.storageviewer.ui.categoryIcon
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.IconBadge
import wtf.buddyworks.storageviewer.ui.components.ShareBar
import wtf.buddyworks.storageviewer.ui.theme.OneUi

val ItemIconSize = 40.dp

/** Which app each folder belongs to, by path ([wtf.buddyworks.storageviewer.data.findFolderOwners]). */
val LocalFolderOwners = compositionLocalOf<Map<String, FolderOwner>> { emptyMap() }

/**
 * Icons shared by every row of a list. A vector icon is rasterised once per painter, so
 * sharing painters keeps scrolling from re-drawing the same icon for each new row.
 */
@Stable
class ItemIcons(
    val folder: Painter,
    val lock: Painter,
    val chevron: Painter,
    val more: Painter,
    private val categories: List<Painter>,
) {
    fun category(category: FileCategory?): Painter = categories[(category ?: FileCategory.Other).ordinal]
}

@Composable
fun rememberItemIcons(): ItemIcons {
    val folder = rememberVectorPainter(Icons.Rounded.Folder)
    val lock = rememberVectorPainter(Icons.Rounded.Lock)
    val chevron = rememberVectorPainter(Icons.Rounded.ChevronRight)
    val more = rememberVectorPainter(Icons.Rounded.ExpandMore)
    val categories = FileCategory.entries.map { rememberVectorPainter(categoryIcon(it)) }
    return remember(folder, lock, chevron, more) { ItemIcons(folder, lock, chevron, more, categories) }
}

/** The app [node] belongs to, if it is a folder with a known owner. [path] saves working it out. */
@Composable
fun folderOwner(node: StorageNode, path: String? = null): FolderOwner? {
    if (!node.isDirectory) return null
    val owners = LocalFolderOwners.current
    if (owners.isEmpty()) return null
    return owners[path ?: node.path]
}

/**
 * Icon, name, size, subtitle and share bar of a file or folder, as used by the tree and by
 * the overview's contents list.
 */
@Composable
fun RowScope.ItemContent(node: StorageNode, parentSize: Long, icons: ItemIcons, path: String? = null) {
    val colors = OneUi.colors
    val owner = folderOwner(node, path)
    NodeIcon(node, owner, icons)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.name,
                style = OneUi.type.itemTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            if (!node.restricted) {
                Text(Format.bytes(node.size), style = OneUi.type.itemValue, color = colors.textSecondary, maxLines = 1)
            }
        }
        Text(
            itemSubtitle(node, parentSize, owner),
            style = OneUi.type.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!node.restricted) {
            Spacer(Modifier.height(7.dp))
            ShareBar(
                fraction = if (parentSize > 0) node.size.toFloat() / parentSize else 0f,
                color = categoryColor(node.dominantCategory),
            )
        }
    }
}

@Composable
private fun itemSubtitle(node: StorageNode, parentSize: Long, owner: FolderOwner?): String {
    if (node.restricted) return stringResource(R.string.protected_folder)
    val share = Format.percent(node.size, parentSize)
    if (!node.isDirectory) return categoryLabel(node.category ?: FileCategory.Other) + " · " + share
    val contents = if (node.itemCount == 0) {
        stringResource(R.string.empty_folder)
    } else {
        pluralStringResource(R.plurals.items, node.itemCount, Format.count(node.itemCount)) + " · " + share
    }
    return ownerLabel(owner)?.let { "$it · $contents" } ?: contents
}

/** The app's name, or "Uninstalled app". */
@Composable
fun ownerLabel(owner: FolderOwner?): String? = when (owner) {
    is FolderOwner.App -> owner.label
    is FolderOwner.Uninstalled -> stringResource(R.string.uninstalled_app)
    null -> null
}

/** A file's thumbnail or type icon; a folder's icon, or the icon of the app it belongs to. */
@Composable
fun NodeIcon(node: StorageNode, owner: FolderOwner?, icons: ItemIcons) {
    when {
        !node.isDirectory -> FileIcon(node, icons)
        owner is FolderOwner.App -> AppFolderIcon(owner.packageName, icons)
        else -> FolderIcon(node.restricted, icons)
    }
}

/** A file's thumbnail where it has one, else its type icon. */
@Composable
fun FileIcon(node: StorageNode, icons: ItemIcons, size: Dp = ItemIconSize) {
    FileThumbnail(node, size) {
        IconBadge(icons.category(node.category), categoryColor(node.category), size = size)
    }
}

@Composable
private fun FolderIcon(restricted: Boolean, icons: ItemIcons) {
    Box(Modifier.size(ItemIconSize), contentAlignment = Alignment.Center) {
        Icon(
            icons.folder,
            contentDescription = null,
            tint = if (restricted) OneUi.colors.neutral else OneUi.colors.folder,
            modifier = Modifier.size(36.dp),
        )
        if (restricted) CornerBadge(icons.lock, OneUi.colors.textSecondary)
    }
}

/** The owning app's icon with a small folder badge, so it does not read as an app row. */
@Composable
private fun AppFolderIcon(packageName: String, icons: ItemIcons) {
    Box(Modifier.size(ItemIconSize), contentAlignment = Alignment.Center) {
        AppIcon(packageName, 36.dp)
        CornerBadge(icons.folder, OneUi.colors.folder)
    }
}

@Composable
private fun BoxScope.CornerBadge(icon: Painter, color: Color) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(18.dp)
            .background(OneUi.colors.card, CircleShape)
            .padding(2.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = OneUi.colors.card, modifier = Modifier.size(9.dp))
    }
}

/** Where [node] is, relative to the storage root: "DCIM › Camera", or "Internal storage". */
@Composable
fun locationOf(node: StorageNode, rootPath: String): String {
    val rootName = stringResource(R.string.internal_storage)
    return node.parent?.path?.removePrefix(rootPath)?.trim('/')?.replace("/", " › ")?.ifEmpty { rootName } ?: rootName
}

/**
 * A file or folder from anywhere in the tree, with its location: search results, largest
 * files and the find lists. Long-press offers the same actions as in the tree.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocatedRow(
    node: StorageNode,
    rootPath: String,
    icons: ItemIcons,
    isFirst: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    showSize: Boolean = true,
    horizontalPadding: Dp = 24.dp,
    onOverview: (() -> Unit)? = null,
) {
    val colors = OneUi.colors
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val path = remember(node) { node.path }
    val owner = folderOwner(node, path)
    val location = caption ?: locationOf(node, rootPath)
    val dividerStart = horizontalPadding + ItemIconSize + 14.dp
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = stringResource(R.string.action_open),
                    onLongClickLabel = stringResource(R.string.action_more_options),
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .drawBehind {
                    if (!isFirst) {
                        drawLine(
                            colors.divider,
                            Offset(dividerStart.toPx(), 0f),
                            Offset(size.width - horizontalPadding.toPx(), 0f),
                            1.dp.toPx(),
                        )
                    }
                }
                .heightIn(min = 64.dp)
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeIcon(node, owner, icons)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, style = OneUi.type.itemTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    ownerLabel(owner)?.let { "$it · $location" } ?: location,
                    style = OneUi.type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showSize) {
                Spacer(Modifier.width(12.dp))
                Text(Format.bytes(node.size), style = OneUi.type.itemValue, color = colors.textSecondary, maxLines = 1)
            }
        }
        if (menuOpen) {
            ItemMenu(node = node, path = path, onDismiss = { menuOpen = false }, onOverview = onOverview)
        }
    }
}

/**
 * Long-press menu of a file or folder. Every entry hands off: a folder opens in My Files (or
 * in the overview, when [onOverview] is given), a file in its viewer or its folder.
 */
@Composable
fun ItemMenu(node: StorageNode, path: String, onDismiss: () -> Unit, onOverview: (() -> Unit)?) {
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
            if (!node.restricted && onOverview != null) {
                entry(stringResource(R.string.action_overview), Icons.Outlined.SpaceDashboard, onOverview)
            }
            entry(stringResource(R.string.action_open_in_files), Icons.Outlined.FolderOpen) {
                Handoff.openFolder(context, path)
            }
        } else {
            entry(stringResource(R.string.action_open), Icons.AutoMirrored.Outlined.OpenInNew) {
                Handoff.openFile(context, path)
            }
            entry(stringResource(R.string.action_show_in_files), Icons.Outlined.FolderOpen) {
                Handoff.openFolder(context, path.substringBeforeLast('/'))
            }
        }
    }
}
