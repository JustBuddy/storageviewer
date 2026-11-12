package wtf.buddyworks.storageviewer.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

data class DeviceStorage(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

class ScanResult(
    val root: StorageNode,
    /** Bytes per [FileCategory] outside the app-owned `Android/` folder. */
    val categoryBytes: LongArray,
    val durationMs: Long,
    /** [SystemClock.elapsedRealtime] when the scan finished. */
    val finishedAt: Long,
)

sealed interface ScanState {
    /** The latest complete scan. It stays available while a rescan runs and after one fails. */
    val result: ScanResult?

    data object Idle : ScanState {
        override val result: ScanResult? get() = null
    }

    data class Scanning(
        val files: Long,
        val bytes: Long,
        val currentPath: String,
        override val result: ScanResult?,
    ) : ScanState

    data class Done(override val result: ScanResult) : ScanState

    data class Failed(val message: String, override val result: ScanResult?) : ScanState
}

/** How long a scan or app list is trusted before returning to the app refreshes it. */
internal const val STALE_AFTER_MS = 10 * 60_000L

/**
 * Whether data produced at [producedAt] should be refreshed at [now]: something was handed
 * to another app since ([changedSince], which may have deleted or uninstalled things), or it
 * is older than [STALE_AFTER_MS]. All times are [SystemClock.elapsedRealtime].
 */
internal fun needsRefresh(producedAt: Long, changedSince: Long, now: Long): Boolean =
    changedSince > producedAt || now - producedAt > STALE_AFTER_MS

/** Owns the storage scan so it survives configuration changes such as folding. */
class StorageRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _device = MutableStateFlow<DeviceStorage?>(null)
    val device: StateFlow<DeviceStorage?> = _device.asStateFlow()

    private var job: Job? = null

    /** Only the newest scan may publish; a cancelled one can still be finishing a write. */
    private val lock = Any()
    private var generation = 0

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    /** Starts a scan unless one is running or finished, or always when [force] is set. */
    fun scan(force: Boolean = false) {
        refreshDevice()
        val current = _state.value
        if (!force && (current is ScanState.Scanning || current is ScanState.Done)) return
        val previous = current.result
        val gen = synchronized(lock) { ++generation }
        job?.cancel()
        job = scope.launch {
            val scanner = StorageScanner(rootPath)
            val started = SystemClock.elapsedRealtime()
            val ticker = launch {
                while (isActive) {
                    publish(
                        gen,
                        ScanState.Scanning(
                            files = scanner.filesScanned.get(),
                            bytes = scanner.bytesScanned.get(),
                            currentPath = scanner.currentPath,
                            result = previous,
                        ),
                    )
                    delay(100)
                }
            }
            val outcome = try {
                val root = scanner.scan()
                val categories = LongArray(FileCategory.entries.size) { scanner.categoryBytes[it] }
                val finished = SystemClock.elapsedRealtime()
                ScanState.Done(ScanResult(root, categories, finished - started, finished))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanState.Failed(e.message ?: e.javaClass.simpleName, previous)
            }
            // Wait for the ticker to stop, or its last progress update could land after the result.
            ticker.cancelAndJoin()
            publish(gen, outcome)
        }
    }

    /**
     * Called when the app comes back to the foreground: scans if there is no result yet, and
     * rescans in the background (keeping the old result on screen) when it may be out of date.
     */
    fun refresh(changedSince: Long) {
        val current = _state.value
        if (current is ScanState.Done && needsRefresh(current.result.finishedAt, changedSince, SystemClock.elapsedRealtime())) {
            scan(force = true)
        } else {
            scan()
        }
    }

    fun refreshDevice() {
        scope.launch {
            _device.value = withContext(Dispatchers.IO) {
                runCatching {
                    val stats = context.getSystemService(StorageStatsManager::class.java)
                    DeviceStorage(
                        totalBytes = nominalCapacity(stats.getTotalBytes(StorageManager.UUID_DEFAULT)),
                        freeBytes = stats.getFreeBytes(StorageManager.UUID_DEFAULT),
                    )
                }.getOrNull()
            }
        }
    }

    private fun publish(gen: Int, state: ScanState) {
        synchronized(lock) {
            if (gen == generation) _state.value = state
        }
    }
}

/**
 * Samsung reports capacity as a binary power of two (256 GiB = 274.9 GB), while One UI's
 * Device care shows the advertised size (256 GB) and derives "used" from it. Mapping to the
 * advertised size keeps our numbers identical to Device care's.
 */
internal fun nominalCapacity(bytes: Long): Long {
    val gib = bytes / (1L shl 30).toDouble()
    if (gib < 1.0) return bytes
    val power = 2.0.pow(round(log2(gib)))
    return if (abs(gib - power) / power < 0.01) (power * 1e9).toLong() else bytes
}
