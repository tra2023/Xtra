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
}
