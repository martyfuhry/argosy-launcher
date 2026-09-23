package com.nendo.argosy.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendActivityIndexTest {

    private fun friend(
        id: String,
        presence: PresenceStatus?,
        igdbId: Int?,
        status: String = "accepted",
        title: String = "Some Game"
    ) = Friend(
        id = id,
        username = id,
        displayName = id.replaceFirstChar { it.uppercase() },
        avatarColor = "#336699",
        status = status,
        presence = presence,
        currentGame = PresenceGameInfo(title = title, igdbId = igdbId)
    )

    @Test
    fun `friends in the same game are grouped under its id`() {
        val index = liveFriendActivity(
            listOf(
                friend("alex", PresenceStatus.IN_GAME, igdbId = 1020),
                friend("sam", PresenceStatus.IN_GAME, igdbId = 1020),
                friend("kim", PresenceStatus.IN_GAME, igdbId = 77)
            )
        )

        assertEquals(listOf("alex", "sam"), index[1020]?.map { it.friendId })
        assertEquals(listOf("kim"), index[77]?.map { it.friendId })
        assertTrue(index.values.flatten().all { it.playingNow })
    }

    @Test
    fun `a friend not in a game is left out even with a game on record`() {
        val index = liveFriendActivity(
            listOf(
                friend("alex", PresenceStatus.ONLINE, igdbId = 1020),
                friend("sam", PresenceStatus.OFFLINE, igdbId = 1020),
                friend("kim", PresenceStatus.WATCHING, igdbId = 1020)
            )
        )

        assertTrue(index.isEmpty())
    }

    @Test
    fun `a game without an igdb id matches nothing even when the title does`() {
        val index = liveFriendActivity(
            listOf(friend("alex", PresenceStatus.IN_GAME, igdbId = null, title = "Chrono Trigger"))
        )

        assertTrue(index.isEmpty())
    }

    @Test
    fun `pending requests are not friends yet`() {
        val index = liveFriendActivity(
            listOf(friend("alex", PresenceStatus.IN_GAME, igdbId = 1020, status = "pending"))
        )

        assertTrue(index.isEmpty())
    }
}
