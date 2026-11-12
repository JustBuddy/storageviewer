package wtf.buddyworks.storageviewer.ui.storage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import wtf.buddyworks.storageviewer.StorageViewerApp
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.DeviceStorage
import wtf.buddyworks.storageviewer.data.DuplicatesState
import wtf.buddyworks.storageviewer.data.FileCategory
import wtf.buddyworks.storageviewer.data.ScanState
import wtf.buddyworks.storageviewer.data.StorageNode
import wtf.buddyworks.storageviewer.system.Handoff
import kotlinx.coroutines.flow.StateFlow

/** A list shown in place of the folder overview. */
sealed interface StoragePanel {
    /** Saved-state form. */
    val key: String

    /** Every file of one type, from the summary's legend. */
    data class Type(val category: FileCategory) : StoragePanel {
        override val key: String get() = "type:${category.name}"
    }

    data object Duplicates : StoragePanel {
        override val key: String get() = "duplicates"
    }

    data object LargeOld : StoragePanel {
        override val key: String get() = "large-old"
    }

    data object Leftovers : StoragePanel {
        override val key: String get() = "leftovers"
    }

    companion object {
        fun parse(key: String?): StoragePanel? = when {
            key == null -> null
            key.startsWith("type:") -> FileCategory.entries.firstOrNull { it.name == key.removePrefix("type:") }?.let(::Type)
            else -> listOf(Duplicates, LargeOld, Leftovers).firstOrNull { it.key == key }
        }
    }
}

/**
 * Tree UI state. Folders are tracked by path so expansions survive a rescan, and the open
 * folders, focus, panel and search are saved so they also survive Android ending the process.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class StorageViewModel(app: StorageViewerApp, saved: SavedStateHandle) : ViewModel() {
    private val repository = app.storage
    val scanState: StateFlow<ScanState> = repository.state
    val device: StateFlow<DeviceStorage?> = repository.device
    val duplicates: StateFlow<DuplicatesState> = repository.duplicates
    val rootPath: String = repository.rootPath

    var hasAccess by mutableStateOf(Access.hasAllFilesAccess())
        private set
    var expanded by saved.saveable { mutableStateOf<Set<String>>(emptySet()) }
        private set
    var focusedPath by saved.saveable { mutableStateOf<String?>(null) }
        private set
    var childLimits by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    private var panelKey by saved.saveable { mutableStateOf<String?>(null) }

    /** The list shown instead of the focused folder's overview, if any. */
    val panel: StoragePanel? get() = StoragePanel.parse(panelKey)

    var searching by saved.saveable { mutableStateOf(false) }
    var query by saved.saveable { mutableStateOf("") }

    /** Increments when focus is set from outside the tree, so the tree scrolls to it. */
    var revealRequest by mutableIntStateOf(0)
        private set

    /** Scans on first start, and again on return when files may have changed meanwhile. */
    fun onResume() {
        hasAccess = Access.hasAllFilesAccess()
        if (hasAccess) {
            val since = scanState.value.result?.finishedAt ?: 0L
            repository.refresh(changedSince = Handoff.lastHandoffAt, changedPaths = Handoff.foldersHandedOffSince(since))
        } else {
            repository.refreshDevice()
        }
    }

    fun rescan() {
        if (hasAccess) repository.scan(force = true)
    }

    /** Single pane: tapping a folder expands or collapses it and makes it the focus. */
    fun toggle(node: StorageNode) {
        toggleExpanded(node)
        focusedPath = node.path
        panelKey = null
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
        if (focusedPath == path && panelKey == null) {
            toggleExpanded(node)
        } else {
            focusedPath = path
            panelKey = null
            if (node.subfolders.isNotEmpty()) expanded = expanded + path
        }
    }

    /** Focus a folder from the overview, opening the tree down to it so it can be shown there. */
    fun focus(node: StorageNode) {
        focusedPath = node.path
        panelKey = null
        val lineage = node.lineage().drop(1)
        val open = (if (node.subfolders.isEmpty()) lineage.dropLast(1) else lineage).map { it.path }
        if (open.isNotEmpty()) expanded = expanded + open
        revealRequest++
    }

    fun focusRoot(root: StorageNode) {
        focusedPath = root.path
        panelKey = null
    }

    fun openPanel(panel: StoragePanel) {
        panelKey = panel.key
    }

    fun closePanel() {
        panelKey = null
    }

    fun closeSearch() {
        searching = false
        query = ""
    }

    fun showMore(parent: StorageNode) {
        val path = parent.path
        childLimits = childLimits + (path to (childLimits[path] ?: PAGE_SIZE) + PAGE_SIZE * 2)
    }

    companion object {
        const val PAGE_SIZE = 40
    }
}
