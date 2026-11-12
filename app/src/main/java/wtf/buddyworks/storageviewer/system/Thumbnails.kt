package wtf.buddyworks.storageviewer.system

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.StorageNode

/** What kind of preview a file can have, which decides how it is made and drawn. */
enum class ThumbKind { Image, Video, Audio, Pdf, Apk }

/**
 * Square previews of files, made on demand and kept in memory only. Everything here only
 * reads: files are opened read-only and nothing is written, not even to MediaStore's
 * thumbnail cache (which [android.content.ContentResolver.loadThumbnail] would fill).
 */
object Thumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(cacheKiloBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    /** Files that had no preview, so scrolling past them again does not decode them again. */
    private val failed = LruCache<String, Boolean>(4096)

    /** A few decodes at a time: enough to keep up with scrolling without starving the scan. */
    private val decoder = Dispatchers.IO.limitedParallelism(3)

    fun kindOf(node: StorageNode): ThumbKind? {
        if (node.isDirectory) return null
        val ext = node.name.substringAfterLast('.', "").lowercase()
        return when (node.category) {
            FileCategory.Images -> if (ext == "svg") null else ThumbKind.Image
            FileCategory.Videos -> ThumbKind.Video
            FileCategory.Audio -> ThumbKind.Audio
            FileCategory.Documents -> if (ext == "pdf") ThumbKind.Pdf else null
            FileCategory.Archives -> if (ext == "apk") ThumbKind.Apk else null
            FileCategory.Other, null -> null
        }
    }

    /** Cache key; a rescan that finds the file changed gets a fresh preview. */
    fun key(node: StorageNode, sizePx: Int): String = "${node.path}|${node.lastModified}|${node.size}|$sizePx"

    fun peek(key: String): ImageBitmap? = cache.get(key)

    /** A [sizePx] square preview of the file at [path], or null if it has none. */
    suspend fun load(context: Context, path: String, kind: ThumbKind, sizePx: Int, key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        if (failed.get(key) != null) return null
        return withContext(decoder) {
            // Rows scrolled away while waiting for a decoder slot are skipped.
            ensureActive()
            cache.get(key)?.let { return@withContext it }
            val bitmap = runCatching { decode(context, File(path), kind, sizePx) }.getOrNull()
            if (bitmap == null) {
                failed.put(key, true)
                null
            } else {
                bitmap.asImageBitmap().also { cache.put(key, it) }
            }
        }
    }

    private fun decode(context: Context, file: File, kind: ThumbKind, px: Int): Bitmap? {
        // Ask for twice the size: the decoders fit the long side, and the square crop needs the short one.
        val request = Size(px * 2, px * 2)
        return when (kind) {
            ThumbKind.Image -> square(ThumbnailUtils.createImageThumbnail(file, request, null), px, alignTop = false)
            ThumbKind.Video -> square(ThumbnailUtils.createVideoThumbnail(file, request, null), px, alignTop = false)
            ThumbKind.Audio -> square(ThumbnailUtils.createAudioThumbnail(file, request, null), px, alignTop = false)
            ThumbKind.Pdf -> pdfFirstPage(file, px)
            ThumbKind.Apk -> apkIcon(context, file, px)
        }
    }

    /** The top of the first page, on white like the paper it stands for. */
    private fun pdfFirstPage(file: File, px: Int): Bitmap? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val width = px * 2
                    val height = (page.height * width.toFloat() / page.width).roundToInt().coerceIn(1, width * 4)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return square(bitmap, px, alignTop = true)
                }
            }
        }
    }

    /** The icon the app inside the APK would get once installed. */
    private fun apkIcon(context: Context, file: File, px: Int): Bitmap? {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.path, 0)
        } ?: return null
        val app = info.applicationInfo ?: return null
        if (app.icon == 0) return null
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        return app.loadIcon(pm).toBitmap(px, px)
    }

    /** Centre (or top) square of [src], scaled to [px]. */
    private fun square(src: Bitmap, px: Int, alignTop: Boolean): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = if (alignTop) 0 else (src.height - side) / 2
        val scale = px.toFloat() / side
        val out = Bitmap.createBitmap(src, x, y, side, side, Matrix().apply { setScale(scale, scale) }, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun cacheKiloBytes(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 12).coerceAtMost(32L * 1024).toInt()
}
