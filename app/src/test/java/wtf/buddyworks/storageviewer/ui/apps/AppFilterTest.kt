package wtf.buddyworks.storageviewer.ui.apps

import wtf.buddyworks.storageviewer.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {
    private fun app(label: String, packageName: String, isSystem: Boolean) = AppEntry(
        packageName = packageName,
        label = label,
        isSystem = isSystem,
        versionName = null,
        appBytes = 1L,
        dataBytes = 0L,
        cacheBytes = 0L,
        hasStats = false,
    )

    private val apps = listOf(
        app("VLC", "org.videolan.vlc", isSystem = false),
        app("Settings", "com.android.settings", isSystem = true),
        app("Wikipedia", "org.wikipedia", isSystem = false),
        app("Phone", "com.samsung.android.dialer", isSystem = true),
    )

    private fun labels(filter: AppFilter, query: String = "") = filterApps(apps, filter, query).map { it.label }

    @Test
    fun filtersSplitDownloadedAndSystemAppsAndKeepTheOrder() {
        assertEquals(listOf("VLC", "Settings", "Wikipedia", "Phone"), labels(AppFilter.All))
        assertEquals(listOf("VLC", "Wikipedia"), labels(AppFilter.Downloaded))
        assertEquals(listOf("Settings", "Phone"), labels(AppFilter.System))
    }

    @Test
    fun searchMatchesLabelOrPackageIgnoringCaseAndSpaces() {
        assertEquals(listOf("Wikipedia"), labels(AppFilter.All, "  wiki "))
        assertEquals(listOf("Phone"), labels(AppFilter.All, "DIALER"))
        assertEquals(listOf("VLC", "Wikipedia"), labels(AppFilter.Downloaded, "org."))
        assertEquals(emptyList<String>(), labels(AppFilter.System, "vlc"))
    }
}
