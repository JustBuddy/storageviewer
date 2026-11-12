package wtf.buddyworks.storageviewer.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNodeTest {
    private val root = testTree(
        "/storage/emulated/0",
        mapOf(
            "DCIM" to mapOf(
                "Camera" to mapOf("a.jpg" to 300L, "b.mp4" to 900L),
                "cover.png" to 50L,
            ),
            "Download" to mapOf("setup.apk" to 700L, "notes.txt" to 10L),
            "big.zip" to 1_000L,
            "Empty" to emptyMap<String, Any>(),
        ),
    )

    @Test
    fun foldersAddUpTheirContents() {
        assertEquals(2_960L, root.size)
        assertEquals(6, root.fileCount)
        assertEquals(4, root.folderCount) // DCIM, Camera, Download, Empty
        val dcim = root.find("/storage/emulated/0/DCIM")!!
        assertEquals(1_250L, dcim.size)
        assertEquals(1, dcim.folderCount)
        val bytes = LongArray(FileCategory.entries.size)
        bytes[FileCategory.Images.ordinal] = 350L
        bytes[FileCategory.Videos.ordinal] = 900L
        assertArrayEquals(bytes, dcim.categoryBytes)
        assertEquals(FileCategory.Videos, dcim.dominantCategory)
    }

    @Test
    fun childrenAreLargestFirstAndSubfoldersAreOnlyFolders() {
        assertEquals(listOf("DCIM", "big.zip", "Download", "Empty"), root.children.map { it.name })
        assertEquals(listOf("DCIM", "Download", "Empty"), root.subfolders.map { it.name })
    }

    @Test
    fun findWalksAbsolutePaths() {
        assertSame(root, root.find("/storage/emulated/0"))
        assertEquals("b.mp4", root.find("/storage/emulated/0/DCIM/Camera/b.mp4")?.name)
        assertNull(root.find("/storage/emulated/0/DCIM/Missing"))
        // A sibling path that merely starts with the root's name is not inside it.
        assertNull(root.find("/storage/emulated/00/DCIM"))
    }

    @Test
    fun pathAndLineageGoThroughEveryParent() {
        val photo = root.find("/storage/emulated/0/DCIM/Camera/a.jpg")!!
        assertEquals("/storage/emulated/0/DCIM/Camera/a.jpg", photo.path)
        assertEquals(listOf("/storage/emulated/0", "DCIM", "Camera", "a.jpg"), photo.lineage().map { it.name })
    }

    @Test
    fun largestFilesSearchesTheWholeSubtree() {
        assertEquals(listOf("big.zip", "b.mp4", "setup.apk"), root.largestFiles(3).map { it.name })
        assertEquals(6, root.largestFiles(100).size)
        assertTrue(root.largestFiles(0).isEmpty())
        val file = root.find("/storage/emulated/0/big.zip")!!
        assertEquals(listOf(file), file.largestFiles(5))
    }

    @Test
    fun emptyFolderHasNoDominantCategory() {
        val empty = root.find("/storage/emulated/0/Empty")!!
        assertEquals(0L, empty.size)
        assertNull(empty.dominantCategory)
    }
}
