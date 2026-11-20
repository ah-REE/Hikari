package com.hikari.app

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import androidx.media3.exoplayer.offline.*
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/** The legacy app's 1 MB ranges, with Media3's persistent cache, queue and resume support. */
@UnstableApi
internal class HikariDownloaderFactory(private val cache: Cache) : DownloaderFactory {
    private val http = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setConnectTimeoutMs(15000).setReadTimeoutMs(15000)
    private val workers = Executors.newFixedThreadPool(6)
    private val cacheFactory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
    private val fallback = DefaultDownloaderFactory(cacheFactory, workers)
    override fun createDownloader(request: DownloadRequest): Downloader {
        val regular = fallback.createDownloader(request)
        return if (Util.inferContentTypeForUriAndMimeType(request.uri, request.mimeType) == C.CONTENT_TYPE_OTHER && request.uri.scheme in listOf("http", "https"))
            ParallelDownloader(request, cache, http, workers, regular) else regular
    }
}

@UnstableApi
internal class ParallelDownloader(
    private val request: DownloadRequest,
    private val cache: Cache,
    private val http: HttpDataSource.Factory,
    private val workers: ExecutorService,
    private val fallback: Downloader
) : Downloader {
    private val key = request.customCacheKey ?: request.uri.toString()
    private val chunk = 1024L * 1024
    @Volatile private var cancelled = false
    private val writers = ConcurrentHashMap.newKeySet<CacheWriter>()
    private val sources = ConcurrentHashMap.newKeySet<CheckedRangeSource>()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    override fun download(listener: Downloader.ProgressListener?) {
        if (cancelled) throw InterruptedException()
        val probe = http.createDataSource()
        val range = try {
            probe.open(DataSpec.Builder().setUri(request.uri).setLength(1).build())
            if (probe.responseCode == 206) DownloadRange.parse(probe.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull())?.takeIf { it.matches(0, 1) } else null
        } finally { probe.close() }
        // Unknown length, small files, and servers without byte ranges retain the proven sequential path.
        if (range == null || range.total < chunk * 2) { if (cancelled) throw InterruptedException(); fallback.download(listener); return }
        val total = range.total
        cache.applyContentMetadataMutations(key, ContentMetadataMutations.setContentLength(ContentMetadataMutations(), total))
        val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory { CheckedRangeSource(client).also { sources += it } }
        val cursor = AtomicLong(0)
        val inflight = ConcurrentHashMap<Long, Long>()
        var lastReport = 0L
        val reportLock = Any()
        fun report(force: Boolean = false) = synchronized(reportLock) {
            val now = System.nanoTime()
            if (force || now - lastReport > 100_000_000L) {
                lastReport = now
                val uncommitted = inflight.entries.sumOf { (start, loaded) -> (loaded - cache.getCachedBytes(key, start, minOf(chunk, total - start))).coerceAtLeast(0L) }
                val bytes = (cache.getCachedBytes(key, 0, total) + uncommitted).coerceAtMost(total)
                listener?.onProgress(total, bytes, bytes * 100f / total)
            }
        }
        report(true)
        val completed = ExecutorCompletionService<Unit>(workers)
        val stopped = CountDownLatch(6)
        val abort = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            repeat(6) {
                completed.submit(Callable {
                    try {
                    while (!cancelled && !abort.get()) {
                        val start = cursor.getAndAdd(chunk)
                        if (start >= total) break
                        val length = minOf(chunk, total - start)
                        if (cache.isCached(key, start, length)) continue
                        val spec = DataSpec.Builder().setUri(request.uri).setKey(key).setPosition(start).setLength(length).build()
                        val writer = CacheWriter(factory.createDataSourceForDownloading(), spec, ByteArray(64 * 1024)) { _, cachedBytes, _ ->
                            inflight[start] = cachedBytes; report()
                        }
                        writers += writer
                        try {
                            if (cancelled) throw InterruptedException()
                            writer.cache()
                            if (!cache.isCached(key, start, length)) throw EOFException("Incomplete video range")
                        } finally { writers -= writer; inflight.remove(start) }
                        report()
                    }
                    } finally { stopped.countDown() }
                })
            }
            repeat(6) { completed.take().get() }
            if (cancelled) throw InterruptedException()
            if (!cache.isCached(key, 0, total)) throw EOFException("Incomplete download")
            report(true)
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is InterruptedException || cancelled) throw InterruptedException()
            throw (cause as? IOException ?: IOException("Download range failed", cause))
        } finally {
            abort.set(true)
            writers.forEach { it.cancel() }
            sources.forEach { it.cancel() }
            // Do not return to Media3 until workers release their cache spans (pause/remove must be safe).
            var interrupted = false
            while (stopped.count > 0) try { stopped.await() } catch (_: InterruptedException) { interrupted = true }
            sources.clear()
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    override fun cancel() { cancelled = true; fallback.cancel(); writers.forEach { it.cancel() }; sources.forEach { it.cancel() } }
    override fun remove() { cache.removeResource(key) }
}

/** Reject ignored/mismatched ranges and premature EOF, instead of caching the wrong segment. */
@UnstableApi
private class CheckedRangeSource(private val client: OkHttpClient) : DataSource {
    @Volatile private var call: Call? = null
    @Volatile private var cancelled = false
    private var response: Response? = null
    private var input: InputStream? = null
    private var remaining = 0L
    private var spec: DataSpec? = null
    private val listeners = mutableListOf<TransferListener>()
    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }
    override fun getUri() = spec?.uri
    override fun getResponseHeaders() = response?.headers?.toMultimap().orEmpty()
    override fun open(dataSpec: DataSpec): Long {
        if (cancelled) throw IOException("Download cancelled")
        spec = dataSpec
        listeners.forEach { it.onTransferInitializing(this, dataSpec, true) }
        val end = if (dataSpec.length > 0) (dataSpec.position + dataSpec.length - 1).toString() else ""
        val request = Request.Builder().url(dataSpec.uri.toString()).header("User-Agent", "Mozilla/5.0").header("Accept-Encoding", "identity").header("Range", "bytes=${dataSpec.position}-$end").build()
        val pending = client.newCall(request)
        call = pending
        if (cancelled) pending.cancel()
        val result = pending.execute()
        response = result
        val range = DownloadRange.parse(result.header("Content-Range"))
        if (result.code != 206 || range?.matches(dataSpec.position, dataSpec.length) != true) {
            result.close(); throw IOException("Server returned an unexpected video range")
        }
        remaining = range.end - range.start + 1
        input = result.body?.byteStream() ?: throw IOException("Empty video response")
        listeners.forEach { it.onTransferStart(this, dataSpec, true) }
        return remaining
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = requireNotNull(input).read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) throw EOFException("Video range ended early")
        remaining -= count
        spec?.let { dataSpec -> listeners.forEach { it.onBytesTransferred(this, dataSpec, true, count) } }
        return count
    }
    fun cancel() { cancelled = true; call?.cancel() }
    override fun close() {
        response?.close(); response = null; input = null; call = null
        spec?.let { dataSpec -> listeners.forEach { it.onTransferEnd(this, dataSpec, true) } }
        spec = null
    }
}
