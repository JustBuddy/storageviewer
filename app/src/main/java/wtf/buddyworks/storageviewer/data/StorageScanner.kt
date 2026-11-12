package wtf.buddyworks.storageviewer.data

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.coroutines.coroutineContext

/**
 * Walks shared storage and builds a [StorageNode] tree.
 *
 * Read-only by construction: the only file-system calls are `File.list()` (readdir) and
 * `Os.lstat()` (stat). Symlinks are not followed. Folders are scanned in parallel because
 * shared storage sits behind a FUSE daemon, where stat latency dominates.
 */
class StorageScanner(private val rootPath: String) {
    val filesScanned = AtomicLong()
    val bytesScanned = AtomicLong()

    /** Bytes per [FileCategory], excluding the app-owned `Android/` folder. */
    val categoryBytes = AtomicLongArray(FileCategory.entries.size)

    @Volatile
    var currentPath: String = rootPath
        private set

    private val protectedPaths = setOf("$rootPath/Android/data", "$rootPath/Android/obb")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(PARALLELISM)

    suspend fun scan(): StorageNode {
        val root = StorageNode(rootPath, null, true, null, mtimeOf(rootPath))
        withContext(dispatcher) { scanDirectory(root, rootPath, inAndroidFolder = false) }
        return root
    }

    private suspend fun scanDirectory(node: StorageNode, path: String, inAndroidFolder: Boolean) {
        currentPath = path
        val names = File(path).list()
        if (names == null) {
            node.restricted = true
            node.categoryBytes = LongArray(FileCategory.entries.size)
            return
        }
        val kids = ArrayList<StorageNode>(names.size)
        val folders = ArrayList<StorageNode>()
        for (name in names) {
            coroutineContext.ensureActive()
            val childPath = "$path/$name"
            val stat = try {
                Os.lstat(childPath)
            } catch (_: ErrnoException) {
                continue
            }
            val mtime = stat.st_mtime * 1000L
            when {
                OsConstants.S_ISDIR(stat.st_mode) -> {
                    val folder = StorageNode(name, node, true, null, mtime)
                    kids += folder
                    folders += folder
                }
                OsConstants.S_ISREG(stat.st_mode) -> {
                    val category = FileCategory.of(name)
                    val file = StorageNode(name, node, false, category, mtime)
                    file.size = stat.st_size
                    file.fileCount = 1
                    kids += file
                    filesScanned.incrementAndGet()
                    bytesScanned.addAndGet(stat.st_size)
                    if (!inAndroidFolder) categoryBytes.addAndGet(category.ordinal, stat.st_size)
                }
                // Symlinks, sockets and device nodes are skipped.
            }
        }

        if (folders.isNotEmpty()) {
            coroutineScope {
                for (folder in folders) {
                    val folderPath = "$path/${folder.name}"
                    if (folderPath in protectedPaths) {
                        folder.restricted = true
                        folder.categoryBytes = LongArray(FileCategory.entries.size)
                        continue
                    }
                    val android = inAndroidFolder || (node.isRoot && folder.name == "Android")
                    launch { scanDirectory(folder, folderPath, android) }
                }
            }
        }

        node.complete(kids)
    }

    private fun mtimeOf(path: String): Long =
        try {
            Os.lstat(path).st_mtime * 1000L
        } catch (_: ErrnoException) {
            0L
        }

    private companion object {
        const val PARALLELISM = 8
    }
}
