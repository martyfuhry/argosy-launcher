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

    @Test
    fun `an existing row takes the earlier server creation time`() {
        val existing = now.minus(Duration.ofHours(2))

        assertEquals(Instant.parse("2026-08-23T12:00:00Z"), reconcileAddedAt(existing, "2026-08-23T12:00:00Z"))
    }

    @Test
    fun `a later server creation time leaves an existing row alone`() {
        val existing = now.minus(Duration.ofDays(30))

        assertEquals(existing, reconcileAddedAt(existing, "2026-09-22T12:00:00Z"))
    }

    @Test
    fun `an existing row without a server creation time keeps its stamp`() {
        val existing = now.minus(Duration.ofDays(30))

        assertEquals(existing, reconcileAddedAt(existing, null))
    }

    @Test
    fun `an existing row survives an unreadable server creation time`() {
        val existing = now.minus(Duration.ofDays(30))

        assertEquals(existing, reconcileAddedAt(existing, "yesterday"))
    }

    @Test
    fun `a row stamped when its file was adopted returns to the server creation time`() {
        val adoptedAt = Instant.parse("2026-09-22T23:47:32Z")

        assertEquals(Instant.parse("2026-06-03T13:25:25Z"), reconcileAddedAt(adoptedAt, "2026-06-03T13:25:25"))
    }
}
