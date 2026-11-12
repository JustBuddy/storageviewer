package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import wtf.buddyworks.storageviewer.StorageViewerApp
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.DeviceStorage
import wtf.buddyworks.storageviewer.data.ScanState
import wtf.buddyworks.storageviewer.data.StorageNode
import kotlinx.coroutines.flow.StateFlow

/** Tree UI state. Folders are tracked by path so expansions survive a rescan. */
class StorageViewModel(app: StorageViewerApp) : ViewModel() {
    private val repository = app.storage
    val scanState: StateFlow<ScanState> = repository.state
    val device: StateFlow<DeviceStorage?> = repository.device
    val rootPath: String = repository.rootPath

    var hasAccess by mutableStateOf(Access.hasAllFilesAccess())
        private set
    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set
    var focusedPath by mutableStateOf<String?>(null)
        private set
    var childLimits by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Increments when focus is set from outside the tree, so the tree scrolls to it. */
    var revealRequest by mutableIntStateOf(0)
        private set

    fun onResume() {
        hasAccess = Access.hasAllFilesAccess()
        if (hasAccess) repository.scan() else repository.refreshDevice()
    }

    fun rescan() {
        if (hasAccess) repository.scan(force = true)
    }

    /** Single pane: tapping a folder expands or collapses it and makes it the focus. */
    fun toggle(node: StorageNode) {
        toggleExpanded(node)
        focusedPath = node.path
    }

    /** The chevron: expand or collapse without changing the focus. */
    fun toggleExpanded(node: StorageNode) {
        val path = node.path
        expanded = if (path in expanded) expanded - path else expanded + path
    }

    /**
     * Two panes: tapping a folder shows it on the right and opens it in the tree. Tapping the
     * folder that is already shown collapses or re-opens it.
     */
    fun select(node: StorageNode) {
        val path = node.path
        if (focusedPath == path) {
            toggleExpanded(node)
        } else {
            focusedPath = path
            if (node.subfolders.isNotEmpty()) expanded = expanded + path
        }
    }

    /** Focus a folder from the overview, opening the tree down to it so it can be shown there. */
    fun focus(node: StorageNode) {
        focusedPath = node.path
        val lineage = node.lineage().drop(1)
        val open = (if (node.subfolders.isEmpty()) lineage.dropLast(1) else lineage).map { it.path }
        if (open.isNotEmpty()) expanded = expanded + open
        revealRequest++
    }

    fun focusRoot(root: StorageNode) {
        focusedPath = root.path
    }

    fun showMore(parent: StorageNode) {
        val path = parent.path
        childLimits = childLimits + (path to (childLimits[path] ?: PAGE_SIZE) + PAGE_SIZE * 2)
    }

    companion object {
        const val PAGE_SIZE = 40
    }
}
