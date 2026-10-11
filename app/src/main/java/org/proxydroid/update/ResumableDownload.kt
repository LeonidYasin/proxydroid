package org.proxydroid.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistent private partial bytes; never an installable/verified APK until all checks pass. */
data class DownloadProgress(val bytes: Long, val total: Long, val bytesPerSecond: Long = 0,
                            val attempt: Int = 1, val status: String = "Downloading")

class ResumableDownload(
    private val open: (String, Long) -> HttpURLConnection,
    private val maxAttempts: Int = 5,
    private val backoffMs: Long = 2000,
) {
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        const val MAX_BYTES = 200L * 1024 * 1024
    }
    suspend fun download(url: String, size: Long, sha256: String, partial: File,
                         progress: (DownloadProgress) -> Unit): File {
        require(size in 1..MAX_BYTES && Regex("[0-9a-f]{64}").matches(sha256))
        require(maxAttempts in 1..10)
        val lock = locks.getOrPut(partial.canonicalPath) { Mutex() }
        return lock.withLock {
            check(partial.parentFile!!.mkdirs() || partial.parentFile!!.isDirectory)
            if (partial.exists() && partial.length() > size) check(partial.delete())
            var attempt = 0
            while (partial.length() < size) {
                currentCoroutineContext().ensureActive()
                attempt++
                val offset = partial.length()
                progress(DownloadProgress(offset, size, attempt = attempt,
                    status = if (offset > 0) "Resuming saved download" else "Downloading"))
                try {
                    val connection = open(url, offset)
                    coroutineScope {
                        // Cancellation closes a blocking network read immediately, rather
                        // than waiting for its idle timeout. The per-file mutex prevents
                        // a new Resume from appending alongside an old cancelled writer.
                        val closeOnCancel = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { runCatching { connection.disconnect() } }
                        }
                        try {
                            val code = connection.responseCode
                            if (code in setOf(408, 429, 500, 502, 503, 504)) throw IOException("Temporary HTTP $code")
                            require(code == 200 || code == 206) { "Download returned HTTP $code" }
                            require(connection.getHeaderField("Content-Encoding").let { it == null || it == "identity" }) { "Unexpected download encoding" }
                            val append = code == 206
                            if (append) {
                                val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(connection.getHeaderField("Content-Range") ?: "")
                                    ?: error("Invalid Content-Range; saved bytes retained")
                                require(match.groupValues[1].toLong() == offset && match.groupValues[2].toLong() == size - 1 && match.groupValues[3].toLong() == size) { "Content-Range does not match this APK" }
                            }
                            // A server ignoring Range returns 200. Truncate; never append
                            // a full response to the partial prefix.
                            var total = if (append) offset else 0L
                            val started = System.nanoTime()
                            var lastEvent = 0L
                            val initial = total
                            // FileOutputStream(append) must be used without truncating first.
                            connection.inputStream.use { input -> java.io.FileOutputStream(partial, append).use { output ->
                                val buffer = ByteArray(32768)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= size) { "APK exceeds its declared size" }
                                    output.write(buffer, 0, n)
                                    val now = System.nanoTime()
                                    if (now - lastEvent >= 250_000_000L || total == size) {
                                        val seconds = (now - started).coerceAtLeast(1L) / 1e9
                                        progress(DownloadProgress(total, size, ((total - initial) / seconds).toLong(), attempt))
                                        lastEvent = now
                                    }
                                }
                                output.fd.sync()
                            } }
                            if (total != size) throw IOException("Connection ended before the APK was complete")
                        } finally {
                            closeOnCancel.cancel()
                            runCatching { connection.disconnect() }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: IOException) {
                    currentCoroutineContext().ensureActive()
                    if (attempt >= maxAttempts) throw IOException("Download paused after $maxAttempts attempts. Saved ${partial.length()} / $size bytes; press Resume download. ${failure.javaClass.simpleName}", failure)
                    progress(DownloadProgress(partial.length(), size, attempt = attempt, status = "Connection interrupted; automatic retry"))
                    delay((backoffMs * (1L shl (attempt - 1))).coerceAtMost(30_000))
                }
            }
            progress(DownloadProgress(size, size, attempt = attempt.coerceAtLeast(1), status = "Verifying complete APK"))
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(32768)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val actual = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            if (partial.length() != size || actual != sha256) {
                partial.delete()
                error("Complete APK checksum/size failed. Unsafe partial bytes deleted; download again.")
            }
            partial
        }
    }
}
