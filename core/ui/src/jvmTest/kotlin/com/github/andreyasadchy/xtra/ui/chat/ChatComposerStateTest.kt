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
    fun startReplyShowsTheIndicatorWithItsLabel() {
        val state = ChatComposerState()
        assertTrue(state.startReply("1", "name", "login", "hello", "0", format))
        assertTrue(state.replying)
        assertEquals("Replying to name(login): hello", state.replyLabel)
    }

    @Test
    fun startReplyWithABlankIdChangesNothing() {
        val state = ChatComposerState()
        assertFalse(state.startReply(null, "name", "login", "hello", "0", format))
        assertFalse(state.replying)
        assertNull(state.replyLabel)
        assertFalse(state.startReply("   ", "name", "login", "hello", "0", format))
        assertFalse(state.replying)
    }

    @Test
    fun startReplyWithoutAMessageStillShowsTheIndicator() {
        // The original set the indicator VISIBLE and only skipped the text when message was null.
        val state = ChatComposerState()
        assertTrue(state.startReply("1", "name", "login", null, "0", format))
        assertTrue(state.replying)
        assertNull(state.replyLabel)
    }

    @Test
    fun cancelReplyHidesTheIndicator() {
        val state = ChatComposerState()
        state.startReply("1", "name", "login", "hello", "0", format)
        state.cancelReply()
        assertFalse(state.replying)
        assertNull(state.replyLabel)
    }

    @Test
    fun sendingClearsTheReply() {
        val state = ChatComposerState()
        state.startReply("1", "name", "login", "hello", "0", format)
        state.onSent()
        assertFalse(state.replying)
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
