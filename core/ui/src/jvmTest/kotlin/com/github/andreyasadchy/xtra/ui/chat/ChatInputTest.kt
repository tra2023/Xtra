package com.github.andreyasadchy.xtra.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatInputTest {

    @Test
    fun deletingLastWordDropsTheTrailingWord() {
        assertEquals("hello", ChatInput.textAfterDeletingLastWord("hello world"))
        assertEquals("hello world", ChatInput.textAfterDeletingLastWord("hello world again"))
    }

    @Test
    fun deletingLastWordIgnoresTrailingWhitespace() {
        // Trailing space is trimmed before the cut, so the last word goes either way.
        assertEquals("hello", ChatInput.textAfterDeletingLastWord("hello world "))
        assertEquals("hello", ChatInput.textAfterDeletingLastWord("hello world   "))
    }

    @Test
    fun deletingLastWordKeepsEarlierWordsIntact() {
        assertEquals("one two", ChatInput.textAfterDeletingLastWord("one two three"))
        assertEquals("one two", ChatInput.textAfterDeletingLastWord("one two three "))
    }

    @Test
    fun deletingLastWordEmptiesSingleWordAndWhitespace() {
        assertEquals("", ChatInput.textAfterDeletingLastWord("hello"))
        assertEquals("", ChatInput.textAfterDeletingLastWord(""))
        assertEquals("", ChatInput.textAfterDeletingLastWord("   "))
        assertEquals("", ChatInput.textAfterDeletingLastWord(" hello"))
    }

    @Test
    fun sendIsShownOnlyForNonBlankText() {
        assertTrue(ChatInput.shouldShowSend("hi"))
        assertTrue(ChatInput.shouldShowSend("  hi  "))
        assertFalse(ChatInput.shouldShowSend(""))
        assertFalse(ChatInput.shouldShowSend("   "))
        assertFalse(ChatInput.shouldShowSend(null))
    }

    // Android's KeyEvent.ACTION_DOWN / KEYCODE_ENTER values, so the test does not need the framework.
    private val actionDown = 0
    private val enter = 66

    @Test
    fun enterKeyDownSends() {
        assertTrue(ChatInput.shouldSendOnKey(actionDown, enter, actionDown, enter))
    }

    @Test
    fun otherKeysAndKeyUpDoNotSend() {
        // ACTION_UP (1) must not send, or every message would go twice.
        assertFalse(ChatInput.shouldSendOnKey(1, enter, actionDown, enter))
        assertFalse(ChatInput.shouldSendOnKey(actionDown, 67, actionDown, enter))
        assertFalse(ChatInput.shouldSendOnKey(1, 67, actionDown, enter))
    }
}
