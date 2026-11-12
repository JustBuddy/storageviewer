package wtf.buddyworks.storageviewer.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
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
    data object Idle : AppsState
    data class Loading(val done: Int, val total: Int) : AppsState
    data class Loaded(val apps: List<AppEntry>, val withStats: Boolean) : AppsState
}

/** Lists installed apps with their sizes. Uses only read-only package and storage queries. */
class AppsRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AppsState>(AppsState.Idle)
    val state: StateFlow<AppsState> = _state.asStateFlow()
    private var job: Job? = null

    /** Loads the list unless already loaded with the same level of access. */
    fun load(force: Boolean = false) {
        val withStats = Access.hasUsageAccess(context)
        val current = _state.value
        if (!force) {
            if (current is AppsState.Loading) return
            if (current is AppsState.Loaded && current.withStats == withStats) return
        }
        job?.cancel()
        job = scope.launch {
            try {
                _state.value = AppsState.Loaded(loadApps(withStats), withStats)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = AppsState.Loaded(emptyList(), withStats)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadApps(withStats: Boolean): List<AppEntry> = withContext(Dispatchers.IO) {
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
        _state.value = AppsState.Loading(0, packages.size)
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
                    if (n % 8 == 0) _state.value = AppsState.Loading(n, packages.size)
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
