package com.nendo.argosy.ui.screens.gamedetail.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSpreadTest {

    @Test
    fun `the cover stands alone and later pages pair even with odd`() {
        assertEquals(0..0, spreadOf(0, 10))
        assertEquals(1..2, spreadOf(1, 10))
        assertEquals(1..2, spreadOf(2, 10))
        assertEquals(3..4, spreadOf(4, 10))
    }

    @Test
    fun `a last page with no partner stands alone`() {
        assertEquals(9..9, spreadOf(9, 10))
        assertEquals(1..1, spreadOf(1, 2))
    }

    @Test
    fun `turning walks the book one spread at a time`() {
        assertEquals(1, spreadStartAfter(0, 10, 1))
        assertEquals(3, spreadStartAfter(1, 10, 1))
        assertEquals(3, spreadStartAfter(2, 10, 1))
        assertEquals(1, spreadStartAfter(3, 10, -1))
        assertEquals(0, spreadStartAfter(1, 10, -1))
    }

    @Test
    fun `turning stops at both covers`() {
        assertEquals(0, spreadStartAfter(0, 10, -1))
        assertEquals(9, spreadStartAfter(9, 10, 1))
    }
}
