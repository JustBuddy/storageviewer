package wtf.buddyworks.storageviewer.data

/** Files of at least this size count as large. */
const val LARGE_FILE_BYTES = 50_000_000L

/** Files not modified for this long count as old. */
const val OLD_AFTER_MS = 365L * 24 * 60 * 60 * 1000

/** Some files from a tree, largest first. */
class FileSelection(
    val files: List<StorageNode>,
    val totalBytes: Long,
    /**
     * Bytes in the app-owned `Android/` folder. Android counts them in each app's size, so the
     * storage summary shows them under Apps rather than under their file type.
     */
    val appFolderBytes: Long,
) {
    companion object {
        val Empty = FileSelection(emptyList(), 0L, 0L)
    }
}

/** Every file of [category] below [root], largest first. */
fun filesOfCategory(root: StorageNode, category: FileCategory): FileSelection =
    selectFiles(root) { (it.category ?: FileCategory.Other) == category }

/** Files of at least [LARGE_FILE_BYTES] not modified in the [OLD_AFTER_MS] before [now], largest first. */
fun largeOldFiles(root: StorageNode, now: Long): FileSelection {
    val before = now - OLD_AFTER_MS
    return selectFiles(root) { it.size >= LARGE_FILE_BYTES && it.lastModified in 1..before }
}

private inline fun selectFiles(root: StorageNode, predicate: (StorageNode) -> Boolean): FileSelection {
    val files = ArrayList<StorageNode>()
    var total = 0L
    var appFolder = 0L
    val stack = ArrayDeque<Pair<StorageNode, Boolean>>()
    stack.addLast(root to false)
    while (stack.isNotEmpty()) {
        val (folder, inAndroid) = stack.removeLast()
        for (child in folder.children) {
            if (child.isDirectory) {
                stack.addLast(child to (inAndroid || (folder === root && child.name == "Android")))
            } else if (predicate(child)) {
                files += child
                total += child.size
                if (inAndroid) appFolder += child.size
            }
        }
    }
    files.sortWith(compareByDescending<StorageNode> { it.size }.thenBy { it.name.lowercase() })
    return FileSelection(files, total, appFolder)
}

/** Files and folders below [root] whose name contains [query], ignoring case; largest first. */
fun searchTree(root: StorageNode, query: String): List<StorageNode> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val matches = ArrayList<StorageNode>()
    val stack = ArrayDeque<StorageNode>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        for (child in stack.removeLast().children) {
            if (child.name.contains(q, ignoreCase = true)) matches += child
            if (child.isDirectory) stack.addLast(child)
        }
    }
    matches.sortWith(compareByDescending<StorageNode> { it.size }.thenBy { it.name.lowercase() })
    return matches
}

/** The app a folder belongs to, or the package of an app that is no longer installed. */
sealed interface FolderOwner {
    val packageName: String

    data class App(override val packageName: String, val label: String) : FolderOwner

    data class Uninstalled(override val packageName: String) : FolderOwner
}

/** Android's standard top-level folders. They belong to no app, but apps keep folders inside them. */
private val StandardFolders = setOf(
    "Alarms", "Audiobooks", "DCIM", "Documents", "Download", "Movies", "Music", "Notifications",
    "Pictures", "Podcasts", "Recordings", "Ringtones",
)

private val PackageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

/**
 * Folder paths below [root] that belong to an app, matched from what folders are called:
 *
 * - `Android/media/<package>`: that package. If Android no longer knows it ([knownPackages]),
 *   the folder was left behind by an uninstalled app.
 * - Top-level folders, and folders directly inside the standard ones (`Pictures/Telegram`,
 *   `DCIM/Camera`): the app whose package name or label matches the folder name.
 */
fun findFolderOwners(root: StorageNode, apps: List<AppEntry>, knownPackages: Set<String>): Map<String, FolderOwner> {
    val owners = HashMap<String, FolderOwner>()
    val byPackage = apps.associateBy { it.packageName }
    // Labels shared by several apps say nothing about which one a folder belongs to.
    val byLabel = apps.groupBy { normalizedLabel(it.label) }
        .filter { (label, matches) -> label.length >= 3 && matches.size == 1 }
        .mapValues { it.value[0] }

    fun match(folder: StorageNode, path: String) {
        val app = byPackage[folder.name] ?: byLabel[normalizedLabel(folder.name)] ?: return
        owners[path] = FolderOwner.App(app.packageName, app.label)
    }

    for (top in root.subfolders) {
        val topPath = root.name + "/" + top.name
        when {
            top.name == "Android" -> {
                val media = top.subfolders.firstOrNull { it.name == "media" } ?: continue
                for (folder in media.subfolders) {
                    val path = "$topPath/media/${folder.name}"
                    val app = byPackage[folder.name]
                    when {
                        app != null -> owners[path] = FolderOwner.App(app.packageName, app.label)
                        folder.name in knownPackages -> Unit
                        PackageName.matches(folder.name) -> owners[path] = FolderOwner.Uninstalled(folder.name)
                    }
                }
            }
            top.name in StandardFolders -> top.subfolders.forEach { match(it, topPath + "/" + it.name) }
            else -> match(top, topPath)
        }
    }
    return owners
}

/** Folders left behind by uninstalled apps ([findFolderOwners]), largest first. */
fun leftoverFolders(root: StorageNode, owners: Map<String, FolderOwner>): List<StorageNode> =
    owners.filterValues { it is FolderOwner.Uninstalled }.keys
        .mapNotNull(root::find)
        .sortedByDescending { it.size }

private fun normalizedLabel(label: String): String = label.filter { it.isLetterOrDigit() }.lowercase()
