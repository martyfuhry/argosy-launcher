package com.nendo.argosy.ui.components.launch

import androidx.annotation.StringRes
import com.nendo.argosy.domain.model.LaunchProgress

enum class CalmLaunchBar { NONE, INDETERMINATE, DETERMINATE }

data class CalmLaunchUi(
    @StringRes val line: Int?,
    val lineIsFailure: Boolean,
    val bar: CalmLaunchBar,
    val fraction: Float?,
    val sizeBytes: Long?,
    val percent: Int?,
    val etaSeconds: Int?,
    val cancellable: Boolean,
    val showsCalmColumn: Boolean,
    val revealAtMs: Long?,
    val reflowMs: Int,
    val statusFadeMs: Int,
    val statusFadeDelayMs: Int,
    val sweepMs: Int
)

object LaunchOverlayState {
    fun from(
        progress: LaunchProgress,
        nowMs: Long,
        reducedMotion: Boolean,
        patienceMs: Int,
        reflowMs: Int,
        statusFadeMs: Int,
        statusFadeDelayMs: Int,
        sweepMs: Int
    ): CalmLaunchUi = TODO("builder B")
}
