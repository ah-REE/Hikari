package com.hikari.app

import org.junit.Assert.*
import org.junit.Test

class HikariNavigationTest {
    @Test fun collectionBackReturnsToLibrary() {
        val state = HikariNavigation().selectRoot("library").open(HikariRoute("collection", "history", "History"))
        assertEquals(HikariRoute("library"), state.back().current)
        assertEquals(HikariRoute("home"), state.back().back().current)
    }

    @Test fun switchingRootsDoesNotBuildABackLoop() {
        val state = HikariNavigation().selectRoot("following").selectRoot("library").selectRoot("following")
        assertEquals("home", state.back().current.page)
        assertFalse(state.back().canGoBack)
    }

    @Test fun channelBackRestoresTheSearchOrigin() {
        val search = HikariNavigation().selectRoot("following").open(HikariRoute("search"))
        val channel = search.open(HikariRoute("channel", "https://www.youtube.com/@creator", "Creator"))
        assertEquals(search, channel.back())
        assertEquals("following", channel.back().back().current.page)
    }

    @Test fun reopeningTheCurrentDestinationDoesNotDuplicateIt() {
        val playlist = HikariRoute("playlist", "id-1", "Weekend films")
        val state = HikariNavigation().selectRoot("library").open(playlist)
        assertEquals(state, state.open(playlist))
        assertEquals("library", state.open(playlist).back().current.page)
    }

    @Test fun openingAChildAssignsAnEntryIdWithoutChangingTheDestination() {
        val route = HikariRoute("collection", "history", "History")
        val state = HikariNavigation().open(route)
        assertFalse(state.current.entryId.isEmpty())
        assertEquals(route, state.current.copy(entryId = ""))
        assertEquals("", route.entryId)
    }

    @Test fun reopeningSettingsAfterBackUsesAFreshKey() {
        val library = HikariNavigation().selectRoot("library")
        val firstVisit = library.open(HikariRoute("settings"))
        val secondVisit = firstVisit.back().open(HikariRoute("settings"))
        assertNotEquals(firstVisit.current.key, secondVisit.current.key)
        assertEquals(library, secondVisit.back())
    }

    @Test fun reopeningCollectionsAndTheSameUrlUsesFreshKeys() {
        val library = HikariNavigation().selectRoot("library")
        val routes = listOf(
            HikariRoute("collection", "history", "History"),
            HikariRoute("collection", "saved", "Watch later"),
            HikariRoute("playlist", "id-1", "Weekend films"),
            HikariRoute("remote_playlist", "https://www.youtube.com/playlist?list=one", "Playlist"),
            HikariRoute("channel", "https://www.youtube.com/@creator", "Creator"),
            HikariRoute("downloads")
        )
        routes.forEach { route ->
            val firstVisit = library.open(route)
            val secondVisit = firstVisit.back().open(firstVisit.current)
            assertNotEquals(firstVisit.current.entryId, secondVisit.current.entryId)
            assertNotEquals(firstVisit.current.key, secondVisit.current.key)
            assertEquals(library, secondVisit.back())
        }
    }

    @Test fun nestedRepeatedDestinationsHaveDistinctKeysAndBackPreservesAncestors() {
        val ancestor = HikariNavigation().selectRoot("following")
            .open(HikariRoute("channel", "https://www.youtube.com/@creator", "Creator"))
        val playlist = ancestor.open(HikariRoute("remote_playlist", "playlist-url", "Playlist"))
        val repeated = playlist.open(ancestor.current)
        assertEquals(3, repeated.children.map { it.key }.toSet().size)
        assertNotEquals(ancestor.current.key, repeated.current.key)
        assertEquals(playlist, repeated.back())
        assertEquals(ancestor, repeated.back().back())
        assertEquals(ancestor.current.key, repeated.back().back().current.key)
    }

    @Test fun currentDestinationDedupIgnoresEntryIdAndTitle() {
        val state = HikariNavigation().open(HikariRoute("playlist", "id-1", "Weekend films"))
        val request = state.current.copy(entryId = "another-entry", title = "Renamed playlist")
        assertSame(state, state.open(request))
        assertEquals(1, state.children.size)
        assertEquals(2, state.open(request.copy(id = "id-2")).children.size)
    }

    @Test fun rootsKeepDefaultIdentityAndEquality() {
        listOf("home", "following", "library").forEach { root ->
            val state = HikariNavigation().selectRoot(root)
            assertEquals(HikariRoute(root), state.current)
            assertEquals("$root:", state.current.key)
            assertSame(state, state.open(HikariRoute(root, entryId = "ignored")))
            assertEquals(state, state.open(HikariRoute("settings")).back())
        }
        assertEquals(HikariNavigation(), HikariNavigation().back())
    }

    @Test fun serializationRoundTripPreservesEntryIdsKeysAndBackStack() {
        listOf("home", "following", "library").forEach { root ->
            val ancestor = HikariNavigation().selectRoot(root).open(HikariRoute("search"))
            val state = ancestor
                .open(HikariRoute("channel", "https://www.youtube.com/@creator", "Creator"))
                .open(HikariRoute("search"))
            val restored = HikariNavigation.decode(state.encode())
            assertEquals(state, restored)
            assertEquals(state.children.map { it.key }, restored.children.map { it.key })
            assertEquals(ancestor, restored.back().back())
            assertEquals(restored, HikariNavigation.decode(restored.encode()))
        }
    }

    @Test fun legacyRootAndTriplesRestoreWithDistinctStableEntryIds() {
        val restored = HikariNavigation.decode(
            listOf(
                "following",
                "search", "", "",
                "channel", "https://www.youtube.com/@creator", "Creator",
                "search", "", ""
            )
        )
        assertEquals("following", restored.root)
        assertEquals(listOf("search", "channel", "search"), restored.children.map { it.page })
        assertEquals("https://www.youtube.com/@creator", restored.children[1].id)
        assertEquals("Creator", restored.children[1].title)
        assertTrue(restored.children.all { it.entryId.isNotEmpty() })
        assertEquals(3, restored.children.map { it.key }.toSet().size)
        assertEquals(restored, HikariNavigation.decode(restored.encode()))
        assertEquals(restored.children.first().key, restored.back().back().current.key)
    }

    @Test fun rootOnlyAndIncompleteSavedStateRestoreGracefully() {
        listOf("home", "following", "library").forEach { root ->
            val state = HikariNavigation(root)
            assertEquals(state, HikariNavigation.decode(listOf(root)))
            assertEquals(state, HikariNavigation.decode(state.encode()))
        }
        assertEquals(HikariNavigation(), HikariNavigation.decode(emptyList()))
        assertEquals(HikariNavigation(), HikariNavigation.decode(listOf("unknown-root")))
        assertEquals(HikariNavigation("library"), HikariNavigation.decode(listOf("library", "collection")))
        val complete = HikariNavigation("library").open(HikariRoute("settings"))
        assertEquals(complete, HikariNavigation.decode(complete.encode() + "incomplete-child"))
    }
}
