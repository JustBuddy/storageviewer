package wtf.buddyworks.storageviewer.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.Settings

/** Checks for the two special accesses the app needs, and the Settings pages that grant them. */
object Access {
    /** "All files access". Android has no read-only variant; the app only ever reads. */
    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** "Usage access", needed to read other apps' data and cache sizes. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun allFilesAccessIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri(context)),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )

    fun usageAccessIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
