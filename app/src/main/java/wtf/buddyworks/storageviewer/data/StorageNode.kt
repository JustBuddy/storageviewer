package wtf.buddyworks.storageviewer.data

import java.util.PriorityQueue

/**
 * One file or folder in the scanned tree. Fields are filled in by [StorageScanner] and are
 * never changed after the scan result is published.
 */
class StorageNode internal constructor(
    val id: Int,
    /** File name, or the absolute path for the root. */
    val name: String,
    val parent: StorageNode?,
    val isDirectory: Boolean,
    /** File type for files; null for folders. */
    val category: FileCategory?,
    val lastModified: Long,
) {
    var size: Long = 0L
        internal set

    /** Number of files in this subtree (1 for a file). */
    var fileCount: Int = 0
        internal set

    /** Number of folders below this one. */
    var folderCount: Int = 0
        internal set

    /** Children, largest first. */
    var children: List<StorageNode> = emptyList()
        internal set

    /** Only the folders among [children], largest first. */
    var subfolders: List<StorageNode> = emptyList()
        internal set

    /** Bytes per [FileCategory] in this subtree; null for files. */
    var categoryBytes: LongArray? = null
        internal set

    /** True when Android does not let any app list this folder (Android/data, Android/obb). */
    var restricted: Boolean = false
        internal set

    val depth: Int = if (parent == null) 0 else parent.depth + 1

    val isRoot: Boolean get() = parent == null

    val itemCount: Int get() = children.size

    val path: String
        get() = if (parent == null) name else parent.path + "/" + name

    /** What this node mostly consists of, or null for an empty folder. */
    val dominantCategory: FileCategory?
        get() {
            if (!isDirectory) return category
            val bytes = categoryBytes ?: return null
            var best = -1
            for (i in bytes.indices) if (bytes[i] > 0 && (best < 0 || bytes[i] > bytes[best])) best = i
            return if (best < 0) null else FileCategory.entries[best]
        }

    /** This node and its parents, starting at the root. */
    fun lineage(): List<StorageNode> = generateSequence(this) { it.parent }.toList().asReversed()

    /** Finds a descendant by absolute path, or null if it no longer exists. */
    fun find(absolutePath: String): StorageNode? {
        if (absolutePath == name) return this
        if (!absolutePath.startsWith("$name/")) return null
        var node: StorageNode = this
        for (segment in absolutePath.substring(name.length + 1).split('/')) {
            node = node.children.firstOrNull { it.name == segment } ?: return null
        }
        return node
    }

    /** The [limit] largest files anywhere below this node, largest first. */
    fun largestFiles(limit: Int): List<StorageNode> {
        if (!isDirectory) return listOf(this)
        val heap = PriorityQueue<StorageNode>(limit + 1, compareBy { it.size })
        val stack = ArrayDeque<StorageNode>()
        stack.addLast(this)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            for (child in node.children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (heap.size < limit) {
                    heap.add(child)
                } else if (child.size > heap.peek()!!.size) {
                    heap.poll()
                    heap.add(child)
                }
            }
        }
        return heap.sortedByDescending { it.size }
    }
}
