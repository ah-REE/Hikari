package com.hikari.app

import java.net.URI
import java.net.URLDecoder

object VideoLinks {
    fun isShort(text: String?): Boolean = id(text) != null && runCatching { URI(Regex("https?://[^\\s]+").find(text.orEmpty())!!.value).path.startsWith("/shorts/") }.getOrDefault(false)

    fun id(text: String?): String? = runCatching {
        val link = Regex("https?://[^\\s]+").find(text.orEmpty())?.value ?: return null
        val uri = URI(link)
        val host = uri.host?.lowercase()
        val id = when(host) {
            "youtu.be" -> uri.path.trim('/').substringBefore('/')
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }.firstOrNull { it.first() == "v" }?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/") || uri.path.startsWith("/embed/") -> uri.path.split('/').getOrNull(2)
                else -> null
            }
            else -> null
        }
        id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }.getOrNull()
}
