package wtf.buddyworks.storageviewer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** Chart slot 0 is Apps; file categories follow in declaration order. */
@Composable
@ReadOnlyComposable
fun categoryColor(category: FileCategory?): Color =
    if (category == null) OneUi.colors.neutral else OneUi.colors.series[category.ordinal + 1]

@Composable
@ReadOnlyComposable
fun appsColor(): Color = OneUi.colors.series[0]

/** A lighter step of [color] toward the card, for inner detail such as a sunburst's outer ring. */
@Composable
@ReadOnlyComposable
fun tint(color: Color, amount: Float): Color = lerp(color, OneUi.colors.card, amount)

fun categoryIcon(category: FileCategory?): ImageVector = when (category) {
    FileCategory.Images -> Icons.Outlined.Image
    FileCategory.Videos -> Icons.Outlined.Movie
    FileCategory.Documents -> Icons.Outlined.Description
    FileCategory.Audio -> Icons.Outlined.MusicNote
    FileCategory.Archives -> Icons.Outlined.FolderZip
    FileCategory.Other, null -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

@Composable
fun categoryLabel(category: FileCategory): String = stringResource(
    when (category) {
        FileCategory.Images -> R.string.cat_images
        FileCategory.Videos -> R.string.cat_videos
        FileCategory.Documents -> R.string.cat_documents
        FileCategory.Audio -> R.string.cat_audio
        FileCategory.Archives -> R.string.cat_archives
        FileCategory.Other -> R.string.cat_other
    },
)
