package com.nendo.argosy.util

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AttachedScreen(
    val key: String,
    val displayId: Int,
    val number: Int,
    val widthPx: Int,
    val heightPx: Int,
    val builtIn: Boolean
)

@Singleton
class ScreenCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun attachedScreens(): List<AttachedScreen> = displayManager.displays
        .filter { it.isUsablePhysicalDisplay() }
        .sortedBy { it.displayId }
        .mapIndexed { index, display ->
            val size = display.panelSize()
            AttachedScreen(
                key = display.stableKey(size),
                displayId = display.displayId,
                number = index + 1,
                widthPx = size.x,
                heightPx = size.y,
                builtIn = display.isBuiltIn()
            )
        }

    fun screenFor(displayId: Int): AttachedScreen? =
        attachedScreens().find { it.displayId == displayId }

    companion object {
        private const val DISPLAY_TYPE_BUILT_IN = 1
        private const val DISPLAY_TYPE_EXTERNAL = 2

        private fun Display.displayType(): Int? = try {
            Display::class.java.getMethod("getType").invoke(this) as? Int
        } catch (_: Exception) { null }

        private fun Display.uniqueIdOrNull(): String? = try {
            (Display::class.java.getMethod("getUniqueId").invoke(this) as? String)?.takeIf {
                it.isNotBlank()
            }
        } catch (_: Exception) { null }

        private fun Display.panelSize(): Point {
            val w = mode.physicalWidth
            val h = mode.physicalHeight
            if (w <= 0 || h <= 0) {
                return Point().also {
                    @Suppress("DEPRECATION")
                    getRealSize(it)
                }
            }
            val sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            return if (sideways) Point(h, w) else Point(w, h)
        }

        private fun Display.stableKey(size: Point): String {
            uniqueIdOrNull()?.let { return it }
            val short = minOf(size.x, size.y)
            val long = maxOf(size.x, size.y)
            return "display:$displayId:${short}x$long"
        }

        private fun Display.isBuiltIn(): Boolean {
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN
            return flags and Display.FLAG_PRESENTATION == 0
        }

        private fun Display.isUsablePhysicalDisplay(): Boolean {
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN || type == DISPLAY_TYPE_EXTERNAL
            return flags and Display.FLAG_PRIVATE == 0
        }
    }
}
