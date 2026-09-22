package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SecondaryDisplayType { NONE, BUILT_IN, EXTERNAL }

@Singleton
class DisplayAffinityHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenCatalog: ScreenCatalog
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val physicalDisplays: Array<Display>
        get() = displayManager.displays.filter { it.isPhysicalDisplay() }.toTypedArray()

    val hasPhysicalSecondaryDisplay: Boolean
        get() = physicalDisplays.size > 1

    var dualScreenEnabled: Boolean = false

    /**
     * False once the companion has been proven unable to initialize on the secondary display,
     * which happens on OS builds that do not let a home activity run there. Gates every
     * dual-screen entry point until a display change or an explicit user re-enable re-probes it.
     */
    var secondaryDisplayUsable: Boolean = true

    /**
     * The display external apps are sent to, when the player has named one. Independent of
     * [hasSecondaryDisplay]: a layout with no presentation screen runs the launcher single-screen
     * and still places app launches here. Games ignore it unless one names it explicitly, so a
     * third screen taking the app-target role does not move gameplay off the built-in panel.
     */
    var appTargetDisplayId: Int? = null

    private val resolvedAppTarget: Int?
        get() = appTargetDisplayId?.takeIf { id -> physicalDisplays.any { it.displayId == id } }

    val hasSecondaryDisplay: Boolean
        get() = dualScreenEnabled && secondaryDisplayUsable && hasPhysicalSecondaryDisplay

    val secondaryDisplayType: SecondaryDisplayType
        get() {
            val secondary = physicalDisplays.getOrNull(1) ?: return SecondaryDisplayType.NONE
            val type = secondary.displayType()
            return when {
                type == DISPLAY_TYPE_EXTERNAL -> SecondaryDisplayType.EXTERNAL
                type == DISPLAY_TYPE_BUILT_IN -> SecondaryDisplayType.BUILT_IN
                secondary.flags and Display.FLAG_PRESENTATION != 0 -> SecondaryDisplayType.EXTERNAL
                else -> SecondaryDisplayType.BUILT_IN
            }
        }

    /**
     * The displays the stored layout gives the two surface-bearing roles, pushed in whenever the
     * layout is applied. Empty until then, and positions are used instead.
     */
    var roleDisplayIds: Pair<Int, Int>? = null

    private val attachedIds: Set<Int>
        get() = physicalDisplays.map { it.displayId }.toSet()

    private val secondaryDisplayId: Int?
        get() = resolveSecondaryDisplayId(
            roleDisplayIds,
            attachedIds,
            physicalDisplays.getOrNull(1)?.displayId
        )

    /**
     * The roomiest attached display: an external panel over a built-in one, then by pixel area.
     */
    fun largestDisplayId(): Int? =
        pickLargestScreen(screenCatalog.attachedScreens().filter { it.displayId in attachedIds })
            ?.displayId

    fun registerDisplayListener(
        listener: DisplayManager.DisplayListener,
        handler: android.os.Handler? = null
    ) {
        displayManager.registerDisplayListener(listener, handler)
    }

    fun unregisterDisplayListener(listener: DisplayManager.DisplayListener) {
        displayManager.unregisterDisplayListener(listener)
    }

    fun getCompanionLaunchOptions(): Bundle? {
        val displayId = secondaryDisplayId ?: return null
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    /**
     * The display holding the app-target role, while it is attached and holds neither of the two
     * roles that already carry a surface.
     */
    fun appScreenDisplayId(rolesSwapped: Boolean): Int? {
        val target = resolvedAppTarget ?: return null
        val roles = getRoleDisplayIds(rolesSwapped) ?: return target
        if (target == roles.first || target == roles.second) return null
        return target
    }

    fun getAppScreenLaunchOptions(rolesSwapped: Boolean): Bundle? {
        val displayId = appScreenDisplayId(rolesSwapped) ?: return null
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    fun getEmulatorDisplayId(rolesSwapped: Boolean): Int =
        getRoleDisplayIds(rolesSwapped)?.second ?: Display.DEFAULT_DISPLAY

    /**
     * Where an app, a video or anything else that is not a game belongs. [preferredScreenKey] is
     * the screen this app is pinned to, keyed as the stored screen layout keys it, and
     * [occupiedDisplayId] names a screen a game holds. Either one being absent passes it over for
     * this launch alone.
     */
    fun appLaunchDisplayId(
        preferredScreenKey: String? = null,
        rolesSwapped: Boolean = false,
        occupiedDisplayId: Int? = null
    ): Int? = resolveAppLaunchDisplayId(
        preferredDisplayId = preferredScreenKey?.let { key ->
            screenCatalog.attachedScreens().find { it.key == key }?.displayId
        },
        appTargetDisplayId = resolvedAppTarget,
        roleDisplayIds = getRoleDisplayIds(rolesSwapped),
        occupiedDisplayId = occupiedDisplayId
    )

    fun getAppLaunchOptions(
        preferredScreenKey: String? = null,
        rolesSwapped: Boolean = false,
        occupiedDisplayId: Int? = null
    ): Bundle? {
        val displayId = appLaunchDisplayId(preferredScreenKey, rolesSwapped, occupiedDisplayId)
            ?: return null
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    /**
     * The display a game goes to under [target], or null while [target] names no screen of its own
     * and the launch should be placed the way it is on a device with two screens.
     */
    fun getDisplayTargetId(target: EmulatorDisplayTarget, rolesSwapped: Boolean): Int? =
        resolveDisplayTargetId(
            target = target,
            roleDisplayIds = getRoleDisplayIds(rolesSwapped),
            appScreenDisplayId = appScreenDisplayId(rolesSwapped)
        )

    /**
     * Which physical display holds each role: the one the viewer is driving, then the one
     * describing what that screen has focused. Null on a single-screen device, where there are no
     * roles to hold.
     *
     * The interactive display carries Home, Library and Media. A role swap is the only thing that
     * moves them, so a caller asks which display holds its role instead of naming a display id,
     * and keeps landing correctly after a swap.
     */
    fun getRoleDisplayIds(rolesSwapped: Boolean): Pair<Int, Int>? =
        resolveRoleDisplayIds(roleDisplayIds, attachedIds, secondaryDisplayId, rolesSwapped)

    /**
     * Where the video player belongs once a game has claimed [emulatorDisplayId]: the other physical
     * display. Null when there is no second display, which is the single-screen answer - nothing
     * moves and the player stays where it is.
     */
    fun getMediaPlayerDisplayId(emulatorDisplayId: Int?): Int? {
        if (!hasSecondaryDisplay) return null
        val secondary = secondaryDisplayId ?: return null
        return if (emulatorDisplayId == secondary) Display.DEFAULT_DISPLAY else secondary
    }

    /**
     * A context associated with one physical display, for launches whose side effects key off the
     * caller's display rather than the launch options. Some dual-screen firmwares keep a volume
     * level per display and bind a new playback to the display the launching context belongs to;
     * the application context belongs to none, and such a launch inherits whichever screen was
     * touched last. Null when the display is gone, which callers treat as "keep the context you
     * already have".
     */
    fun displayContext(displayId: Int): Context? {
        val display = displayManager.getDisplay(displayId) ?: return null
        return context.createDisplayContext(display)
    }

    fun getActivityOptions(
        forEmulator: Boolean,
        rolesSwapped: Boolean = false,
        overrideDisplayId: Int? = null
    ): Bundle? {
        if (overrideDisplayId == null && !hasSecondaryDisplay && (forEmulator || resolvedAppTarget == null)) {
            return null
        }

        val targetDisplayId = overrideDisplayId
            ?: if (forEmulator) {
                getRoleDisplayIds(rolesSwapped)?.second
            } else {
                appLaunchDisplayId(rolesSwapped = rolesSwapped)
            }
            ?: return null

        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(targetDisplayId)
            .toBundle()
    }

    fun isPhysicalDisplay(displayId: Int): Boolean {
        val display = displayManager.getDisplay(displayId) ?: return false
        return display.isPhysicalDisplay()
    }

    companion object {
        private const val DISPLAY_TYPE_BUILT_IN = 1
        private const val DISPLAY_TYPE_EXTERNAL = 2

        private val KNOWN_DUAL_SCREEN_DEVICES = listOf("thor")

        private val INVERTED_INTERNAL_ORDER_DEVICES = emptyList<String>()

        fun isKnownDualScreenDevice(): Boolean =
            KNOWN_DUAL_SCREEN_DEVICES.any { Build.MODEL.contains(it, ignoreCase = true) }

        /**
         * Whether this model seats its smaller internal panel above the larger one, against the
         * arrangement every verified device uses. Add a model here when a report shows the default
         * layout hands it the wrong screen.
         */
        fun hasInvertedInternalOrder(): Boolean =
            INVERTED_INTERNAL_ORDER_DEVICES.any { Build.MODEL.contains(it, ignoreCase = true) }

        private fun Display.displayType(): Int? = try {
            Display::class.java.getMethod("getType").invoke(this) as? Int
        } catch (_: Exception) { null }

        private fun Display.isPhysicalDisplay(): Boolean {
            if (state == Display.STATE_OFF) return false
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN || type == DISPLAY_TYPE_EXTERNAL
            return flags and Display.FLAG_PRIVATE == 0
        }

        /**
         * The display carrying the companion surface: the role holder that is not the default
         * display, or [positionalFallback] while no layout has been applied.
         */
        internal fun resolveSecondaryDisplayId(
            roleDisplayIds: Pair<Int, Int>?,
            attachedIds: Set<Int>,
            positionalFallback: Int?
        ): Int? {
            roleDisplayIds
                ?.toList()
                ?.firstOrNull { it != Display.DEFAULT_DISPLAY && it in attachedIds }
                ?.let { return it }
            return positionalFallback
        }

        /**
         * The display [target] names, or null when it names none. An app-screen choice falls back
         * to the presentation screen, which is where the setting sends a game on a device with no
         * third screen attached.
         */
        internal fun resolveDisplayTargetId(
            target: EmulatorDisplayTarget,
            roleDisplayIds: Pair<Int, Int>?,
            appScreenDisplayId: Int?
        ): Int? {
            if (target == EmulatorDisplayTarget.DEFAULT) return null
            val (primary, presentation) = roleDisplayIds ?: return null
            return when (target) {
                EmulatorDisplayTarget.PRIMARY -> primary
                EmulatorDisplayTarget.PRESENTATION -> presentation
                EmulatorDisplayTarget.APP_SCREEN -> appScreenDisplayId ?: presentation
                EmulatorDisplayTarget.DEFAULT -> null
            }
        }

        /**
         * Where an app lands, best screen first: the one it is pinned to, the app-target role, the
         * presentation screen, then the interactive one. [occupiedDisplayId] names a screen a game
         * holds, which moves the launch down the list and never off it.
         */
        internal fun resolveAppLaunchDisplayId(
            preferredDisplayId: Int?,
            appTargetDisplayId: Int?,
            roleDisplayIds: Pair<Int, Int>?,
            occupiedDisplayId: Int?
        ): Int? {
            val candidates = listOfNotNull(
                preferredDisplayId,
                appTargetDisplayId,
                roleDisplayIds?.second,
                roleDisplayIds?.first
            ).distinct()
            return candidates.firstOrNull { it != occupiedDisplayId } ?: candidates.firstOrNull()
        }

        /**
         * The roomiest screen: any external panel outranks every built-in one, then pixel area
         * decides.
         */
        internal fun pickLargestScreen(screens: List<AttachedScreen>): AttachedScreen? =
            screens.maxWithOrNull(
                compareBy<AttachedScreen> { if (it.builtIn) 0 else 1 }
                    .thenBy { it.widthPx.toLong() * it.heightPx.toLong() }
            )

        /**
         * The display driving input, then the one describing it. [rolesSwapped] exchanges them.
         */
        internal fun resolveRoleDisplayIds(
            roleDisplayIds: Pair<Int, Int>?,
            attachedIds: Set<Int>,
            secondaryDisplayId: Int?,
            rolesSwapped: Boolean
        ): Pair<Int, Int>? {
            roleDisplayIds
                ?.takeIf { it.first in attachedIds && it.second in attachedIds }
                ?.let { (primary, presentation) ->
                    return if (rolesSwapped) presentation to primary else primary to presentation
                }
            val secondary = secondaryDisplayId ?: return null
            return if (rolesSwapped) {
                Display.DEFAULT_DISPLAY to secondary
            } else {
                secondary to Display.DEFAULT_DISPLAY
            }
        }
    }
}
