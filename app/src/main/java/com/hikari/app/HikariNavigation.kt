package com.hikari.app

import java.util.UUID

/** Root switches never accumulate Back entries; child routes return to their origin. */
data class HikariRoute(val page: String, val id: String = "", val title: String = "", val entryId: String = "") {
    val key get() = if (entryId.isEmpty()) "$page:$id" else "$page:$id:$entryId"
}

data class HikariNavigation(val root: String = "home", val children: List<HikariRoute> = emptyList()) {
    val current get() = children.lastOrNull() ?: HikariRoute(root)
    val canGoBack get() = children.isNotEmpty() || root != "home"
    fun selectRoot(page: String) = HikariNavigation(page)
    fun open(route: HikariRoute) =
        if (current.page == route.page && current.id == route.id) this
        else copy(children = children + route.copy(entryId = UUID.randomUUID().toString()))

    fun back() = if (children.isNotEmpty()) copy(children = children.dropLast(1)) else HikariNavigation()

    fun encode(): List<String> = listOf(SAVED_STATE_VERSION, root) + children.flatMap {
        listOf(it.page, it.id, it.title, it.entryId)
    }

    companion object {
        private const val SAVED_STATE_VERSION = "hikari-navigation-v2"

        fun decode(saved: List<String>): HikariNavigation {
            val versioned = saved.firstOrNull() == SAVED_STATE_VERSION
            val rootIndex = if (versioned) 1 else 0
            val root = saved.getOrNull(rootIndex)?.takeIf { it in listOf("home", "following", "library") } ?: "home"
            val fieldCount = if (versioned) 4 else 3
            val children = saved.drop(rootIndex + 1).chunked(fieldCount).filter { it.size == fieldCount }.map {
                HikariRoute(
                    it[0],
                    it[1],
                    it[2],
                    it.getOrNull(3)?.takeIf { id -> id.isNotEmpty() } ?: UUID.randomUUID().toString())
            }
            return HikariNavigation(root, children)
        }
    }
}
