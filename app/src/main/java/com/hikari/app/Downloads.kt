package com.hikari.app

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.*
import androidx.media3.exoplayer.offline.*
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject

@UnstableApi
object Downloads {
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    @Synchronized fun manager(context: Context): DownloadManager {
        if (manager == null) {
            val db = StandaloneDatabaseProvider(context.applicationContext)
            cache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), db)
            manager = DownloadManager(context.applicationContext, DefaultDownloadIndex(db), HikariDownloaderFactory(cache!!)).apply { maxParallelDownloads = 2 }
        }
        return manager!!
    }
    fun cacheFactory(context: Context): CacheDataSource.Factory {
        manager(context)
        return CacheDataSource.Factory().setCache(cache!!).setCacheWriteDataSinkFactory(null).setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0"))).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    fun enqueue(context: Context, video: Video, choice: StreamChoice) {
        val data = video.json().put("audio", choice.audio).put("mime", choice.mime).put("quality", choice.label).put("audioTrackId", choice.audioTrackId).put("audioTrackName", choice.audioTrackName).toString().toByteArray()
        val request = DownloadRequest.Builder(video.id, android.net.Uri.parse(choice.url)).setMimeType(choice.mime).setData(data).build()
        DownloadService.sendAddDownload(context, HikariDownloadService::class.java, request, false)
        if (choice.audio == null) DownloadService.sendRemoveDownload(context, HikariDownloadService::class.java, video.id + "-audio", false)
        choice.audio?.let { audio -> DownloadService.sendAddDownload(context, HikariDownloadService::class.java, DownloadRequest.Builder(video.id + "-audio", android.net.Uri.parse(audio)).build(), false) }
    }
    fun all(context: Context): List<Download> {
        val result = mutableListOf<Download>()
        manager(context).downloadIndex.getDownloads().use { cursor -> while (cursor.moveToNext()) result += cursor.download }
        return result
    }
    fun remove(context: Context, id: String) {
        DownloadService.sendRemoveDownload(context, HikariDownloadService::class.java, id, false)
        DownloadService.sendRemoveDownload(context, HikariDownloadService::class.java, "$id-audio", false)
    }
    fun pause(context: Context, id: String, pause: Boolean) {
        listOf(id, "$id-audio").forEach { DownloadService.sendSetStopReason(context, HikariDownloadService::class.java, it, if (pause) 1 else 0, false) }
    }
    fun offline(context: Context, id: String): Pair<Video, StreamChoice>? {
        val all = all(context)
        val download = all.firstOrNull { it.request.id == id && it.state == Download.STATE_COMPLETED } ?: return null
        val metadata = JSONObject(String(download.request.data))
        val audio = metadata.optString("audio").takeIf { it.isNotBlank() && it != "null" }
        if (audio != null && all.none { it.request.id == "$id-audio" && it.state == Download.STATE_COMPLETED }) return null
        return metadata.video() to StreamChoice("Offline", download.request.uri.toString(), audio, download.request.mimeType, metadata.optString("audioTrackId").takeIf { it.isNotBlank() }, metadata.optString("audioTrackName").takeIf { it.isNotBlank() })
    }
}

@UnstableApi
class HikariDownloadService : DownloadService(42, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, "hikari-downloads", R.string.downloads, 0) {
    override fun getDownloadManager() = Downloads.manager(this)
    override fun getScheduler(): Scheduler = PlatformScheduler(this, 43)
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification = DownloadNotificationHelper(this, "hikari-downloads").buildProgressNotification(this, R.drawable.ic_notification, null, "Saving for offline viewing", downloads, notMetRequirements)
}
