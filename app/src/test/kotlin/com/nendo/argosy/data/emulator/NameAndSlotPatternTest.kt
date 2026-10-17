package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.io.path.createTempDirectory

class NameAndSlotPatternTest {

    private val pattern = StateSlotPattern.NameAndSlot(separator = "_", extension = "state")

    @Test
    fun `a rom whose name ends another rom's name does not claim its states`() {
        assertNull(pattern.parseSlotNumber("Dr. Mario_1.state", "Mario"))
    }

    @Test
    fun `a trailing number after the slot is not read as that slot`() {
        assertNull(pattern.parseSlotNumber("Game_2_3.state", "Game"))
        assertEquals(3, pattern.parseSlotNumber("Game_2_3.state", "Game_2"))
    }

    @Test
    fun `a copied state with text after the slot is not a slot`() {
        assertNull(pattern.parseSlotNumber("Rom_1 (copy).state", "Rom"))
    }

    @Test
    fun `a no-intro name with brackets and punctuation parses its slot`() {
        val rom = "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"

        assertEquals(1, pattern.parseSlotNumber("${rom}_1.state", rom))
        assertEquals(1, pattern.parseSlotNumber("${rom.uppercase()}_1.STATE", rom))
    }

    @Test
    fun `every registered name-and-slot emulator reads back the files it writes`() {
        val rom = "Mr. Driller 2 (USA)"
        val ids = listOf(
            "drastic", "melonds", "melondualds",
            "pizza_boy_gba", "pizza_boy_gb", "pizza_boy_gba_pro", "pizza_boy_gb_pro"
        )

        for (id in ids) {
            val slotPattern = requireNotNull(StatePathRegistry.getConfig(id)).slotPattern
            for (slot in listOf(0, 1, 7)) {
                assertEquals(id, slot, slotPattern.parseSlotNumber(slotPattern.buildFileName(rom, slot), rom))
            }
            assertNull(id, slotPattern.parseSlotNumber(slotPattern.buildFileName("Dr. $rom", 1), rom))
        }
    }

    @Test
    fun `discovery keeps a near-name rom's states out of the list`() {
        val dir = createTempDirectory("name_and_slot").toFile()
        try {
            listOf("Mario_0.state", "Mario_2.state", "Dr. Mario_1.state", "Mario_2_3.state", "Mario.sav")
                .forEach { java.io.File(dir, it).writeBytes(byteArrayOf(1)) }

            val found = StatePathRegistry.discoverStates(dir, "Mario", pattern)

            assertEquals(listOf("Mario_0.state" to 0, "Mario_2.state" to 2), found.map { it.first.name to it.second })
        } finally {
            dir.deleteRecursively()
        }
    }
}
