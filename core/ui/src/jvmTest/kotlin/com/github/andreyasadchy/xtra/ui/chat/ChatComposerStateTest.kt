package com.github.andreyasadchy.xtra.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatComposerStateTest {

    private val format = { name: String?, message: String? -> "Replying to $name: $message" }

    @Test
    fun startsWithoutAReply() {
        val state = ChatComposerState()
        assertFalse(state.replying)
        assertNull(state.replyLabel)
    }

    @Test
    fun startReplyRecordsTheLabelAndIsAppliedBySetReplying() {
        val state = ChatComposerState()
        assertTrue(state.startReply("1", "name", "login", "hello", "0", format))
        assertEquals("Replying to name(login): hello", state.replyLabel)
        // Visibility is applied separately, so the state cannot disagree with the View mid-update.
        assertFalse(state.replying)
        state.setReplying(true)
        assertTrue(state.replying)
    }

    @Test
    fun startReplyWithABlankIdChangesNothing() {
        val state = ChatComposerState()
        assertFalse(state.startReply(null, "name", "login", "hello", "0", format))
        assertNull(state.replyLabel)
        assertFalse(state.startReply("   ", "name", "login", "hello", "0", format))
        assertNull(state.replyLabel)
    }

    @Test
    fun startReplyWithoutAMessageLeavesTheIndicatorTextEmpty() {
        // The original set the indicator VISIBLE and only skipped the text when message was null.
        val state = ChatComposerState()
        assertTrue(state.startReply("1", "name", "login", null, "0", format))
        assertNull(state.replyLabel)
        state.setReplying(true)
        assertTrue(state.replying)
    }

    @Test
    fun cancellingHidesTheIndicatorAndDropsTheLabel() {
        val state = ChatComposerState()
        state.startReply("1", "name", "login", "hello", "0", format)
        state.setReplying(true)
        state.setReplying(false)
        state.onSent()
        assertFalse(state.replying)
        assertNull(state.replyLabel)
    }

    @Test
    fun sendingClearsTheLabel() {
        val state = ChatComposerState()
        state.startReply("1", "name", "login", "hello", "0", format)
        state.setReplying(true)
        state.onSent()
        assertNull(state.replyLabel)
    }

    @Test
    fun sendAffordancesFollowTheDraft() {
        val state = ChatComposerState()
        assertFalse(state.sendVisible(""))
        assertFalse(state.sendVisible("   "))
        assertTrue(state.sendVisible("hi"))
        assertTrue(state.sendVisible("  hi  "))
    }
}
