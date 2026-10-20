package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LidSleepGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val displayPrefs: DisplayPreferencesRepository
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val hallSensor: Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL)
        .firstOrNull { it.isHallSensor() && !it.isWakeUpSensor }
        ?: sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull { it.isHallSensor() }

    val isSupported: Boolean
        get() = hallSensor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    val serviceReady: StateFlow<Boolean> get() = FocusAccessibilityService.connected

    val isServiceReady: Boolean get() = serviceReady.value

    private var running = false
    private var enabled = false
    private var listening = false
    private var lidClosed = false
    private var lidReadAt = 0L
    private var closedAt = 0L
    private var closeConfirmed = false
    private var screenOnAt = 0L
    private var staleRetries = 0
    private var attemptWindowStart = 0L
    private var attemptsInWindow = 0

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val closed = event.values.firstOrNull()?.let { it <= 0f } ?: return
            lidReadAt = SystemClock.elapsedRealtime()
            if (closed == lidClosed) return
            Logger.info(TAG, "Hall sensor value=${event.values.first()} closed=$closed")
            onLidChanged(closed)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!lidClosed) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (closeConfirmed) return
                    if (!LidSleepDecision.armsOnScreenOff(closedAt, SystemClock.elapsedRealtime())) return
                    closeConfirmed = true
                    Logger.info(TAG, "Lid close followed by sleep, guard armed")
                }
                Intent.ACTION_SCREEN_ON -> {
                    Logger.info(TAG, "Screen turned on with the lid closed")
                    screenOnAt = SystemClock.elapsedRealtime()
                    staleRetries = 0
                    refreshReading()
                    scheduleSleepCheck(WAKE_GRACE_MS)
                }
            }
        }
    }

    private val sleepCheck = Runnable { sleepIfLidClosed() }

    fun start() {
        if (running || !isSupported) return
        running = true
        scope.launch {
            displayPrefs.preferences.map { it.lidSleepEnabled }.distinctUntilChanged().collect { on ->
                enabled = on
                if (on) listen() else unlisten()
            }
        }
    }

    private fun listen() {
        val sensor = hallSensor ?: return
        if (listening) return
        lidClosed = false
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, filter)
        listening = true
        Logger.info(TAG, "Listening on ${sensor.name}")
    }

    private fun unlisten() {
        if (!listening) return
        handler.removeCallbacks(sleepCheck)
        sensorManager.unregisterListener(sensorListener)
        context.unregisterReceiver(screenReceiver)
        listening = false
        lidClosed = false
    }

    private fun refreshReading() {
        val sensor = hallSensor ?: return
        sensorManager.unregisterListener(sensorListener)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
    }

    private fun onLidChanged(closed: Boolean) {
        lidClosed = closed
        handler.removeCallbacks(sleepCheck)
        if (closed) {
            closedAt = SystemClock.elapsedRealtime()
            scheduleSleepCheck(CLOSE_GRACE_MS)
        }
    }

    private fun scheduleSleepCheck(delayMs: Long) {
        handler.removeCallbacks(sleepCheck)
        handler.postDelayed(sleepCheck, delayMs)
    }

    private fun sleepIfLidClosed() {
        val now = SystemClock.elapsedRealtime()
        if (now - attemptWindowStart > ATTEMPT_WINDOW_MS) {
            attemptWindowStart = now
            attemptsInWindow = 0
        }
        val service = FocusAccessibilityService.instance
        val state = LidSleepState(
            enabled = enabled,
            lidClosed = lidClosed,
            closeConfirmed = closeConfirmed,
            interactive = powerManager.isInteractive,
            lidReadAt = lidReadAt,
            screenOnAt = screenOnAt,
            staleRetries = staleRetries,
            externalDisplayInUse = displayAffinityHelper.isDesktopMode ||
                displayAffinityHelper.isDockedDark ||
                displayAffinityHelper.hasLitExternalDisplay,
            attemptsInWindow = attemptsInWindow,
            serviceReady = service != null
        )
        when (LidSleepDecision.decide(state)) {
            LidSleepVerdict.IDLE -> Unit
            LidSleepVerdict.RETRY_READING -> {
                staleRetries++
                scheduleSleepCheck(WAKE_GRACE_MS)
            }
            LidSleepVerdict.STALE_READING ->
                Logger.warn(TAG, "No lid reading since the screen came on, leaving the device awake")
            LidSleepVerdict.EXTERNAL_DISPLAY ->
                Logger.info(TAG, "Lid closed with an external display in use, leaving the device awake")
            LidSleepVerdict.RATE_LIMITED ->
                Logger.warn(TAG, "Sleep attempts exhausted for now, leaving the device awake")
            LidSleepVerdict.SERVICE_OFF ->
                Logger.warn(TAG, "Lid closed with the screen on, but the accessibility service is off")
            LidSleepVerdict.SLEEP -> {
                attemptsInWindow++
                val requested = service?.lockScreen() == true
                Logger.info(TAG, "Lid closed with the screen on, sleep requested=$requested")
            }
        }
    }

    private fun Sensor.isHallSensor(): Boolean =
        stringType.contains(HALL, ignoreCase = true) || name.contains(HALL, ignoreCase = true)

    companion object {
        private const val TAG = "LidSleepGuard"
        private const val HALL = "hall"
        private const val CLOSE_GRACE_MS = LidSleepDecision.CLOSE_GRACE_MS
        private const val WAKE_GRACE_MS = LidSleepDecision.WAKE_GRACE_MS
        private const val ATTEMPT_WINDOW_MS = LidSleepDecision.ATTEMPT_WINDOW_MS
    }
}
