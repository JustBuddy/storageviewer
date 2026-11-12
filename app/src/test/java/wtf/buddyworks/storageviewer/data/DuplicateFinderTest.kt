package wtf.buddyworks.storageviewer.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class DuplicateFinderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, bytes: ByteArray) {
        val file = File(tmp.root, relative)
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
    }

    @Test
    fun findsIdenticalFilesOnlyAndReadsThemOnce() {
        val photo = Random(1).nextBytes(200_000)
        write("DCIM/Camera/photo.jpg", photo)
        write("Pictures/WhatsApp/IMG-1.jpg", photo)
        write("Download/photo (1).jpg", photo)
        // Same size, head and tail, different middle: only a full read tells it apart.
        write("Download/edited.jpg", photo.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() })
        // Same size, different start.
        write("Download/other.jpg", Random(2).nextBytes(200_000))
        // Identical but too small to be worth it.
        val small = Random(3).nextBytes(50_000)
        write("Documents/a.txt", small)
        write("Documents/b.txt", small)

        val root = tmp.root.absolutePath.replace('\\', '/')
        val tree = runBlocking { StorageScanner(root, JvmScanFs).scan() }
        val finder = DuplicateFinder()
        var total = -1L
        val groups = runBlocking { finder.find(tree) { _, all -> total = all } }

        assertEquals(1, groups.size)
        assertEquals(
            setOf("photo.jpg", "IMG-1.jpg", "photo (1).jpg"),
            groups[0].files.map { it.name }.toSet(),
        )
        assertEquals(400_000L, groups[0].extraBytes)
        // The three copies and the edited file were read in full; the rest only in part.
        assertEquals(800_000L, total)

        // A second check, as after a rescan, reuses the digests instead of reading again.
        val again = runBlocking { finder.find(tree) { _, all -> total = all } }
        assertEquals(0L, total)
        assertEquals(groups[0].files.map { it.path }.toSet(), again[0].files.map { it.path }.toSet())
    }
}
