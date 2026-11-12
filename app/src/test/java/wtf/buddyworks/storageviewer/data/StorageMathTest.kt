package wtf.buddyworks.storageviewer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageMathTest {
    @Test
    fun binaryCapacityMapsToAdvertisedSize() {
        // The Fold8 reports 2^38 bytes; Device care shows "256 GB".
        assertEquals(256_000_000_000L, nominalCapacity(1L shl 38))
        assertEquals(512_000_000_000L, nominalCapacity(1L shl 39))
    }

    @Test
    fun otherCapacitiesAreLeftAlone() {
        assertEquals(256_000_000_000L, nominalCapacity(256_000_000_000L))
        assertEquals(123_456_789_000L, nominalCapacity(123_456_789_000L))
    }

    @Test
    fun fileCategoriesComeFromTheExtension() {
        assertEquals(FileCategory.Images, FileCategory.of("IMG_0001.JPG"))
        assertEquals(FileCategory.Videos, FileCategory.of("clip.mp4"))
        assertEquals(FileCategory.Archives, FileCategory.of("backup.tar.gz"))
        assertEquals(FileCategory.Archives, FileCategory.of("game.apk"))
        assertEquals(FileCategory.Other, FileCategory.of(".nomedia"))
        assertEquals(FileCategory.Other, FileCategory.of("README"))
        assertEquals(FileCategory.Other, FileCategory.of("trailing."))
    }
}
