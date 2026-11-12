package wtf.buddyworks.storageviewer.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    /** Android's App info page, where the user can open, clear or uninstall the app. */
    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        start(context, listOf(intent), R.string.error_no_app_info)
    }

    /** Opens a file in its default viewer with a read-only grant. */
    fun openFile(context: Context, path: String) {
        val file = File(path)
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
        if (uri == null) {
            toast(context, R.string.error_no_viewer)
            return
        }
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, listOf(intent), R.string.error_no_viewer)
    }

    /** Opens a folder in Samsung My Files, falling back to Android's Files app. */
    fun openFolder(context: Context, path: String, storageRoot: String) {
        val myFiles = Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
            .putExtra("samsung.myfiles.intent.extra.START_PATH", path)
        val relative = path.removePrefix(storageRoot).trimStart('/')
        val documentUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$relative",
        )
        val files = Intent(Intent.ACTION_VIEW)
            .setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        start(context, listOf(myFiles, files), R.string.error_no_file_manager)
    }

    /** Opens the first Settings page that exists for a special access. */
    fun openSettings(context: Context, intents: List<Intent>) {
        start(context, intents, R.string.error_no_settings)
    }

    private fun start(context: Context, intents: List<Intent>, errorRes: Int) {
        for (intent in intents) {
            try {
                // From an activity the other app opens on top of ours, so Back returns here.
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next option.
            } catch (_: SecurityException) {
                // Not exported to us; try the next option.
            }
        }
        toast(context, errorRes)
    }

    private fun toast(context: Context, res: Int) {
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
    }
}
