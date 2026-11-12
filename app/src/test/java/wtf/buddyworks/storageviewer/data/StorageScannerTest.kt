package wtf.buddyworks.storageviewer.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class StorageScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: String

    @Before
    fun setUp() {
        root = tmp.root.absolutePath.replace('\\', '/')
        write("DCIM/Camera/a.jpg", 300)
        write("Download/x.zip", 100)
        write("Download/notes.txt", 10)
        write("Android/data/com.example/cache.bin", 50)
        write("Android/media/com.example/clip.mp4", 70)
        // Fixed folder times, so a rescan sees exactly the changes a test makes.
        listOf("", "DCIM", "DCIM/Camera", "Download", "Android", "Android/data", "Android/media", "Android/media/com.example")
            .forEach { touch(it, 1_000_000L) }
    }

    private fun write(relative: String, size: Int, fill: Byte = 1) {
        val file = File(tmp.root, relative)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(size) { fill })
    }

    private fun touch(relative: String, millis: Long) {
        Files.setLastModifiedTime(File(tmp.root, relative).toPath(), FileTime.fromMillis(millis))
    }

    private fun scan(previous: StorageNode? = null, fullPaths: List<String> = emptyList()): Pair<StorageNode, StorageScanner> =
        runBlocking {
            val scanner = StorageScanner(root, JvmScanFs)
            scanner.scan(previous, fullPaths) to scanner
        }

    @Test
    fun scanAddsUpSizesAndSkipsTheProtectedFolders() {
        val (tree, scanner) = scan()
        assertEquals(480L, tree.size) // Android/data is not counted.
        assertTrue(tree.find("$root/Android/data")!!.restricted)
        assertEquals(listOf("DCIM", "Download", "Android"), tree.children.map { it.name })
        // The summary's file types leave out the app-owned Android folder.
        assertEquals(300L, scanner.categoryBytes[FileCategory.Images.ordinal])
        assertEquals(0L, scanner.categoryBytes[FileCategory.Videos.ordinal])
        assertEquals(4L, scanner.filesScanned.get())
    }

    @Test
    fun rescanTakesOverUnchangedFoldersAndSeesDeletions() {
        val (first, _) = scan()
        val photo = first.find("$root/DCIM/Camera/a.jpg")!!
        val notes = first.find("$root/Download/notes.txt")!!

        File(tmp.root, "Download/x.zip").delete()
        touch("Download", 2_000_000L)
        val (second, scanner) = scan(previous = first)

        assertNull(second.find("$root/Download/x.zip"))
        assertEquals(380L, second.size)
        // The unchanged camera folder's file was taken over and now hangs off the new tree.
        assertSame(photo, second.find("$root/DCIM/Camera/a.jpg"))
        assertSame(second.find("$root/DCIM/Camera"), photo.parent)
        assertEquals("$root/DCIM/Camera/a.jpg", photo.path)
        // Taken-over files still count towards progress and the type split.
        assertEquals(3L, scanner.filesScanned.get())
        assertEquals(300L, scanner.categoryBytes[FileCategory.Images.ordinal])
        // The changed folder was read again from scratch.
        assertNotSame(notes, second.find("$root/Download/notes.txt"))
    }

    @Test
    fun rescanSeesNewFolders() {
        val (first, _) = scan()
        write("DCIM/Screenshots/s.png", 40)
        touch("DCIM", 2_000_000L)
        val (second, _) = scan(previous = first)
        assertEquals(40L, second.find("$root/DCIM/Screenshots")!!.size)
        assertEquals(520L, second.size)
    }

    @Test
    fun handedOffFoldersAreReadInFull() {
        val (first, _) = scan()
        // A file changed in place does not change its folder's time...
        write("DCIM/Camera/a.jpg", 900, fill = 2)
        touch("DCIM/Camera", 1_000_000L)
        val (second, _) = scan(previous = first)
        assertEquals(300L, second.find("$root/DCIM/Camera/a.jpg")!!.size)
        // ...so the folder the user just had open in another app is read again in full.
        val (third, _) = scan(previous = second, fullPaths = listOf("$root/DCIM"))
        assertEquals(900L, third.find("$root/DCIM/Camera/a.jpg")!!.size)
        assertEquals(1_080L, third.size)
    }
}
