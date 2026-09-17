package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.domain.model.HomeLayoutSettings

/**
 * Which screen published the presentation content on the other display. Slots are held per owner so
 * that a screen taking over and the screen it replaced can publish and release in any order without
 * the later publish being undone by the earlier release.
 */
@JvmInline
value class SlotOwner(val id: String) {
    companion object {
        /**
         * An owner unique to [holder], for a screen that more than one surface can host at once.
         */
        fun of(name: String, holder: Any): SlotOwner = SlotOwner("$name@${System.identityHashCode(holder)}")
    }
}

/**
 * What the presentation screen shows while a screen on the control surface is open.
 *
 * Every case is authored beside the screen that publishes it; there is no generic payload a host
 * could render by guessing. A screen with nothing worth showing publishes nothing and the host
 * falls back.
 */
sealed interface PresentationSlot {
    data object Fallback : PresentationSlot

    data class HomeLayoutPreview(val settings: HomeLayoutSettings) : PresentationSlot

    data class PlayTime(
        val sectionLabel: String,
        val games: List<PlayTimeSlotGame>,
        val dateLabel: String? = null
    ) : PresentationSlot

    data class ScreenIdentity(val number: Int) : PresentationSlot

    data class Detail(val detail: CompanionDetail) : PresentationSlot

    data class Breakdown(
        val title: String,
        val subtitle: String?,
        val rows: List<BreakdownRow>
    ) : PresentationSlot

    data class PlatformShowcase(
        val name: String,
        val yearSpan: String?,
        val coverPaths: List<String>,
        val facts: List<CompanionFact>
    ) : PresentationSlot

    data class InGame(
        val state: com.nendo.argosy.hardware.CompanionInGameState,
        val achievements: List<com.nendo.argosy.core.game.AchievementUi>
    ) : PresentationSlot
}

data class BreakdownRow(
    val label: String,
    val value: String,
    val fraction: Float,
    val color: androidx.compose.ui.graphics.Color
)

data class PlayTimeSlotGame(
    val gameId: Long,
    val title: String,
    val coverPath: String?,
    val detail: String
)
