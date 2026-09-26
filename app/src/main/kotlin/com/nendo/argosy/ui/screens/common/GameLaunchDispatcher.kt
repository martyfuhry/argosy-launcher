package com.nendo.argosy.ui.screens.common

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.PresenceEventKind
import com.nendo.argosy.data.emulator.isAlreadyLaunched
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameLaunchDispatcher"

@Singleton
class GameLaunchDispatcher internal constructor(
    private val context: Context,
    private val launchTargetResolver: EmulatorLaunchTargetResolver,
    private val playSessionTracker: PlaySessionTracker,
    private val permissionHelper: PermissionHelper,
    private val notificationManager: NotificationManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        launchTargetResolver: EmulatorLaunchTargetResolver,
        playSessionTracker: PlaySessionTracker,
        permissionHelper: PermissionHelper,
        notificationManager: NotificationManager
    ) : this(context, launchTargetResolver, playSessionTracker, permissionHelper, notificationManager, Dispatchers.IO)

    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, TAG)
    private var resumedHost: WeakReference<Activity>? = null
    private var arrivalWatch: Job? = null

    private val hostTracker = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedHost = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedHost?.get() === activity) resumedHost = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    init {
        (context as? Application)?.registerActivityLifecycleCallbacks(hostTracker)
    }

    /**
     * Places and starts [intent] for [gameId], then opens the session its launch prepared. An app
     * already running is brought forward and its session attached rather than counted as new.
     */
    fun dispatch(gameId: Long, intent: Intent, overrideDisplayId: Int? = null): Job = scope.launch {
        val packageName = intent.component?.packageName ?: intent.`package`
        val wasRunning = packageName != null && withContext(ioDispatcher) {
            permissionHelper.isPackageOnScreenOrRecent(context, packageName, RECENTLY_RUNNING_MS)
        }
        val options = launchTargetResolver.launchOptionsFor(gameId, intent, overrideDisplayId)
        val startedAtMs = System.currentTimeMillis()
        if (!start(intent, options)) {
            Logger.error(TAG, "Nothing could start gameId=$gameId (${intent.component ?: intent.`package`}), dropping its session")
            playSessionTracker.discardPreparedSession(gameId)
            if (playSessionTracker.activeSession.value == null) {
                DualScreenManagerHolder.instance?.setEmulatorDisplay(null)
            }
            notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
            return@launch
        }
        val watchedPackage = playSessionTracker.startPreparedSession(gameId, isNewGame = !wasRunning)
            ?.takeIf { it.isNotEmpty() }
            ?: return@launch
        arrivalWatch?.cancel()
        arrivalWatch = scope.launch {
            watchArrival(gameId, watchedPackage, intent, options, startedAtMs, retryOnce = wasRunning)
        }
    }

    private suspend fun watchArrival(
        gameId: Long,
        packageName: String,
        intent: Intent,
        options: Bundle?,
        startedAtMs: Long,
        retryOnce: Boolean
    ) {
        delay(ARRIVAL_TIMEOUT_MS)
        if (playSessionTracker.activeSession.value?.gameId != gameId) return
        val arrived = withContext(ioDispatcher) { hasArrived(packageName, startedAtMs) }
        if (arrived != false) return
        if (retryOnce) {
            Logger.warn(TAG, "gameId=$gameId: $packageName not in front after ${ARRIVAL_TIMEOUT_MS}ms, starting it once more")
            delay(RETRY_DELAY_MS)
            val retriedAtMs = System.currentTimeMillis()
            if (start(intent, options)) {
                watchArrival(gameId, packageName, intent, options, retriedAtMs, retryOnce = false)
                return
            }
        }
        if (playSessionTracker.activeSession.value?.gameId != gameId) return
        Logger.error(TAG, "gameId=$gameId: $packageName never came to the front, ending its session")
        playSessionTracker.cancelSession()
        notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
    }

    private fun hasArrived(packageName: String, sinceMs: Long): Boolean? {
        if (!permissionHelper.canObservePresence(context)) return null
        val foreignResume = permissionHelper.presenceEvents(context, sinceMs, System.currentTimeMillis())
            .any { it.kind == PresenceEventKind.ACTIVITY_RESUMED && it.packageName != context.packageName }
        return foreignResume || permissionHelper.isPackageOnScreen(context, packageName) == true
    }

    private fun start(intent: Intent, options: Bundle?): Boolean {
        if (intent.isAlreadyLaunched()) return true
        val host = resumedHost?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        if (host != null && startFrom(host, intent, options)) return true
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startFrom(context, intent, options)
    }

    private fun startFrom(launchContext: Context, intent: Intent, options: Bundle?): Boolean =
        try {
            launchContext.startActivity(intent, options)
            true
        } catch (e: Exception) {
            Logger.warn(TAG, "startActivity failed from ${launchContext.javaClass.simpleName}", e)
            false
        }

    private companion object {
        const val ARRIVAL_TIMEOUT_MS = 5_000L
        const val RETRY_DELAY_MS = 750L
        const val RECENTLY_RUNNING_MS = 10_000L
    }
}
