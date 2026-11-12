package wtf.buddyworks.storageviewer.data

/** A file with a modification time (epoch ms), for trees where age matters. */
data class TestFile(val size: Long, val modified: Long)

/**
 * Builds a finished tree the way [StorageScanner] does, without touching the file system.
 * Map values are folders, Long values are file sizes, [TestFile]s are files with a time.
 */
fun testTree(rootPath: String, contents: Map<String, Any>): StorageNode = folder(rootPath, null, contents)

@Suppress("UNCHECKED_CAST")
private fun folder(name: String, parent: StorageNode?, contents: Map<String, Any>): StorageNode {
    val node = StorageNode(name, parent, isDirectory = true, category = null, lastModified = 0L)
    val kids = contents.mapTo(ArrayList()) { (childName, value) ->
        when (value) {
            is Map<*, *> -> folder(childName, node, value as Map<String, Any>)
            is Long -> StorageNode(childName, node, isDirectory = false, FileCategory.of(childName), lastModified = 0L).apply {
                size = value
                fileCount = 1
            }
            is TestFile -> StorageNode(childName, node, isDirectory = false, FileCategory.of(childName), value.modified).apply {
                size = value.size
                fileCount = 1
            }
            else -> error("Unsupported entry $childName: $value")
        }
    }
    node.complete(kids)
    return node
}
