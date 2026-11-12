package wtf.buddyworks.storageviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.coroutines.coroutineContext

/**
 * Walks shared storage and builds a [StorageNode] tree.
 *
 * Read-only by construction: the only file-system calls are readdir and lstat ([ScanFs]).
 * Symlinks are not followed. Folders are scanned in parallel because shared storage sits
 * behind a FUSE daemon, where stat latency dominates.
 */
class StorageScanner(private val rootPath: String, private val fs: ScanFs = OsScanFs) {
    val filesScanned = AtomicLong()
    val bytesScanned = AtomicLong()

    /** Bytes per [FileCategory], excluding the app-owned `Android/` folder. */
    val categoryBytes = AtomicLongArray(FileCategory.entries.size)

    @Volatile
    var currentPath: String = rootPath
        private set

    private val protectedPaths = setOf("$rootPath/Android/data", "$rootPath/Android/obb")

    private var fullPaths: Collection<String> = emptyList()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(PARALLELISM)

    /**
     * Scans everything, or with [previous] only what may have changed since that scan.
     *
     * A folder whose modification time is unchanged still has the same entries, so its files
     * are taken over from [previous] without a stat each; only its subfolders are checked.
     * That misses a file changed in place, so folders at or under [fullPaths] (where the user
     * just was in another app) are always read in full.
     */
    suspend fun scan(previous: StorageNode? = null, fullPaths: Collection<String> = emptyList()): StorageNode {
        this.fullPaths = fullPaths
        val stat = fs.stat(rootPath)
        val root = StorageNode(rootPath, null, true, null, stat?.modifiedMs ?: 0L)
        root.stamp = stat?.stamp ?: 0L
        val old = previous?.takeIf { it.name == rootPath }
        withContext(dispatcher) { scanDirectory(root, rootPath, inAndroidFolder = false, old = old, full = old == null) }
        return root
    }

    private suspend fun scanDirectory(node: StorageNode, path: String, inAndroidFolder: Boolean, old: StorageNode?, full: Boolean) {
        currentPath = path
        val readAll = full || fullPaths.any { path == it || path.startsWith("$it/") }
        val unchanged = !readAll && old != null && !old.restricted && old.stamp != 0L && old.stamp == node.stamp
        val listed = (if (unchanged) takeOver(node, path, inAndroidFolder, old) else null)
            ?: readFolder(node, path, inAndroidFolder, old)
            ?: return
        val (kids, folders) = listed

        if (folders.isNotEmpty()) {
            coroutineScope {
                for ((folder, oldFolder) in folders) {
                    val folderPath = "$path/${folder.name}"
                    if (folderPath in protectedPaths) {
                        folder.restricted = true
                        folder.categoryBytes = LongArray(FileCategory.entries.size)
                        continue
                    }
                    val android = inAndroidFolder || (node.isRoot && folder.name == "Android")
                    launch { scanDirectory(folder, folderPath, android, oldFolder, readAll) }
                }
            }
        }

        node.complete(kids)
    }

    /** New folder nodes paired with the same folder from the previous scan, if any. */
    private data class Listing(val kids: ArrayList<StorageNode>, val folders: List<Pair<StorageNode, StorageNode?>>)

    /** Lists and stats every entry. Null (after marking [node] restricted) when it cannot be listed. */
    private suspend fun readFolder(node: StorageNode, path: String, inAndroidFolder: Boolean, old: StorageNode?): Listing? {
        val names = fs.list(path)
        if (names == null) {
            node.restricted = true
            node.categoryBytes = LongArray(FileCategory.entries.size)
            return null
        }
        val oldFolders = old?.subfolders?.associateBy { it.name }
        val kids = ArrayList<StorageNode>(names.size)
        val folders = ArrayList<Pair<StorageNode, StorageNode?>>()
        for (name in names) {
            coroutineContext.ensureActive()
            val stat = fs.stat("$path/$name") ?: continue
            when (stat.kind) {
                EntryKind.Directory -> {
                    val folder = StorageNode(name, node, true, null, stat.modifiedMs)
                    folder.stamp = stat.stamp
                    kids += folder
                    folders += folder to oldFolders?.get(name)
                }
                EntryKind.File -> {
                    val file = StorageNode(name, node, false, FileCategory.of(name), stat.modifiedMs)
                    file.size = stat.size
                    file.fileCount = 1
                    kids += file
                    countFile(file, inAndroidFolder)
                }
                // Symlinks, sockets and device nodes are skipped.
                EntryKind.Other -> Unit
            }
        }
        return Listing(kids, folders)
    }

    /**
     * Takes over the files of an unchanged folder from the previous scan and stats only its
     * subfolders. Null when a subfolder no longer matches, so the folder is read in full.
     */
    private suspend fun takeOver(node: StorageNode, path: String, inAndroidFolder: Boolean, old: StorageNode): Listing? {
        val folders = ArrayList<Pair<StorageNode, StorageNode?>>(old.subfolders.size)
        for (oldFolder in old.subfolders) {
            coroutineContext.ensureActive()
            val stat = fs.stat("$path/${oldFolder.name}")
            if (stat == null || stat.kind != EntryKind.Directory) return null
            val folder = StorageNode(oldFolder.name, node, true, null, stat.modifiedMs)
            folder.stamp = stat.stamp
            folders += folder to oldFolder
        }
        val kids = ArrayList<StorageNode>(old.children.size)
        folders.mapTo(kids) { it.first }
        for (child in old.children) {
            if (child.isDirectory) continue
            child.parent = node
            kids += child
            countFile(child, inAndroidFolder)
        }
        return Listing(kids, folders)
    }

    private fun countFile(file: StorageNode, inAndroidFolder: Boolean) {
        filesScanned.incrementAndGet()
        bytesScanned.addAndGet(file.size)
        if (!inAndroidFolder) categoryBytes.addAndGet((file.category ?: FileCategory.Other).ordinal, file.size)
    }

    private companion object {
        const val PARALLELISM = 8
    }
}
