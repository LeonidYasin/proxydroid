package org.proxydroid.update

import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ResumableDownloadTest {
    private val bytes = "0123456789abcdefghij".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private val url = "https://github.com/fixture.apk"
    private class Response(val code: Int, val stream: InputStream, val range: String? = null,
                           val disconnected: () -> Unit = {}) : HttpURLConnection(URL("https://github.com/fixture.apk")) {
        override fun connect() {}
        override fun disconnect() { disconnected() }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = if (name == "Content-Range") range else null
        override fun getInputStream() = stream
    }
    private fun run(test: suspend CoroutineScope.(File) -> Unit) = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("resume-test").toFile()
        try { withTimeout(5000) { test(File(dir, "partial.apk")) } } finally { dir.deleteRecursively() }
    }
    @Test fun resumesSavedPrefixWithStrict206Range() = run { file ->
        file.writeBytes(bytes.copyOfRange(0, 7))
        val manager = ResumableDownload({ _, offset ->
            assertEquals(7L, offset)
            Response(206, bytes.copyOfRange(7, bytes.size).inputStream(), "bytes 7-19/20")
        }, backoffMs = 0)
        manager.download(url, bytes.size.toLong(), hash, file) {}
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun rangeIgnored200RestartsInsteadOfAppending() = run { file ->
        file.writeBytes(bytes.copyOfRange(0, 7))
        ResumableDownload({ _, _ -> Response(200, bytes.inputStream()) }, backoffMs = 0)
            .download(url, 20, hash, file) {}
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun interruptedConnectionRetriesOnlyMissingBytes() = run { file ->
        var requests = 0
        val progress = mutableListOf<DownloadProgress>()
        ResumableDownload({ _, offset ->
            requests++
            if (requests == 1) Response(200, bytes.copyOfRange(0, 5).inputStream())
            else { assertEquals(5L, offset); Response(206, bytes.copyOfRange(5, 20).inputStream(), "bytes 5-19/20") }
        }, backoffMs = 0).download(url, 20, hash, file) { progress.add(it) }
        assertEquals(2, requests); assertArrayEquals(bytes, file.readBytes())
        assertTrue(progress.any { it.attempt == 2 && it.bytes == 5L })
    }
    @Test fun invalidRangeNeverAppendsToSavedFile() = run { file ->
        val prefix = bytes.copyOfRange(0, 5); file.writeBytes(prefix)
        try {
            ResumableDownload({ _, _ -> Response(206, bytes.inputStream(), "bytes 0-19/20") })
                .download(url, 20, hash, file) {}
            fail("Invalid range accepted")
        } catch (_: IllegalArgumentException) { assertArrayEquals(prefix, file.readBytes()) }
    }
    @Test fun fullBadChecksumDeletesUnsafeBytes() = run { file ->
        file.writeBytes(ByteArray(20))
        try {
            ResumableDownload({ _, _ -> error("Network should not be used") }).download(url, 20, hash, file) {}
            fail("Bad checksum accepted")
        } catch (_: IllegalStateException) { assertFalse(file.exists()) }
    }
    @Test fun completeGoodPartialDoesNotDownloadAgain() = run { file ->
        file.writeBytes(bytes)
        ResumableDownload({ _, _ -> error("Network should not be used") }).download(url, 20, hash, file) {}
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun exhaustingRetriesRetainsPartialForNextManager() = run { file ->
        try {
            ResumableDownload({ _, offset ->
                if (offset == 0L) Response(200, bytes.copyOfRange(0, 5).inputStream()) else throw IOException("offline")
            }, maxAttempts = 2, backoffMs = 0).download(url, 20, hash, file) {}
            fail("Offline download completed")
        } catch (_: IOException) { assertEquals(5L, file.length()) }
        ResumableDownload({ _, offset -> assertEquals(5L, offset); Response(206, bytes.copyOfRange(5, 20).inputStream(), "bytes 5-19/20") })
            .download(url, 20, hash, file) {}
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun pauseClosesBlockedReadAndRetainsWrittenPrefix() = run { file ->
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val blocked = object : InputStream() {
            var first = true
            override fun read(): Int = error("Bulk read expected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; bytes.copyInto(b, off, 0, 5); return 5 }
                entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); throw IOException("closed")
            }
        }
        val job = launch(Dispatchers.IO) {
            ResumableDownload({ _, _ -> Response(200, blocked, disconnected = { release.countDown() }) })
                .download(url, 20, hash, file) {}
        }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertEquals(5L, file.length())
    }
}
