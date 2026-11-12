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
)

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val files: Long, val bytes: Long, val currentPath: String) : ScanState
    data class Done(val result: ScanResult) : ScanState
    data class Failed(val message: String) : ScanState
}

/** Owns the storage scan so it survives configuration changes such as folding. */
class StorageRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _device = MutableStateFlow<DeviceStorage?>(null)
    val device: StateFlow<DeviceStorage?> = _device.asStateFlow()

    private var job: Job? = null

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    /** Starts a scan unless one is running or finished, or always when [force] is set. */
    fun scan(force: Boolean = false) {
        refreshDevice()
        val current = _state.value
        if (!force && (current is ScanState.Scanning || current is ScanState.Done)) return
        job?.cancel()
        job = scope.launch {
            val scanner = StorageScanner(rootPath)
            val started = SystemClock.elapsedRealtime()
            val ticker = launch {
                while (isActive) {
                    _state.value = ScanState.Scanning(
                        files = scanner.filesScanned.get(),
                        bytes = scanner.bytesScanned.get(),
                        currentPath = scanner.currentPath,
                    )
                    delay(100)
                }
            }
            try {
                val root = scanner.scan()
                ticker.cancel()
                val categories = LongArray(FileCategory.entries.size) { scanner.categoryBytes[it] }
                _state.value = ScanState.Done(
                    ScanResult(root, categories, SystemClock.elapsedRealtime() - started),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ticker.cancel()
                _state.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
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
