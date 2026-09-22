package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.nendo.argosy.util.PServerExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SECTION_CHANGE,
    SELECTION,
    BOUNDARY_HIT,
    ERROR,
    STRENGTH_PREVIEW
}

@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DEFAULT_STRENGTH = 0.5f
        private const val MIN_TICK_AMPLITUDE = 20
        private const val FOCUS_MS = 12L
        private const val SECTION_MS = 25L
        private const val SELECTION_MS = 30L
        private const val BOUNDARY_MS = 60L
        private const val ERROR_MS = 150L
        private val PREVIEW_TIMINGS = longArrayOf(0, 500, 100, 500, 100, 500)
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private var enabled = true

    @Volatile
    private var cachedStrength: Float? = null
    private val hasAmplitudeControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.hasAmplitudeControl() == true
    } else false

    val supportsSystemVibration: Boolean
        get() = PServerExecutor.isAvailable

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Reads the live system setting, which costs a binder transaction and a shell command.
     * Call it to display or re-sync the value; [vibrate] uses the cached strength so a
     * d-pad repeat does not issue one IPC per tick.
     */
    fun getSystemVibrationStrength(): Float {
        val strength = PServerExecutor.getSystemSettingFloat("vibrate_strength_value", DEFAULT_STRENGTH)
        cachedStrength = strength
        return strength
    }

    fun setSystemVibrationStrength(strength: Float): Boolean {
        val clamped = strength.coerceIn(0f, 1f)
        val applied = PServerExecutor.setSystemSettingFloat("vibrate_strength_value", clamped)
        if (applied) cachedStrength = clamped
        return applied
    }

    private fun currentStrength(): Float = cachedStrength ?: getSystemVibrationStrength()

    private fun getAmplitude(): Int = (currentStrength() * 255).toInt().coerceIn(1, 255)

    private fun scaledDuration(base: Long): Long = (base * (0.5f + currentStrength())).toLong().coerceAtLeast(1L)

    fun vibrate(pattern: HapticPattern) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when {
                hasAmplitudeControl -> amplitudeEffect(pattern)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> predefinedEffect(pattern)
                else -> fixedAmplitudeEffect(pattern)
            }
            vibrator.vibrate(effect)
        } else {
            legacyVibrate(vibrator, pattern)
        }
    }

    private fun amplitudeEffect(pattern: HapticPattern): VibrationEffect {
        val amplitude = getAmplitude()
        val tickAmplitude = amplitude.coerceIn(MIN_TICK_AMPLITUDE, 255)
        return when (pattern) {
            HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(FOCUS_MS, tickAmplitude)
            HapticPattern.SECTION_CHANGE -> VibrationEffect.createOneShot(SECTION_MS, tickAmplitude)
            HapticPattern.SELECTION -> VibrationEffect.createOneShot(SELECTION_MS, amplitude)
            HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(BOUNDARY_MS, 255)
            HapticPattern.ERROR -> VibrationEffect.createOneShot(ERROR_MS, 255)
            HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.createWaveform(
                PREVIEW_TIMINGS,
                intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                -1
            )
        }
    }

    private fun predefinedEffect(pattern: HapticPattern): VibrationEffect = when (pattern) {
        HapticPattern.FOCUS_CHANGE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        HapticPattern.SECTION_CHANGE -> VibrationEffect.createOneShot(SECTION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(BOUNDARY_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.ERROR -> VibrationEffect.createOneShot(ERROR_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.createWaveform(PREVIEW_TIMINGS, -1)
    }

    private fun fixedAmplitudeEffect(pattern: HapticPattern): VibrationEffect = when (pattern) {
        HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(scaledDuration(FOCUS_MS), VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.SECTION_CHANGE -> VibrationEffect.createOneShot(scaledDuration(SECTION_MS), VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.SELECTION -> VibrationEffect.createOneShot(scaledDuration(SELECTION_MS), VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(BOUNDARY_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.ERROR -> VibrationEffect.createOneShot(ERROR_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.createWaveform(PREVIEW_TIMINGS, -1)
    }

    @Suppress("DEPRECATION")
    private fun legacyVibrate(vibrator: Vibrator, pattern: HapticPattern) {
        when (pattern) {
            HapticPattern.FOCUS_CHANGE -> vibrator.vibrate(scaledDuration(FOCUS_MS))
            HapticPattern.SECTION_CHANGE -> vibrator.vibrate(scaledDuration(SECTION_MS))
            HapticPattern.SELECTION -> vibrator.vibrate(scaledDuration(SELECTION_MS))
            HapticPattern.BOUNDARY_HIT -> vibrator.vibrate(BOUNDARY_MS)
            HapticPattern.ERROR -> vibrator.vibrate(ERROR_MS)
            HapticPattern.STRENGTH_PREVIEW -> vibrator.vibrate(PREVIEW_TIMINGS, -1)
        }
    }
}
