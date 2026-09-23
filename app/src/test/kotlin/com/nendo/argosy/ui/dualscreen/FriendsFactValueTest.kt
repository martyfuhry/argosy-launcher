package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendsFactValueTest {

    @Test
    fun `no friends shows no fact`() {
        assertNull(friendsFactValue(emptyList()))
    }

    @Test
    fun `two friends are both named`() {
        val value = friendsFactValue(
            listOf(CompanionFriend("Alex", playingNow = true), CompanionFriend("Sam", playingNow = true))
        )

        assertEquals("Alex, Sam", value)
    }

    @Test
    fun `friends playing now are named before friends who played recently`() {
        val value = friendsFactValue(
            listOf(
                CompanionFriend("Kim", playingNow = false),
                CompanionFriend("Alex", playingNow = true),
                CompanionFriend("Sam", playingNow = false),
                CompanionFriend("Lee", playingNow = true)
            )
        )

        assertEquals("Alex, Lee +2", value)
    }
}
