package wtf.buddyworks.storageviewer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    private val scannedAt = 1_000_000L

    @Test
    fun freshDataWithoutAHandoffIsKept() {
        assertFalse(needsRefresh(producedAt = scannedAt, changedSince = 0L, now = scannedAt + 5_000L))
        // A handoff before the scan finished is already reflected in it.
        assertFalse(needsRefresh(producedAt = scannedAt, changedSince = scannedAt - 1L, now = scannedAt + 5_000L))
    }

    @Test
    fun aHandoffAfterTheScanRefreshes() {
        assertTrue(needsRefresh(producedAt = scannedAt, changedSince = scannedAt + 1L, now = scannedAt + 5_000L))
    }

    @Test
    fun oldDataRefreshes() {
        assertFalse(needsRefresh(producedAt = scannedAt, changedSince = 0L, now = scannedAt + STALE_AFTER_MS))
        assertTrue(needsRefresh(producedAt = scannedAt, changedSince = 0L, now = scannedAt + STALE_AFTER_MS + 1L))
    }
}
