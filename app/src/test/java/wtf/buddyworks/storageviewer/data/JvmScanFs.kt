package wtf.buddyworks.storageviewer.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/** [ScanFs] on the JVM, so the scanner can be tested on a real temporary folder. */
object JvmScanFs : ScanFs {
    override fun list(path: String): Array<String>? = File(path).list()

    override fun stat(path: String): EntryStat? {
        val attrs = try {
            Files.readAttributes(Paths.get(path), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: IOException) {
            return null
        }
        val kind = when {
            attrs.isDirectory -> EntryKind.Directory
            attrs.isRegularFile -> EntryKind.File
            else -> EntryKind.Other
        }
        val modified = attrs.lastModifiedTime()
        return EntryStat(kind, attrs.size(), modified.toMillis(), modified.to(TimeUnit.NANOSECONDS))
    }
}
