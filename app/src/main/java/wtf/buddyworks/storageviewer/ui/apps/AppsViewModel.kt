package wtf.buddyworks.storageviewer.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import wtf.buddyworks.storageviewer.StorageViewerApp
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.AppEntry
import wtf.buddyworks.storageviewer.data.AppsState
import kotlinx.coroutines.flow.StateFlow

enum class AppFilter { All, Downloaded, System }

class AppsViewModel(private val app: StorageViewerApp) : ViewModel() {
    val state: StateFlow<AppsState> = app.apps.state

    var hasUsageAccess by mutableStateOf(Access.hasUsageAccess(app))
        private set
    /** Downloaded first: preinstalled apps are rarely what frees up space. */
    var filter by mutableStateOf(AppFilter.Downloaded)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)

    fun onResume() {
        hasUsageAccess = Access.hasUsageAccess(app)
        app.apps.load()
    }

    fun refresh() = app.apps.load(force = true)

    fun closeSearch() {
        searching = false
        query = ""
    }

    /** The apps matching the current filter and search, still sorted by size. */
    fun visible(apps: List<AppEntry>): List<AppEntry> {
        val q = query.trim()
        return apps.filter { entry ->
            val matchesFilter = when (filter) {
                AppFilter.All -> true
                AppFilter.Downloaded -> !entry.isSystem
                AppFilter.System -> entry.isSystem
            }
            matchesFilter && (q.isEmpty() || entry.label.contains(q, ignoreCase = true) ||
                entry.packageName.contains(q, ignoreCase = true))
        }
    }
}
