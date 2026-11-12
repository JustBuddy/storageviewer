package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.ui.categoryColor
import wtf.buddyworks.storageviewer.ui.categoryIcon
import wtf.buddyworks.storageviewer.ui.categoryLabel
import wtf.buddyworks.storageviewer.ui.components.IconBadge
import wtf.buddyworks.storageviewer.ui.components.ShareBar
import wtf.buddyworks.storageviewer.ui.theme.OneUi

val ItemIconSize = 40.dp

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

/**
 * Icon, name, size, subtitle and share bar of a file or folder, as used by the tree and by
 * the overview's contents list.
 */
@Composable
fun RowScope.ItemContent(node: StorageNode, parentSize: Long, icons: ItemIcons) {
    val colors = OneUi.colors
    if (node.isDirectory) {
        FolderIcon(node.restricted, icons)
    } else {
        FileIcon(node, icons)
    }
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
            itemSubtitle(node, parentSize),
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
private fun itemSubtitle(node: StorageNode, parentSize: Long): String {
    if (node.restricted) return stringResource(R.string.protected_folder)
    val share = Format.percent(node.size, parentSize)
    return if (node.isDirectory) {
        if (node.itemCount == 0) {
            stringResource(R.string.empty_folder)
        } else {
            pluralStringResource(R.plurals.items, node.itemCount, Format.count(node.itemCount)) + " · " + share
        }
    } else {
        categoryLabel(node.category ?: FileCategory.Other) + " · " + share
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
        if (restricted) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(18.dp)
                    .background(OneUi.colors.card, CircleShape)
                    .padding(2.dp)
                    .background(OneUi.colors.textSecondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icons.lock, contentDescription = null, tint = OneUi.colors.card, modifier = Modifier.size(9.dp))
            }
        }
    }
}
