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
}
