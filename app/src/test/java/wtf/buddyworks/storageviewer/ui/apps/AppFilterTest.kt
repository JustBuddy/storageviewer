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

    private fun labels(filter: AppFilter, query: String = "") = filterApps(apps, filter, query, now = 0L).map { it.label }

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

    @Test
    fun unusedMeansDownloadedInstalledAndNotUsedForMonths() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_000L * day
        fun used(label: String, lastUsed: Long?, installed: Long = now - 400 * day, system: Boolean = false, unopened: Boolean = false) =
            app(label, "pkg.$label", system).copy(installedAt = installed, lastUsedAt = lastUsed, worksUnopened = unopened)
        val candidates = listOf(
            used("Old", lastUsed = now - 120 * day),
            used("NeverOpened", lastUsed = 0L),
            used("Recent", lastUsed = now - 10 * day),
            used("JustInstalled", lastUsed = 0L, installed = now - 20 * day),
            used("Keyboard", lastUsed = 0L, unopened = true),
            used("SystemApp", lastUsed = 0L, system = true),
            used("NoUsageAccess", lastUsed = null),
        )
        assertEquals(
            listOf("Old", "NeverOpened"),
            filterApps(candidates, AppFilter.Unused, "", now).map { it.label },
        )
    }
}
