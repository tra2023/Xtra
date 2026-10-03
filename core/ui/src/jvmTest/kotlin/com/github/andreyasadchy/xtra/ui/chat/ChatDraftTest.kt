package com.github.andreyasadchy.xtra.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatDraftTest {

    @Test
    fun appendingAnEmoteAddsNameAndSpaceAtTheEnd() {
        assertEquals("hello Kappa ", ChatDraft.afterAppendEmote("hello ", "Kappa"))
        assertEquals("Kappa ", ChatDraft.afterAppendEmote("", "Kappa"))
        // Plain concatenation: existing whitespace is not normalised.
        assertEquals("   Kappa ", ChatDraft.afterAppendEmote("   ", "Kappa"))
    }

    @Test
    fun appendingIsPureConcatenation() {
        // The caret is deliberately ignored: the original appended to the end.
        // No space is inserted before the name; only the trailing one is added.
        assertEquals("hello worldKappa ", ChatDraft.afterAppendEmote("hello world", "Kappa"))
        // A draft already ending in a space therefore ends up with two.
        assertEquals("ab Kappa ", ChatDraft.afterAppendEmote("ab ", "Kappa"))
    }

    @Test
    fun consumingTrimsAndEmptiesTheDraft() {
        assertEquals("hello", ChatDraft.consume("  hello  "))
        assertEquals("hello world", ChatDraft.consume("hello world"))
    }

    @Test
    fun consumingAnEmptyOrBlankDraftSendsNothing() {
        assertNull(ChatDraft.consume(""))
        assertNull(ChatDraft.consume("   "))
        assertNull(ChatDraft.consume("\n\t "))
    }

    @Test
    fun hasContentMatchesWhatWouldSend() {
        assertTrue(ChatDraft.hasContent("hi"))
        assertTrue(ChatDraft.hasContent("  hi  "))
        assertFalse(ChatDraft.hasContent(""))
        assertFalse(ChatDraft.hasContent("   "))
    }

    @Test
    fun clearMatchesTheSharedLastWordRule() {
        assertEquals("hello", ChatDraft.textAfterDeletingLastWord("hello world"))
        assertEquals("", ChatDraft.textAfterDeletingLastWord("hello"))
    }

    @Test
    fun setMessageReplacesTheDraftVerbatim() {
        assertEquals("copied message", ChatDraft.setMessage("copied message"))
    }
}
