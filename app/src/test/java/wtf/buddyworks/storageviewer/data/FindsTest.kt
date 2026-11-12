package wtf.buddyworks.storageviewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 2_000L * day
    private val old = now - 400 * day
    private val recent = now - 30 * day
    private val root = "/storage/emulated/0"

    private val tree = testTree(
        root,
        mapOf(
            "DCIM" to mapOf(
                "Camera" to mapOf(
                    "trip.mp4" to TestFile(900_000_000L, old),
                    "new.mp4" to TestFile(600_000_000L, recent),
                    "small.jpg" to TestFile(3_000_000L, old),
                ),
            ),
            "Download" to mapOf("Setup.zip" to TestFile(70_000_000L, old), "camera-manual.pdf" to 2_000_000L),
            "Android" to mapOf(
                "media" to mapOf(
                    "com.whatsapp" to mapOf("Video.mp4" to TestFile(80_000_000L, old)),
                    "com.gone.app" to mapOf("cache.bin" to 5_000L),
                    "com.archived.app" to mapOf("x.bin" to 1_000L),
                ),
            ),
            "Telegram" to mapOf("Telegram Video" to mapOf("v.mp4" to 10_000L)),
            "Pictures" to mapOf("Instagram" to mapOf("i.jpg" to 1_000L), "Screenshots" to mapOf("s.png" to 1_000L)),
            "Music" to mapOf("song.mp3" to 1_000L),
            "Notes" to mapOf("n.txt" to 1_000L),
            "com.example.tool" to mapOf("log.txt" to 1_000L),
        ),
    )

    private fun app(packageName: String, label: String, isSystem: Boolean = false) = AppEntry(
        packageName = packageName,
        label = label,
        isSystem = isSystem,
        versionName = null,
        appBytes = 1L,
        dataBytes = 0L,
        cacheBytes = 0L,
        hasStats = true,
    )

    @Test
    fun fileTypeListsCountTheAppFoldersSeparately() {
        val videos = filesOfCategory(tree, FileCategory.Videos)
        assertEquals(listOf("trip.mp4", "new.mp4", "Video.mp4", "v.mp4"), videos.files.map { it.name })
        assertEquals(1_580_010_000L, videos.totalBytes)
        // WhatsApp's video lives in Android/media, which the summary counts under Apps.
        assertEquals(80_000_000L, videos.appFolderBytes)
    }

    @Test
    fun largeOldFilesAreBigAndUntouchedForAYear() {
        val found = largeOldFiles(tree, now)
        assertEquals(listOf("trip.mp4", "Video.mp4", "Setup.zip"), found.files.map { it.name })
        assertEquals(1_050_000_000L, found.totalBytes)
    }

    @Test
    fun searchMatchesFilesAndFoldersIgnoringCaseLargestFirst() {
        assertEquals(
            listOf("Camera", "camera-manual.pdf"),
            searchTree(tree, " CAMERA ").map { it.name },
        )
        assertTrue(searchTree(tree, "   ").isEmpty())
    }

    @Test
    fun foldersAreMatchedToTheirApps() {
        val apps = listOf(
            app("com.whatsapp", "WhatsApp"),
            app("org.telegram.messenger", "Telegram"),
            app("com.instagram.android", "Instagram"),
            app("com.sec.android.app.camera", "Camera", isSystem = true),
            app("com.example.music", "Music"),
            app("com.example.notes", "Notes"),
            app("com.other.notes", "Notes"),
            app("com.example.tool", "Tool"),
        )
        val known = apps.map { it.packageName }.toSet() + "com.archived.app"
        val owners = findFolderOwners(tree, apps, known)

        assertEquals(FolderOwner.App("com.whatsapp", "WhatsApp"), owners["$root/Android/media/com.whatsapp"])
        assertEquals(FolderOwner.Uninstalled("com.gone.app"), owners["$root/Android/media/com.gone.app"])
        assertEquals(FolderOwner.App("org.telegram.messenger", "Telegram"), owners["$root/Telegram"])
        assertEquals(FolderOwner.App("com.instagram.android", "Instagram"), owners["$root/Pictures/Instagram"])
        assertEquals(FolderOwner.App("com.sec.android.app.camera", "Camera"), owners["$root/DCIM/Camera"])
        assertEquals(FolderOwner.App("com.example.tool", "Tool"), owners["$root/com.example.tool"])
        // Archived apps keep their folder; standard folders belong to no app; two "Notes" apps are ambiguous.
        val unowned = listOf("Android/media/com.archived.app", "Music", "Notes", "Pictures/Screenshots", "Telegram/Telegram Video")
        unowned.forEach { assertEquals(it, null, owners["$root/$it"]) }

        assertEquals(listOf("com.gone.app"), leftoverFolders(tree, owners).map { it.name })
    }
}
