package wtf.buddyworks.storageviewer.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatTest {
    private lateinit var saved: Locale

    @Before
    fun setUp() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(saved)

    @Test
    fun smallSizesAreWholeBytes() {
        assertEquals("0 B", Format.bytes(0))
        assertEquals("999 B", Format.bytes(999))
    }

    @Test
    fun usesDecimalUnitsWithThreeSignificantDigits() {
        // Same numbers My Files shows for these files on the Fold8.
        assertEquals("541 KB", Format.bytes(541_082))
        assertEquals("11.9 MB", Format.bytes(11_940_567))
        assertEquals("2.50 GB", Format.bytes(2_498_379_622))
        assertEquals("1.00 KB", Format.bytes(1_000))
    }

    @Test
    fun roundsUpIntoTheNextUnit() {
        assertEquals("1.00 MB", Format.bytes(999_999))
    }

    @Test
    fun followsTheLocaleDecimalSeparator() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("2,50 GB", Format.bytes(2_498_379_622))
    }

    @Test
    fun tinySharesAreNotShownAsZero() {
        assertEquals("<1%", Format.percent(1, 10_000))
        assertEquals("0%", Format.percent(0, 10_000))
        assertEquals("50%", Format.percent(5, 10))
    }

    @Test
    fun agoRoundsDownToTheLargestSensibleUnit() {
        val day = 24L * 60 * 60 * 1000
        val now = 10_000L * day
        assertEquals(Format.AgoUnit.Today to 0, Format.ago(now - day / 2, now))
        assertEquals(Format.AgoUnit.Days to 13, Format.ago(now - 13 * day, now))
        assertEquals(Format.AgoUnit.Weeks to 2, Format.ago(now - 14 * day, now))
        assertEquals(Format.AgoUnit.Months to 3, Format.ago(now - 90 * day, now))
        assertEquals(Format.AgoUnit.Years to 2, Format.ago(now - 730 * day, now))
        // A clock that went backwards reads as today, not as a negative age.
        assertEquals(Format.AgoUnit.Today to 0, Format.ago(now + day, now))
    }
}
