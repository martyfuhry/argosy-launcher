package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RomMAddedAtTest {

    private val now = Instant.parse("2026-09-22T12:00:00Z")

    @Test
    fun `a rom created a month ago was added a month ago`() {
        val created = now.minus(Duration.ofDays(30))

        assertEquals(created, resolveAddedAt("2026-08-23T12:00:00Z", now))
    }

    @Test
    fun `a naive server timestamp is read as utc`() {
        assertEquals(Instant.parse("2026-08-23T12:00:00Z"), resolveAddedAt("2026-08-23T12:00:00", now))
    }

    @Test
    fun `a rom without a creation time was added now`() {
        assertEquals(now, resolveAddedAt(null, now))
    }

    @Test
    fun `an unreadable creation time falls back to now`() {
        assertEquals(now, resolveAddedAt("yesterday", now))
    }
}
