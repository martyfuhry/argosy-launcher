package com.nendo.argosy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the condition under which the RomM session token may ride along with a request. A
 * server controls the media urls it hands out, so a url that names any other host, or that
 * cannot be read as a url at all, must never match the configured address.
 */
class UrlHostsTest {

    @Test
    fun `matches the same host on the same explicit port`() {
        assertTrue(isSameHost("http://romm.local:8080/api/roms/1", "http://romm.local:8080"))
        assertTrue(isSameHost("https://romm.example.com/assets/cover.png", "https://romm.example.com"))
    }

    @Test
    fun `reads an omitted port as the scheme default`() {
        assertTrue(isSameHost("https://romm.example.com:443/cover.png", "https://romm.example.com"))
        assertTrue(isSameHost("http://romm.local/cover.png", "http://romm.local:80"))
    }

    @Test
    fun `rejects a different port on the same host`() {
        assertFalse(isSameHost("http://romm.local:9090/cover.png", "http://romm.local:8080"))
        assertFalse(isSameHost("https://romm.example.com/cover.png", "http://romm.example.com"))
    }

    @Test
    fun `rejects another host`() {
        assertFalse(isSameHost("http://attacker.test:8080/cover.png", "http://romm.local:8080"))
        assertFalse(isSameHost("http://romm.local.attacker.test/cover.png", "http://romm.local"))
        assertFalse(isSameHost("http://user@attacker.test/cover.png", "http://romm.local"))
    }

    @Test
    fun `rejects a url it cannot read`() {
        assertFalse(isSameHost("not a url", "http://romm.local:8080"))
        assertFalse(isSameHost("http://", "http://romm.local:8080"))
        assertFalse(isSameHost("/api/roms/1", "http://romm.local:8080"))
        assertFalse(isSameHost("http://romm.local/cover.png", ""))
    }
}
