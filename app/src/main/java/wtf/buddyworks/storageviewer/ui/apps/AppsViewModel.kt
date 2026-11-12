package wtf.buddyworks.storageviewer.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import wtf.buddyworks.storageviewer.StorageViewerApp
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.AppEntry
import wtf.buddyworks.storageviewer.data.AppsState
import wtf.buddyworks.storageviewer.data.isUnused
import wtf.buddyworks.storageviewer.system.Handoff
import kotlinx.coroutines.flow.StateFlow

enum class AppFilter {
    All,
    Downloaded,
    System,

    /** Downloaded apps not used for months ([isUnused]). */
    Unused,
}

/** Filter, search and list state; the filter and search survive Android ending the process. */
@OptIn(SavedStateHandleSaveableApi::class)
class AppsViewModel(private val app: StorageViewerApp, saved: SavedStateHandle) : ViewModel() {
    val state: StateFlow<AppsState> = app.apps.state

    var hasUsageAccess by mutableStateOf(Access.hasUsageAccess(app))
        private set
    /** Downloaded first: preinstalled apps are rarely what frees up space. */
    var filter by saved.saveable { mutableStateOf(AppFilter.Downloaded) }
    var query by saved.saveable { mutableStateOf("") }
    var searching by saved.saveable { mutableStateOf(false) }

    /** Loads on first start, and again on return when apps may have changed meanwhile. */
    fun onResume() {
        hasUsageAccess = Access.hasUsageAccess(app)
        app.apps.refresh(changedSince = Handoff.lastHandoffAt)
    }

    fun refresh() = app.apps.load(force = true)

    fun closeSearch() {
        searching = false
        query = ""
    }

    /** The apps matching the current filter and search, still sorted by size. */
    fun visible(apps: List<AppEntry>): List<AppEntry> = filterApps(apps, filter, query, System.currentTimeMillis())
}

/**
 * The [apps] in [filter] whose label or package name contains [query], in their original
 * order. [now] (epoch ms) decides which apps are unused.
 */
fun filterApps(apps: List<AppEntry>, filter: AppFilter, query: String, now: Long): List<AppEntry> {
    val q = query.trim()
    return apps.filter { entry ->
        val matchesFilter = when (filter) {
            AppFilter.All -> true
            AppFilter.Downloaded -> !entry.isSystem
            AppFilter.System -> entry.isSystem
            AppFilter.Unused -> entry.isUnused(now)
        }
        matchesFilter && (q.isEmpty() || entry.label.contains(q, ignoreCase = true) ||
            entry.packageName.contains(q, ignoreCase = true))
    }
}
