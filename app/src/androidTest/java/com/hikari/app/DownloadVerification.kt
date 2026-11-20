package com.hikari.app

import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.*
import androidx.media3.exoplayer.offline.*
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Runs the real downloader/cache on Android against a deterministic local range server. No user data is used. */
@UnstableApi
class DownloadVerification : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = StringBuilder()
        try {
            RangeServer().use { server ->
                fun verify(path: String, parallel: Boolean = true): Long = withCache { cache ->
                    val request = DownloadRequest.Builder(path, Uri.parse(server.url(path))).build()
                    val downloader = if (parallel) HikariDownloaderFactory(cache).createDownloader(request) else DefaultDownloaderFactory(CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())).createDownloader(request)
                    val start = System.nanoTime()
                    downloader.download(null)
                    val time = (System.nanoTime() - start) / 1_000_000
                    checkDigest(cache, request, server.bytes)
                    time
                }
                val sequential = verify("baseline", false)
                server.maxActive.set(0)
                val parallel = verify("ranges")
                check(server.maxActive.get() > 1) { "No concurrent requests" }
                report.appendLine("PASS parallel download: exact SHA-256; ${server.maxActive.get()} concurrent requests; ${parallel}ms vs ${sequential}ms sequential (controlled local server)")
                verify("ignored")
                report.appendLine("PASS server ignores Range: sequential fallback has exact SHA-256")
                listOf("wrong", "short").forEach { path -> withCache { cache ->
                    val request = DownloadRequest.Builder(path, Uri.parse(server.url(path))).build()
                    val result = runCatching { HikariDownloaderFactory(cache).createDownloader(request).download(null) }
                    check(result.isFailure) { "$path was accepted" }
                    check(!cache.isCached(request.uri.toString(), 0, server.bytes.size.toLong())) { "Invalid response marked complete" }
                    report.appendLine("PASS $path response: rejected; no complete cache entry")
                } }
                withCache { cache ->
                    val request = DownloadRequest.Builder("resume", Uri.parse(server.url("slow"))).build()
                    val downloader = HikariDownloaderFactory(cache).createDownloader(request)
                    val worker = thread { runCatching { downloader.download(null) } }
                    val deadline = System.nanoTime() + 10_000_000_000L
                    while (cache.getCachedBytes(request.uri.toString(), 0, server.bytes.size.toLong()) < 1024 * 1024 && System.nanoTime() < deadline) Thread.sleep(10)
                    downloader.cancel(); worker.interrupt(); worker.join(5000)
                    check(!worker.isAlive) { "Cancellation did not finish" }
                    val retained = cache.getCachedBytes(request.uri.toString(), 0, server.bytes.size.toLong())
                    check(retained in 1 until server.bytes.size.toLong()) { "No partial download to resume: $retained" }
                    HikariDownloaderFactory(cache).createDownloader(request).download(null)
                    checkDigest(cache, request, server.bytes)
                    report.appendLine("PASS cancel/resume: retained $retained bytes, resumed to exact SHA-256")
                    HikariDownloaderFactory(cache).createDownloader(request).remove()
                    check(cache.getCachedBytes(request.uri.toString(), 0, server.bytes.size.toLong()) == 0L)
                    report.appendLine("PASS remove: cache resource deleted")
                }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString() + failure.stackTraceToString()) }) }
    }
    @Suppress("DEPRECATION")
    private fun <T> withCache(block: (SimpleCache) -> T): T {
        val directory = File(targetContext.cacheDir, "range-verification-${System.nanoTime()}")
        val cache = SimpleCache(directory, NoOpCacheEvictor())
        return try { block(cache) } finally { cache.release(); directory.deleteRecursively() }
    }
    private fun checkDigest(cache: Cache, request: DownloadRequest, expected: ByteArray) {
        val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null).createDataSource()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            source.open(DataSpec(request.uri))
            val buffer = ByteArray(65536)
            while (true) { val read = source.read(buffer, 0, buffer.size); if (read < 0) break; digest.update(buffer, 0, read); size += read }
        } finally { source.close() }
        check(size == expected.size.toLong()) { "Wrong cached length: $size" }
        check(digest.digest().contentEquals(MessageDigest.getInstance("SHA-256").digest(expected))) { "Cached bytes differ" }
    }
}

private class RangeServer : AutoCloseable {
    val bytes = ByteArray(12 * 1024 * 1024 + 137) { (it * 31 + it / 7919).toByte() }
    private val server = ServerSocket(0)
    private val executor = Executors.newCachedThreadPool()
    private val active = AtomicInteger()
    val maxActive = AtomicInteger()
    @Volatile private var closed = false
    init { thread(isDaemon = true) { while (!closed) runCatching { val socket = server.accept(); executor.execute { socket.use {
        val count = active.incrementAndGet(); maxActive.updateAndGet { maxOf(it, count) }
        try {
            val reader = socket.getInputStream().bufferedReader()
            val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
            var requested: String? = null
            while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Range:", true)) requested = line.substringAfter(':').trim() }
            val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(requested.orEmpty())
            val start = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val end = (match?.groupValues?.get(2)?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
            val isRange = match != null && path != "/ignored"
            val from = if (isRange) start else 0
            val to = if (isRange) end else bytes.lastIndex
            val length = to - from + 1
            val probe = length == 1
            val wrong = path == "/wrong" && !probe
            val header = "HTTP/1.1 ${if (isRange) "206 Partial Content" else "200 OK"}\r\nContent-Length: $length\r\n" +
                (if (isRange) "Content-Range: bytes ${if (wrong) 0 else from}-${if (wrong) length - 1 else to}/${bytes.size}\r\n" else "") + "Connection: close\r\n\r\n"
            val out = socket.getOutputStream(); out.write(header.toByteArray())
            var position = from
            val limit = if (path == "/short" && !probe) from + length / 2 else to + 1
            while (position < limit) { val size = minOf(65536, limit - position); out.write(bytes, position, size); out.flush(); position += size; if (!probe) Thread.sleep(if (path == "/slow") 14 else 4) }
        } catch (_: Exception) { } finally { active.decrementAndGet() }
    } } } } }
    fun url(path: String) = "http://127.0.0.1:${server.localPort}/$path"
    override fun close() { closed = true; server.close(); executor.shutdownNow() }
}
