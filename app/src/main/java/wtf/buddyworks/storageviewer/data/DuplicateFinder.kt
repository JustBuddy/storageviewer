package wtf.buddyworks.storageviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/** Files with identical contents, oldest first (most likely the original). */
class DuplicateGroup(val size: Long, val files: List<StorageNode>) {
    /** The space taken by every copy after the first. */
    val extraBytes: Long get() = size * (files.size - 1)
}

sealed interface DuplicatesState {
    /** The latest complete result. It stays available while a new check runs. */
    val groups: List<DuplicateGroup>?

    data object Idle : DuplicatesState {
        override val groups: List<DuplicateGroup>? get() = null
    }

    /** [checkedBytes] of [totalBytes] read in full so far; the total is 0 until it is known. */
    data class Checking(val checkedBytes: Long, val totalBytes: Long, override val groups: List<DuplicateGroup>?) : DuplicatesState

    data class Done(override val groups: List<DuplicateGroup>) : DuplicatesState
}

/**
 * Finds files with identical contents. Only files of the same size can match, so most files
 * are never opened. Candidates are first compared by their first and last 16 KiB, and only
 * those that still match are read in full and compared by SHA-256.
 *
 * Files are opened read-only. Digests are kept in memory only, keyed by path, size and
 * modification time, so a check after a rescan reads only new or changed files.
 */
class DuplicateFinder {
    private data class Key(val path: String, val size: Long, val modified: Long)

    private data class Digest(val a: Long, val b: Long, val c: Long, val d: Long)

    private val partials = ConcurrentHashMap<Key, Digest>()
    private val fulls = ConcurrentHashMap<Key, Digest>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(2)

    /**
     * Duplicate groups below [root], largest waste first. [onProgress] gets the bytes read in
     * full so far and the total to read, from any thread.
     */
    suspend fun find(root: StorageNode, onProgress: (checked: Long, total: Long) -> Unit = { _, _ -> }): List<DuplicateGroup> {
        val bySize = HashMap<Long, MutableList<StorageNode>>()
        val stack = ArrayDeque<StorageNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            for (child in stack.removeLast().children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (child.size >= MIN_SIZE) {
                    bySize.getOrPut(child.size) { ArrayList(2) } += child
                }
            }
        }
        val sameSize = bySize.values.filter { it.size > 1 }

        // Head and tail first: cheap, and enough to tell apart almost all files that merely share a size.
        val partialMatches = digestAll(sameSize.flatten(), partials, ::partialDigest)
            .let { digests -> sameSize.flatMap { group -> matching(group, digests) } }

        val candidates = partialMatches.flatten()
        val total = candidates.filter { fulls[keyOf(it)] == null }.sumOf { it.size }
        val checked = AtomicLong()
        onProgress(0L, total)
        val fullDigests = digestAll(candidates, fulls) { path, _ ->
            fullDigest(path) { onProgress(checked.addAndGet(it), total) }
        }

        return partialMatches
            .flatMap { group -> matching(group, fullDigests) }
            .map { files -> DuplicateGroup(files[0].size, files.sortedWith(compareBy<StorageNode> { it.lastModified }.thenBy { it.path })) }
            .sortedWith(compareByDescending<DuplicateGroup> { it.extraBytes }.thenBy { it.files[0].name })
    }

    /** Splits [group] by digest; files without one (gone or unreadable) are dropped. */
    private fun matching(group: List<StorageNode>, digests: Map<StorageNode, Digest>): List<List<StorageNode>> =
        group.filter { it in digests }.groupBy { digests.getValue(it) }.values.filter { it.size > 1 }

    private suspend fun digestAll(
        files: List<StorageNode>,
        cache: ConcurrentHashMap<Key, Digest>,
        compute: suspend (path: String, size: Long) -> Digest?,
    ): Map<StorageNode, Digest> = withContext(io) {
        coroutineScope {
            files.map { file ->
                async {
                    val key = keyOf(file)
                    val digest = cache[key] ?: compute(key.path, file.size)?.also { cache[key] = it }
                    digest?.let { file to it }
                }
            }.awaitAll()
        }.filterNotNull().toMap(HashMap(files.size))
    }

    private fun keyOf(file: StorageNode) = Key(file.path, file.size, file.lastModified)

    private suspend fun partialDigest(path: String, size: Long): Digest? = digestOf(path) { file, md, buffer ->
        val head = min(size, CHUNK.toLong())
        readRange(file, 0L, head, md, buffer)
        val tailStart = max(head, size - CHUNK)
        if (tailStart < size) readRange(file, tailStart, size - tailStart, md, buffer)
    }

    private suspend fun fullDigest(path: String, onBytes: (Long) -> Unit): Digest? = digestOf(path) { file, md, buffer ->
        while (true) {
            coroutineContext.ensureActive()
            val read = file.read(buffer)
            if (read < 0) break
            md.update(buffer, 0, read)
            onBytes(read.toLong())
        }
    }

    private suspend fun digestOf(path: String, read: suspend (RandomAccessFile, MessageDigest, ByteArray) -> Unit): Digest? {
        val md = MessageDigest.getInstance("SHA-256")
        try {
            RandomAccessFile(path, "r").use { file -> read(file, md, ByteArray(BUFFER)) }
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        val bytes = ByteBuffer.wrap(md.digest())
        return Digest(bytes.getLong(), bytes.getLong(), bytes.getLong(), bytes.getLong())
    }

    private suspend fun readRange(file: RandomAccessFile, start: Long, length: Long, md: MessageDigest, buffer: ByteArray) {
        file.seek(start)
        var left = length
        while (left > 0) {
            coroutineContext.ensureActive()
            val read = file.read(buffer, 0, min(left, buffer.size.toLong()).toInt())
            if (read < 0) break
            md.update(buffer, 0, read)
            left -= read
        }
    }

    companion object {
        /** Smaller files waste little space and would cost many reads through FUSE. */
        const val MIN_SIZE = 100_000L

        private const val CHUNK = 16 * 1024
        private const val BUFFER = 256 * 1024
    }
}
