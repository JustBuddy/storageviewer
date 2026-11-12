package wtf.buddyworks.storageviewer.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String?,
    /** APK and other install files. */
    val appBytes: Long,
    /** User data including cache, as reported by Android. */
    val dataBytes: Long,
    val cacheBytes: Long,
    /** False when usage access is missing and only the APK size is known. */
    val hasStats: Boolean,
) {
    val totalBytes: Long get() = appBytes + dataBytes
    val userDataBytes: Long get() = (dataBytes - cacheBytes).coerceAtLeast(0L)
}

sealed interface AppsState {
    /** The latest complete list. It stays available while a reload runs and after one fails. */
    val loaded: Loaded?

    data object Idle : AppsState {
        override val loaded: Loaded? get() = null
    }

    data class Loading(val done: Int, val total: Int, override val loaded: Loaded?) : AppsState

    data class Loaded(
        val apps: List<AppEntry>,
        val withStats: Boolean,
        /** [SystemClock.elapsedRealtime] when the list was read. */
        val loadedAt: Long,
    ) : AppsState {
        override val loaded: Loaded get() = this
    }

    data class Failed(val message: String, override val loaded: Loaded?) : AppsState
}

/** Lists installed apps with their sizes. Uses only read-only package and storage queries. */
class AppsRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AppsState>(AppsState.Idle)
    val state: StateFlow<AppsState> = _state.asStateFlow()
    private var job: Job? = null

    /** Only the newest load may publish; a cancelled one can still be finishing a write. */
    private val lock = Any()
    private var generation = 0

    /** Loads the list unless already loaded with the same level of access, or always when [force] is set. */
    fun load(force: Boolean = false) {
        val withStats = Access.hasUsageAccess(context)
        val current = _state.value
        if (!force) {
            if (current is AppsState.Loading) return
            if (current is AppsState.Loaded && current.withStats == withStats) return
        }
        val previous = current.loaded
        val gen = synchronized(lock) { ++generation }
        job?.cancel()
        job = scope.launch {
            val outcome = try {
                AppsState.Loaded(loadApps(gen, withStats, previous), withStats, SystemClock.elapsedRealtime())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppsState.Failed(e.message ?: e.javaClass.simpleName, previous)
            }
            publish(gen, outcome)
        }
    }

    /**
     * Called when the app comes back to the foreground: loads if needed, and reloads in the
     * background (keeping the old list on screen) when it may be out of date, for example
     * after an app was uninstalled from its App info page.
     */
    fun refresh(changedSince: Long) {
        val current = _state.value
        if (current is AppsState.Loaded && needsRefresh(current.loadedAt, changedSince, SystemClock.elapsedRealtime())) {
            load(force = true)
        } else {
            load()
        }
    }

    private fun publish(gen: Int, state: AppsState) {
        synchronized(lock) {
            if (gen == generation) _state.value = state
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadApps(gen: Int, withStats: Boolean, previous: AppsState.Loaded?): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val done = AtomicInteger()
        publish(gen, AppsState.Loading(0, packages.size, previous))
        val io = Dispatchers.IO.limitedParallelism(6)

        packages.mapNotNull { info -> info.applicationInfo?.let { info to it } }
            .map { (info, ai) ->
                async(io) {
                    val label = ai.loadLabel(pm).toString()
                    val storage = if (withStats) {
                        runCatching { stats.queryStatsForPackage(ai.storageUuid, ai.packageName, user) }
                            .getOrNull()
                    } else {
                        null
                    }
                    val entry = AppEntry(
                        packageName = ai.packageName,
                        label = label,
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionName = info.versionName,
                        appBytes = storage?.appBytes ?: apkBytes(ai),
                        dataBytes = storage?.dataBytes ?: 0L,
                        cacheBytes = storage?.cacheBytes ?: 0L,
                        hasStats = storage != null,
                    )
                    val n = done.incrementAndGet()
                    if (n % 8 == 0) publish(gen, AppsState.Loading(n, packages.size, previous))
                    entry
                }
            }
            .awaitAll()
            .sortedWith(compareByDescending<AppEntry> { it.totalBytes }.thenBy { it.label.lowercase() })
    }

    private fun apkBytes(ai: ApplicationInfo): Long {
        val paths = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        return paths.sumOf { File(it).length() }
    }
}
