package com.hikari.app

/** Range replies must identify the exact requested bytes before they can enter the cache. */
internal data class DownloadRange(val start: Long, val end: Long, val total: Long) {
    companion object {
        fun parse(header: String?): DownloadRange? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE).matchEntire(header?.trim().orEmpty()) ?: return null
            val (start, end, total) = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
            return if (start >= 0 && end >= start && total > end) DownloadRange(start, end, total) else null
        }
    }
    fun matches(position: Long, length: Long): Boolean = start == position && (length < 0 || end - start + 1 == length)
}
