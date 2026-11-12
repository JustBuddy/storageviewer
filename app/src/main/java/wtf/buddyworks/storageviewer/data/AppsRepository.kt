package wtf.buddyworks.storageviewer.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
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
    /** When this copy of the app was installed (epoch ms). */
    val installedAt: Long = 0L,
    /**
     * When the user last had the app open or it last ran a foreground service (epoch ms), 0
     * when Android has no record of either. Null without usage access.
     */
    val lastUsedAt: Long? = null,
    /** Does its job without being opened: keyboard, home screen, live wallpaper and the like. */
    val worksUnopened: Boolean = false,
) {
    val totalBytes: Long get() = appBytes + dataBytes
    val userDataBytes: Long get() = (dataBytes - cacheBytes).coerceAtLeast(0L)
}

/** Downloaded apps not used for this long count as unused. */
const val UNUSED_AFTER_MS = 90L * 24 * 60 * 60 * 1000

/**
 * A downloaded app installed and not used in the last [UNUSED_AFTER_MS] before [now]. Apps
 * that work without being opened never count, and without usage access none do.
 */
fun AppEntry.isUnused(now: Long): Boolean {
    val lastUsed = lastUsedAt ?: return false
    val cutoff = now - UNUSED_AFTER_MS
    return !isSystem && !worksUnopened && installedAt in 1..cutoff && lastUsed <= cutoff
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
        /** Every package Android knows, including archived ones and ones uninstalled with their data kept. */
        val knownPackages: Set<String> = apps.mapTo(HashSet()) { it.packageName },
    ) : AppsState {
        override val loaded: Loaded get() = this
    }

    data class Failed(val message: String, override val loaded: Loaded?) : AppsState
}

/** How far back to look for an app's last use. Android keeps yearly usage for a few years. */
private const val USAGE_HISTORY_MS = 3L * 365 * 24 * 60 * 60 * 1000

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
                val apps = loadApps(gen, withStats, previous)
                AppsState.Loaded(apps, withStats, SystemClock.elapsedRealtime(), knownPackages(apps))
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
        val lastUse = if (withStats) runCatching { lastUse() }.getOrNull() else null
        val unopened = worksUnopened(pm)
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
                        installedAt = info.firstInstallTime,
                        lastUsedAt = lastUse?.let { it[ai.packageName] ?: 0L },
                        worksUnopened = ai.packageName in unopened,
                    )
                    val n = done.incrementAndGet()
                    if (n % 8 == 0) publish(gen, AppsState.Loading(n, packages.size, previous))
                    entry
                }
            }
            .awaitAll()
            .sortedWith(compareByDescending<AppEntry> { it.totalBytes }.thenBy { it.label.lowercase() })
    }

    /** Last use per package, over as much usage history as Android keeps. */
    private fun lastUse(): Map<String, Long> {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return usage.queryAndAggregateUsageStats(now - USAGE_HISTORY_MS, now).mapValues { (_, stats) ->
            maxOf(stats.lastTimeUsed, stats.lastTimeVisible, stats.lastTimeForegroundServiceUsed).coerceIn(0L, now)
        }
    }

    /**
     * Apps that do their job without ever being opened, so a missing "last used" means
     * nothing: keyboards, the home screen, a live wallpaper, accessibility services, device
     * admins, notification listeners, the autofill service, and this app.
     */
    private fun worksUnopened(pm: PackageManager): Set<String> = buildSet {
        add(context.packageName)
        runCatching {
            context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) }
        }
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved?.activityInfo?.packageName?.let(::add)
        }
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.packageName?.let(::add) }
        runCatching {
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .forEach { add(it.resolveInfo.serviceInfo.packageName) }
        }
        runCatching { context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.forEach { add(it.packageName) } }
        runCatching { NotificationManagerCompat.getEnabledListenerPackages(context).forEach(::add) }
        runCatching {
            Settings.Secure.getString(context.contentResolver, "autofill_service")
                ?.let(ComponentName::unflattenFromString)
                ?.let { add(it.packageName) }
        }
    }

    /** [apps] plus packages that are archived or were uninstalled with their data kept. */
    private fun knownPackages(apps: List<AppEntry>): Set<String> {
        val known = apps.mapTo(HashSet()) { it.packageName }
        runCatching {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var flags = PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) flags = flags or PackageManager.MATCH_ARCHIVED_PACKAGES
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
            packages.forEach { known += it.packageName }
        }
        return known
    }

    private fun apkBytes(ai: ApplicationInfo): Long {
        val paths = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        return paths.sumOf { File(it).length() }
    }
}
