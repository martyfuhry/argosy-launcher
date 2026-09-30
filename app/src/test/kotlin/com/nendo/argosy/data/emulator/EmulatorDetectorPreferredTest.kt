package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorDetectorPreferredTest {

    private fun detectorWith(vararg installedPackages: String): EmulatorDetector {
        val packageManager = mockk<PackageManager>()
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } answers {
            val name = firstArg<String>()
            if (name in installedPackages || name == "com.nendo.argosy") {
                mockk<PackageInfo>(relaxed = true)
            } else {
                throw PackageManager.NameNotFoundException(name)
            }
        }
        every { packageManager.getInstalledPackages(any<Int>()) } returns emptyList()
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.nendo.argosy"
        return EmulatorDetector(context)
    }

    @Test
    fun `with the built-in core off Pizza Boy is preferred over Minarch Link for gba`() = runTest {
        val detector = detectorWith("it.dbtecno.pizzaboygba", "farm.fuhry.minarch")
        detector.detectEmulators()

        val preferred = detector.getPreferredEmulator("gba", builtinEnabled = false)

        assertEquals("pizza_boy_gba", preferred?.def?.id)
    }

    @Test
    fun `Minarch Link is picked when it is the only gba emulator installed`() = runTest {
        val detector = detectorWith("farm.fuhry.minarch")
        detector.detectEmulators()

        val preferred = detector.getPreferredEmulator("gba", builtinEnabled = false)

        assertEquals("minarch_link", preferred?.def?.id)
    }
}
