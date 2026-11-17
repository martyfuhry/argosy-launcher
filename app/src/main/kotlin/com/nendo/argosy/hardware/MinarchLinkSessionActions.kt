package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationCategory
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.MinarchLinkCommands
import com.nendo.argosy.data.emulator.MinarchStateCommand
import com.nendo.argosy.data.emulator.MinarchStateOp
import com.nendo.argosy.data.emulator.MinarchStateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MinarchLinkActions"
private const val ANSWER_TIMEOUT_MS = 8000L

enum class MinarchStateFeedback(@StringRes val messageRes: Int, val success: Boolean) {
    SAVED(R.string.dual_dashboard_minarch_state_saved, true),
    LOADED(R.string.dual_dashboard_minarch_state_loaded, true),
    SAVE_FAILED(R.string.dual_dashboard_minarch_save_failed, false),
    LOAD_FAILED(R.string.dual_dashboard_minarch_load_failed, false),
    LOAD_LINKED(R.string.dual_dashboard_minarch_load_linked, false),
    NO_ANSWER(R.string.dual_dashboard_minarch_no_answer, false);

    companion object {
        fun of(result: MinarchStateResult): MinarchStateFeedback = when {
            result.ok && result.command.op == MinarchStateOp.SAVE -> SAVED
            result.ok -> LOADED
            result.command.op == MinarchStateOp.SAVE -> SAVE_FAILED
            result.reason == MinarchLinkCommands.REASON_LINKED -> LOAD_LINKED
            else -> LOAD_FAILED
        }
    }
}

/**
 * The dashboard's quick actions for a Minarch Link game: Save State and Load State go to Minarch
 * as broadcasts, and its answer is shown as a save notice. Everything runs on the main thread.
 */
class MinarchLinkSessionActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val notificationManager: NotificationManager,
    private val savedAtBySlot: () -> Map<Int, Long>,
    private val onQuit: () -> Unit
) : DualScreenManager.SessionQuickActions {

    private var pending: MinarchStateCommand? = null
    private var answerTimeout: Job? = null

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val result = MinarchLinkCommands.parseResult(
                opToken = intent.getStringExtra(MinarchLinkCommands.EXTRA_OP),
                slotNumber = intent.getIntExtra(MinarchLinkCommands.EXTRA_SLOT, -1),
                ok = intent.getBooleanExtra(MinarchLinkCommands.EXTRA_OK, false),
                reason = intent.getStringExtra(MinarchLinkCommands.EXTRA_REASON)
            ) ?: return
            if (result.command != pending) return
            pending = null
            answerTimeout?.cancel()
            show(MinarchStateFeedback.of(result))
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            context,
            resultReceiver,
            IntentFilter(MinarchLinkCommands.ACTION_STATE_RESULT),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun stop() {
        answerTimeout?.cancel()
        pending = null
        runCatching { context.unregisterReceiver(resultReceiver) }
            .onFailure { Log.w(TAG, "Result receiver was not registered", it) }
    }

    override fun quickSave() {
        send(MinarchStateCommand(MinarchStateOp.SAVE, MinarchLinkCommands.nextSaveSlot(savedAtBySlot())))
    }

    override fun loadState(slotNumber: Int, savedAtMillis: Long) {
        if (!MinarchLinkCommands.isMenuSlot(slotNumber)) return
        send(MinarchStateCommand(MinarchStateOp.LOAD, slotNumber))
    }

    override fun screenshot() = Unit

    override fun openCheats() = Unit

    override fun openGameSettings() = Unit

    override fun quit() = onQuit()

    private fun send(command: MinarchStateCommand) {
        pending = command
        context.sendBroadcast(
            Intent(command.op.action)
                .setPackage(MinarchLinkCommands.PACKAGE)
                .putExtra(MinarchLinkCommands.EXTRA_SLOT, command.slotNumber)
        )
        answerTimeout?.cancel()
        answerTimeout = scope.launch {
            delay(ANSWER_TIMEOUT_MS)
            if (pending == command) show(MinarchStateFeedback.NO_ANSWER)
        }
    }

    private fun show(feedback: MinarchStateFeedback) {
        notificationManager.show(
            title = NotificationText.Res(feedback.messageRes),
            type = if (feedback.success) NotificationType.SUCCESS else NotificationType.WARNING,
            category = NotificationCategory.SAVE
        )
    }
}
