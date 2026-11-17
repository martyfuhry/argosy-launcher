package com.nendo.argosy.data.emulator

enum class MinarchStateOp(val token: String, val action: String) {
    SAVE("save", MinarchLinkCommands.ACTION_SAVE_STATE),
    LOAD("load", MinarchLinkCommands.ACTION_LOAD_STATE);

    companion object {
        fun fromToken(token: String?): MinarchStateOp? = entries.firstOrNull { it.token == token }
    }
}

data class MinarchStateCommand(val op: MinarchStateOp, val slotNumber: Int)

data class MinarchStateResult(
    val command: MinarchStateCommand,
    val ok: Boolean,
    val reason: String?
)

/**
 * Minarch Link's Save/Load State broadcast contract, mirrored from its `StateCommands.java`: a
 * command goes to [PACKAGE] with [EXTRA_SLOT] 0 to 7, and the outcome comes back as
 * [ACTION_STATE_RESULT] carrying the op token, the slot, [EXTRA_OK] and, when refused, a reason token.
 */
object MinarchLinkCommands {
    const val PACKAGE = "farm.fuhry.minarch"
    const val ACTION_SAVE_STATE = "farm.fuhry.minarch.action.SAVE_STATE"
    const val ACTION_LOAD_STATE = "farm.fuhry.minarch.action.LOAD_STATE"
    const val ACTION_STATE_RESULT = "farm.fuhry.minarch.action.STATE_RESULT"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_OP = "op"
    const val EXTRA_OK = "ok"
    const val EXTRA_REASON = "reason"
    const val REASON_LINKED = "linked"

    fun isMenuSlot(slotNumber: Int): Boolean = slotNumber in 0 until MinarchLinkStates.MENU_SLOT_COUNT

    /**
     * The slot a dashboard Save State writes: the first empty menu slot, else the one saved longest
     * ago.
     */
    fun nextSaveSlot(savedAtBySlot: Map<Int, Long>): Int =
        (0 until MinarchLinkStates.MENU_SLOT_COUNT).firstOrNull { it !in savedAtBySlot }
            ?: savedAtBySlot.filterKeys(::isMenuSlot).minBy { it.value }.key

    fun parseResult(opToken: String?, slotNumber: Int, ok: Boolean, reason: String?): MinarchStateResult? {
        val op = MinarchStateOp.fromToken(opToken) ?: return null
        if (!isMenuSlot(slotNumber)) return null
        return MinarchStateResult(MinarchStateCommand(op, slotNumber), ok, reason.takeUnless { ok })
    }
}
