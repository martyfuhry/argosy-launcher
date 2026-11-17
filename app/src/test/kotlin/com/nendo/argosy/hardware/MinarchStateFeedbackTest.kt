package com.nendo.argosy.hardware

import com.nendo.argosy.data.emulator.MinarchStateCommand
import com.nendo.argosy.data.emulator.MinarchStateOp
import com.nendo.argosy.data.emulator.MinarchStateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinarchStateFeedbackTest {

    private fun result(op: MinarchStateOp, ok: Boolean, reason: String? = null) =
        MinarchStateResult(MinarchStateCommand(op, 2), ok, reason)

    @Test
    fun `successes say what was done`() {
        assertEquals(MinarchStateFeedback.SAVED, MinarchStateFeedback.of(result(MinarchStateOp.SAVE, ok = true)))
        assertEquals(MinarchStateFeedback.LOADED, MinarchStateFeedback.of(result(MinarchStateOp.LOAD, ok = true)))
        assertTrue(MinarchStateFeedback.SAVED.success)
        assertTrue(MinarchStateFeedback.LOADED.success)
    }

    @Test
    fun `a load refused while linked says so, other refusals are failures`() {
        assertEquals(MinarchStateFeedback.LOAD_LINKED, MinarchStateFeedback.of(result(MinarchStateOp.LOAD, false, "linked")))
        assertEquals(MinarchStateFeedback.LOAD_FAILED, MinarchStateFeedback.of(result(MinarchStateOp.LOAD, false, "empty")))
        assertEquals(MinarchStateFeedback.LOAD_FAILED, MinarchStateFeedback.of(result(MinarchStateOp.LOAD, false, null)))
        assertEquals(MinarchStateFeedback.SAVE_FAILED, MinarchStateFeedback.of(result(MinarchStateOp.SAVE, false, "write")))
        assertFalse(MinarchStateFeedback.LOAD_LINKED.success)
        assertFalse(MinarchStateFeedback.NO_ANSWER.success)
    }
}
