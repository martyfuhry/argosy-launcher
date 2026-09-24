package com.nendo.argosy.data.emulator

import android.content.Intent
import com.nendo.argosy.libretro.LibretroActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondScreenCapabilityTest {

    private val libretro = LibretroActivity::class.java.name

    @Test
    fun `emulators that show a second screen on their own display are flagged`() {
        listOf(
            "org.azahar_emu.azahar",
            "org.azahar_emu.azahar.thor",
            "io.github.lime3ds.android",
            "org.citra.citra_emu",
            "me.magnum.melonds",
            "me.magnum.melondualds",
            "me.magnum.melonds.nightly",
            "com.dsemu.drastic"
        ).forEach { assertTrue(it, EmulatorRegistry.drawsSecondScreen(it)) }
    }

    @Test
    fun `other and unknown emulators are single-screen`() {
        listOf(
            "com.retroarch.aarch64",
            "org.ppsspp.ppsspp",
            "com.github.stenzek.duckstation",
            "io.github.borked3ds.android",
            "com.example.unknown"
        ).forEach { assertFalse(it, EmulatorRegistry.drawsSecondScreen(it)) }
    }

    @Test
    fun `a definition without the flag still draws a second screen when its family does`() {
        assertFalse(EmulatorRegistry.getByPackage("org.citra.emu")!!.drawsSecondScreen)
        assertTrue(EmulatorRegistry.findFamilyForPackage("org.citra.emu")!!.drawsSecondScreen)

        assertTrue(EmulatorRegistry.drawsSecondScreen("org.citra.emu"))
    }

    @Test
    fun `the built-in emulator draws a second screen only with a core that splits its frame`() {
        assertTrue(LaunchDisplayPlanner.drawsSecondScreen(libretro, "com.nendo.argosy", "melonds"))
        assertTrue(LaunchDisplayPlanner.drawsSecondScreen(libretro, "com.nendo.argosy", "azahar"))
        assertFalse(LaunchDisplayPlanner.drawsSecondScreen(libretro, "com.nendo.argosy", "desmume"))
        assertFalse(LaunchDisplayPlanner.drawsSecondScreen(libretro, "com.nendo.argosy", "snes9x"))
        assertFalse(LaunchDisplayPlanner.drawsSecondScreen(libretro, "com.nendo.argosy", null))
    }

    @Test
    fun `a launch with no package draws a single screen`() {
        assertFalse(LaunchDisplayPlanner.drawsSecondScreen(className = null, packageName = null, coreId = null))
    }

    @Test
    fun `a shell launch names the display it was given`() {
        val command = EffectiveLaunchCommand(
            action = Intent.ACTION_VIEW,
            packageName = "org.azahar_emu.azahar",
            activityClass = "org.citra.citra_emu.activities.EmulationActivity",
            categories = emptyList(),
            intentFlags = 0
        )

        assertTrue(command.toShellArgv(displayId = 0).last().startsWith("/system/bin/am start --display 0 "))
        assertFalse(command.toShellArgv(displayId = null).last().contains("--display"))
    }
}
