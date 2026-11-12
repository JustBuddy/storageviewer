package wtf.buddyworks.storageviewer.system

import android.content.ContentValues
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.FileNotFoundException

/**
 * Hands files to other apps for viewing. Only "r" (read) opens are allowed, and every
 * mutating call is refused, so a receiving app can never write through this provider,
 * whatever URI flags it asks for.
 */
class ReadOnlyFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        requireReadOnly(mode)
        return super.openFile(uri, mode)
    }

    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        requireReadOnly(mode)
        return super.openFile(uri, mode)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("StorageViewer shares files read-only")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw SecurityException("StorageViewer shares files read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw SecurityException("StorageViewer shares files read-only")

    private fun requireReadOnly(mode: String) {
        if (mode != "r") throw FileNotFoundException("Only read access is allowed (requested \"$mode\")")
    }
}
