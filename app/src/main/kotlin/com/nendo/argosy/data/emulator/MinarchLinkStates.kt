package com.nendo.argosy.data.emulator

import java.io.File

data class MinarchLinkState(
    val slotNumber: Int,
    val savedAtMillis: Long,
    val previewFile: File?
)

/**
 * Minarch Link's menu save states for one rom, read from its own folders. Layout per the Thor
 * build's minarch.c: states at `MinarchLink/States/GBA/<rom without extension>.state[N]` for menu
 * slots 0 to 7, and a BMP preview per slot at
 * `MinarchLink/.userdata/shared/.minui/GBA/<rom file name>.<slot>.bmp`.
 */
object MinarchLinkStates {
    const val EMULATOR_ID = "minarch_link"
    const val MENU_SLOT_COUNT = 8
    private const val STATES_DIR = "MinarchLink/States/GBA"
    private const val PREVIEW_DIR = "MinarchLink/.userdata/shared/.minui/GBA"
    private const val PREVIEW_EXTENSION = "bmp"

    fun isMinarchLink(emulatorPackage: String?): Boolean =
        emulatorPackage != null && EmulatorRegistry.getByPackage(emulatorPackage)?.id == EMULATOR_ID

    fun statesDir(storageRoot: File): File = File(storageRoot, STATES_DIR)

    fun previewDir(storageRoot: File): File = File(storageRoot, PREVIEW_DIR)

    fun stateStem(romFileName: String): String = romFileName.substringBeforeLast('.')

    fun previewFileName(romFileName: String, slotNumber: Int): String =
        "$romFileName.$slotNumber.$PREVIEW_EXTENSION"

    fun menuSlotLabel(slotNumber: Int): Int = slotNumber + 1

    fun list(storageRoot: File, romFileName: String): List<MinarchLinkState> {
        val pattern = StatePathRegistry.getConfig(EMULATOR_ID)?.slotPattern ?: return emptyList()
        val previews = previewDir(storageRoot)
        return StatePathRegistry.discoverStates(
            statesDir(storageRoot),
            StateNameKey(stateStem(romFileName), emptySet()),
            pattern
        )
            .filter { (_, slot) -> slot in 0 until MENU_SLOT_COUNT }
            .map { (file, slot) ->
                MinarchLinkState(
                    slotNumber = slot,
                    savedAtMillis = file.lastModified(),
                    previewFile = File(previews, previewFileName(romFileName, slot)).takeIf { it.isFile }
                )
            }
            .sortedByDescending { it.savedAtMillis }
    }
}
