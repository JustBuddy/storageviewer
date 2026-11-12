package wtf.buddyworks.storageviewer.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import wtf.buddyworks.storageviewer.R
import java.io.File

/**
 * Everything the app "does" is handed to another app. StorageViewer itself never changes
 * files or apps: it opens them in the app that normally handles them.
 */
object Handoff {
    private const val MY_FILES_PACKAGE = "com.sec.android.app.myfiles"
    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * [SystemClock.elapsedRealtime] of the last time a file, folder or app was handed to
     * another app, which may have deleted or uninstalled it. 0 when nothing was handed off.
     */
    @Volatile
    var lastHandoffAt: Long = 0L
        private set

    /** Folders handed to another app, with the time; the newest few are kept. */
    private val handedOff = ArrayDeque<Pair<Long, String>>()

    /**
     * Folders handed to another app after [since] ([SystemClock.elapsedRealtime]): a file's
     * folder, or the folder opened in My Files. A rescan reads these in full.
     */
    fun foldersHandedOffSince(since: Long): List<String> = synchronized(handedOff) {
        handedOff.filter { it.first > since }.map { it.second }.distinct()
    }

    /** Android's App info page, where the user can open, clear or uninstall the app. */
    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        if (start(context, listOf(intent), R.string.error_no_app_info)) markHandoff(null)
    }

    /** Opens a file in its default viewer with a read-only grant. */
    fun openFile(context: Context, path: String) {
        val file = File(path)
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        if (mime == APK_MIME) {
            // The package installer only accepts APKs from apps that may install packages, which
            // a read-only viewer never asks for. My Files can, so the APK is shown there.
            val parent = file.parent ?: return
            toast(context, R.string.apk_in_files)
            openFolder(context, parent)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
        if (uri == null) {
            toast(context, R.string.error_no_viewer)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (start(context, listOf(intent), R.string.error_no_viewer)) markHandoff(file.parent)
    }

    /** Opens a folder in Samsung My Files, falling back to Android's Files app. */
    fun openFolder(context: Context, path: String) {
        // Explicit package: an implicit intent could be claimed by any app declaring the action.
        val myFiles = Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
            .setPackage(MY_FILES_PACKAGE)
            .putExtra("samsung.myfiles.intent.extra.START_PATH", path)
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        val relative = path.removePrefix(storageRoot).trimStart('/')
        val documentUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$relative",
        )
        val files = Intent(Intent.ACTION_VIEW)
            .setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        if (start(context, listOf(myFiles, files), R.string.error_no_file_manager)) markHandoff(path)
    }

    /** Opens the first Settings page that exists for a special access. */
    fun openSettings(context: Context, intents: List<Intent>) {
        start(context, intents, R.string.error_no_settings)
    }

    private fun markHandoff(folder: String?) {
        val now = SystemClock.elapsedRealtime()
        if (folder != null) {
            synchronized(handedOff) {
                handedOff.addLast(now to folder)
                while (handedOff.size > 16) handedOff.removeFirst()
            }
        }
        lastHandoffAt = now
    }

    /** Starts the first intent that resolves; false (after a toast) when none does. */
    private fun start(context: Context, intents: List<Intent>, errorRes: Int): Boolean {
        for (intent in intents) {
            try {
                // From an activity the other app opens on top of ours, so Back returns here.
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next option.
            } catch (_: SecurityException) {
                // Not exported to us; try the next option.
            }
        }
        toast(context, errorRes)
        return false
    }

    private fun toast(context: Context, res: Int) {
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
    }
}
