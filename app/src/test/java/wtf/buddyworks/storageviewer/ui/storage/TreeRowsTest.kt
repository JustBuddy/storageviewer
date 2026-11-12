package wtf.buddyworks.storageviewer.ui.storage

import wtf.buddyworks.storageviewer.data.testTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeRowsTest {
    private val rootPath = "/storage/emulated/0"

    private fun sample() = testTree(
        rootPath,
        mapOf(
            "DCIM" to mapOf(
                "Camera" to mapOf("a.jpg" to 300L, "b.mp4" to 900L),
                "cover.png" to 50L,
            ),
            "Download" to mapOf("setup.apk" to 700L),
            "big.zip" to 1_000L,
        ),
    )

    private fun List<TreeRow>.items() = filterIsInstance<TreeRow.Item>()

    @Test
    fun collapsedTreeShowsTheTopLevelLargestFirst() {
        val rows = buildTreeRows(sample(), expanded = emptySet(), limits = emptyMap(), foldersOnly = false)
        assertEquals(listOf("DCIM", "big.zip", "Download"), rows.items().map { it.node.name })
        assertTrue(rows.items().all { it.depth == 0 && !it.expanded })
        assertTrue(rows.items().first { it.node.name == "DCIM" }.canExpand)
        assertFalse(rows.items().first { it.node.name == "big.zip" }.canExpand)
    }

    @Test
    fun expandedFoldersShowTheirChildrenIndented() {
        val rows = buildTreeRows(
            sample(),
            expanded = setOf("$rootPath/DCIM", "$rootPath/DCIM/Camera"),
            limits = emptyMap(),
            foldersOnly = false,
        )
        assertEquals(
            listOf("DCIM" to 0, "Camera" to 1, "b.mp4" to 2, "a.jpg" to 2, "cover.png" to 1, "big.zip" to 0, "Download" to 0),
            rows.items().map { it.node.name to it.depth },
        )
        assertEquals("$rootPath/DCIM/Camera/b.mp4", rows.items()[2].path)
    }

    @Test
    fun twoPanesListFoldersOnly() {
        val rows = buildTreeRows(sample(), expanded = setOf("$rootPath/DCIM"), limits = emptyMap(), foldersOnly = true)
        assertEquals(listOf("DCIM", "Camera", "Download"), rows.items().map { it.node.name })
        // Camera holds only files, so in this view it has nothing to expand.
        assertFalse(rows.items().first { it.node.name == "Camera" }.canExpand)
    }

    @Test
    fun longFoldersEndInAShowMoreRow() {
        val many = (1..50).associate { "file$it.bin" to it.toLong() }
        val rows = buildTreeRows(testTree(rootPath, many), expanded = emptySet(), limits = emptyMap(), foldersOnly = false)
        assertEquals(StorageViewModel.PAGE_SIZE, rows.items().size)
        val more = rows.last() as TreeRow.More
        assertEquals(10, more.hidden)
        assertEquals((1..10).sum().toLong(), more.hiddenBytes)

        val all = buildTreeRows(testTree(rootPath, many), emptySet(), mapOf(rootPath to 100), foldersOnly = false)
        assertEquals(50, all.size)
    }

    @Test
    fun rowKeysSurviveARescan() {
        val expanded = setOf("$rootPath/DCIM")
        val before = buildTreeRows(sample(), expanded, emptyMap(), foldersOnly = false).map { it.key }
        val after = buildTreeRows(sample(), expanded, emptyMap(), foldersOnly = false).map { it.key }
        assertEquals(before, after)
        assertEquals(before.size, before.toSet().size)
    }
}
