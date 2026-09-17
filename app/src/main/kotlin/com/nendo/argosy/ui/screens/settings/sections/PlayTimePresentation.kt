package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.dualscreen.PlayTimeSlotGame
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.TimelineDot
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.ui.screens.settings.PlayTimeEntryUi
import com.nendo.argosy.ui.screens.settings.PlayTimeScrub
import com.nendo.argosy.ui.screens.settings.PlayTimeState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.util.formatPlayTime
import java.time.ZoneId

private const val MS_PER_MIN = 60_000L
private const val PRESENTED_ROWS = 12

/**
 * Hands the other display what the cursor is resting on: the run of games behind the focused
 * section, or the day the calendar is scrubbed to. A section with nothing to show publishes
 * nothing, which leaves the other screen on its fallback.
 */
@Composable
internal fun PlayTimePresentation(
    uiState: SettingsUiState,
    visibleItems: List<PlayTimeItem>,
    layoutState: PlayTimeLayoutState
) {
    val active = DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    if (!active) return

    val context = LocalContext.current
    val playTime = uiState.playTime
    val focused = playTimeLayout.itemAtFocusIndex(uiState.focusedIndex, layoutState)
        ?: visibleItems.firstOrNull { it.isFocusable }
    val slot = when {
        focused == null -> PresentationSlot.Fallback
        focused.section == "activity" -> playTime.timelineSlot(context)
        focused.section == "platforms" -> PresentationSlot.PlayTime(
            sectionLabel = stringResource(R.string.settings_play_time_section_platforms),
            games = playTime.platforms.toSlotGames(context)
        )
        focused.section == "where" -> PresentationSlot.PlayTime(
            sectionLabel = stringResource(R.string.settings_play_time_section_where),
            games = playTime.devices.toSlotGames(context)
        )
        focused.section == "what" -> PresentationSlot.PlayTime(
            sectionLabel = stringResource(R.string.settings_play_time_section_what),
            games = playTime.games.toSlotGames(context)
        )
        else -> PresentationSlot.Fallback
    }

    if (slot is PresentationSlot.PlayTime && slot.games.isEmpty()) return
    if (slot == PresentationSlot.Fallback) return
    PresentOnCompanion(SlotOwner("settings.playTime"), slot)
}

@Composable
private fun PlayTimeState.timelineSlot(context: android.content.Context): PresentationSlot {
    if (days.isEmpty()) return PresentationSlot.Fallback
    val zone = remember { ZoneId.systemDefault() }
    val theme = LocalArgosyTheme.current
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val slots = ComponentDefaults.PlayTimeChart.seriesSlots
    val slotOfSlug = remember(platforms, slots) {
        platforms.take(slots).withIndex().associate { (index, entry) -> entry.key to index }
    }
    val sessionsByDate = remember(sessions) { sessionsByDate(zone) }
    val daySlots = remember(days, sessionsByDate, slotOfSlug) {
        dayPlatformSlots(sessionsByDate, slotOfSlug)
    }
    val selectedIndex = playTimeScrubIndex(PlayTimeScrub.CALENDAR, this)
        ?.coerceIn(days.indices)
        ?: days.lastIndex
    val selectedDay = days[selectedIndex]
    return PresentationSlot.PlayTimeline(
        dots = days.mapIndexed { index, day ->
            TimelineDot(
                hasActivity = day.activeMs > 0L,
                color = daySlots[index]?.let { series[it] } ?: theme.textMute,
                label = day.date
                    .takeIf { it.dayOfMonth == 1 }
                    ?.month
                    ?.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            )
        },
        selectedIndex = selectedIndex,
        dayLabel = formatMonthDay(selectedDay.date.atStartOfDay(zone).toInstant()),
        dayTotal = selectedDay.activeMs
            .takeIf { it > 0L }
            ?.let { formatPlayTime(context, (it / MS_PER_MIN).toInt()) },
        games = gamesPlayedOn(selectedDay.date, context)
    )
}

private fun PlayTimeState.gamesPlayedOn(
    date: java.time.LocalDate,
    context: android.content.Context
): List<PlayTimeSlotGame> {
    val zone = ZoneId.systemDefault()
    return sessions
        .filter { it.startTime.atZone(zone).toLocalDate() == date }
        .groupBy { it.gameId }
        .map { (gameId, played) ->
            PlayTimeSlotGame(
                gameId = gameId,
                title = played.first().gameTitle,
                coverPath = coverPaths[gameId],
                detail = formatPlayTime(context, (played.sumOf { it.activeMs } / MS_PER_MIN).toInt()),
                subtitle = played.first().platformName
            )
        }
        .sortedByDescending { it.detail }
        .take(PRESENTED_ROWS)
}

private fun List<PlayTimeEntryUi>.toSlotGames(context: android.content.Context) =
    take(PRESENTED_ROWS).mapIndexed { index, entry ->
        PlayTimeSlotGame(
            gameId = entry.key.toLongOrNull() ?: index.toLong(),
            title = entry.name,
            coverPath = entry.coverPath,
            detail = formatPlayTime(context, (entry.activeMs / MS_PER_MIN).toInt())
        )
    }
