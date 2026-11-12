package wtf.buddyworks.storageviewer.ui.apps

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import wtf.buddyworks.storageviewer.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(300)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // State keyed by app: a reused slot must not keep showing the previous app's icon.
    val icon = remember(packageName, px) { mutableStateOf(IconCache.peek("$packageName@$px")) }
    LaunchedEffect(icon) {
        if (icon.value == null) icon.value = withContext(Dispatchers.IO) { IconCache.load(context, packageName, px) }
    }
    val current = icon.value
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).background(OneUi.colors.pill, RoundedCornerShape(size * 0.3f)))
    }
}
