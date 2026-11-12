package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.system.ThumbKind
import wtf.buddyworks.storageviewer.system.Thumbnails
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** Every preview is decoded at list-icon size; smaller uses scale the same bitmap down. */
private val DecodeSize = ItemIconSize

/**
 * A preview of [node] (photo, video frame, album art, first PDF page or APK icon) in a
 * [size] square. Shows [fallback] while it loads and for files that have no preview.
 */
@Composable
fun FileThumbnail(node: StorageNode, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val kind = remember(node) { Thumbnails.kindOf(node) }
    if (kind == null) {
        fallback()
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { DecodeSize.roundToPx() }
    val key = remember(node, px) { Thumbnails.key(node, px) }
    // State keyed by file: a reused slot must not keep showing the previous file's preview.
    val thumb = remember(key) { mutableStateOf(Thumbnails.peek(key)) }
    LaunchedEffect(thumb) {
        if (thumb.value == null) thumb.value = Thumbnails.load(context.applicationContext, node.path, kind, px, key)
    }
    val bitmap = thumb.value
    if (bitmap == null) {
        fallback()
        return
    }
    if (kind == ThumbKind.Apk) {
        // Already shaped like an app icon.
        Image(bitmap, contentDescription = null, modifier = modifier.size(size))
        return
    }
    val shape = RoundedCornerShape(size * 0.25f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            // Pages are white: give them an edge on light cards.
            .then(if (kind == ThumbKind.Pdf) Modifier.border(1.dp, OneUi.colors.divider, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        if (kind == ThumbKind.Video) {
            Box(
                Modifier
                    .size(size * 0.46f)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.34f))
            }
        }
    }
}
