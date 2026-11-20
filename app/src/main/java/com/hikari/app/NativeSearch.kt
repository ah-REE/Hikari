package com.hikari.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class SearchPage(val videos: List<Video>, val next: Page?)
object NativeSearch {
    suspend fun search(query: String, next: Page? = null, includeShortsShelves: Boolean = false): SearchPage = withContext(Dispatchers.IO) {
        Extractor.init()
        val service = NewPipe.getService(0)
        val handler = service.searchQHFactory.fromQuery(query, listOf(if (includeShortsShelves) "all" else "videos"), "")
        val (items, page) = if (next == null) {
            val info = SearchInfo.getInfo(service, handler)
            info.relatedItems to info.nextPage
        } else {
            val info = SearchInfo.getMoreItems(service, handler, next)
            info.items to info.nextPage
        }
        SearchPage(items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            val id = VideoLinks.id(item.url) ?: return@mapNotNull null
            val seconds = item.duration
            val duration = if (seconds < 0) "LIVE" else if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
            Video(id, item.name, item.uploaderName, duration, if (item.viewCount < 0) "" else "%,d views".format(item.viewCount), item.uploaderUrl, item.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(), isShort = item.isShortFormContent)
        }.distinctBy { it.id }, page?.takeIf { Page.isValid(it) })
    }
}
