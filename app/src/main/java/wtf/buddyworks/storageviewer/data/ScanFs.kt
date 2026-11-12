package wtf.buddyworks.storageviewer.data

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

enum class EntryKind { Directory, File, Other }

/** What a scan learns about one entry, without following symlinks. */
class EntryStat(
    val kind: EntryKind,
    val size: Long,
    /** Modification time in milliseconds, for display. */
    val modifiedMs: Long,
    /**
     * Modification time in nanoseconds. A folder's changes whenever an entry is added, removed
     * or renamed in it, which is how a rescan tells which folders it can take over unchanged.
     */
    val stamp: Long,
)

/** The only two file-system calls a scan makes, both read-only. */
interface ScanFs {
    /** Names in the folder at [path], or null when it cannot be listed. */
    fun list(path: String): Array<String>?

    /** The entry at [path] (lstat), or null when it is gone or cannot be read. */
    fun stat(path: String): EntryStat?
}

/** [ScanFs] on Android: readdir and lstat. */
object OsScanFs : ScanFs {
    override fun list(path: String): Array<String>? = File(path).list()

    override fun stat(path: String): EntryStat? {
        val stat = try {
            Os.lstat(path)
        } catch (_: ErrnoException) {
            return null
        }
        val kind = when {
            OsConstants.S_ISDIR(stat.st_mode) -> EntryKind.Directory
            OsConstants.S_ISREG(stat.st_mode) -> EntryKind.File
            else -> EntryKind.Other
        }
        val mtime = stat.st_mtim
        return EntryStat(kind, stat.st_size, stat.st_mtime * 1000L, mtime.tv_sec * 1_000_000_000L + mtime.tv_nsec)
    }
}
