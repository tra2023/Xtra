package com.github.andreyasadchy.xtra.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyIndicatorTest {

    @Test
    fun displayNameAppendsLoginOnlyWhenItDiffers() {
        assertEquals("name(login)", ReplyIndicator.displayName("name", "login", "0"))
        assertEquals("name", ReplyIndicator.displayName("name", "login", "1"))
        assertEquals("login", ReplyIndicator.displayName("name", "login", "2"))
    }

    @Test
    fun displayNameFallsBackWhenLoginsMatchOrAreMissing() {
        // Same name (case-insensitive) means no "(login)" suffix in any display mode.
        assertEquals("Name", ReplyIndicator.displayName("Name", "name", "0"))
        assertEquals("Name", ReplyIndicator.displayName("Name", "name", "1"))
        assertEquals("Name", ReplyIndicator.displayName("Name", "name", "2"))
        // Missing one side falls back to whichever is present.
        assertEquals("login", ReplyIndicator.displayName(null, "login", "0"))
        assertEquals("name", ReplyIndicator.displayName("name", null, "0"))
        assertNull(ReplyIndicator.displayName(null, null, "0"))
    }

    @Test
    fun labelFormatsNameAndMessage() {
        val format = { name: String?, message: String? -> "Replying to $name: $message" }
        assertEquals(
            "Replying to name(login): hello",
            ReplyIndicator.label("1", "name", "login", "hello", "0", format),
        )
    }

    @Test
    fun labelIsNullWithoutReplyIdOrMessage() {
        val format = { name: String?, message: String? -> "Replying to $name: $message" }
        assertNull(ReplyIndicator.label(null, "name", "login", "hello", "0", format))
        assertNull(ReplyIndicator.label("", "name", "login", "hello", "0", format))
        assertNull(ReplyIndicator.label("   ", "name", "login", "hello", "0", format))
        // A null message yields no text; the caller keeps the indicator visible but empty.
        assertNull(ReplyIndicator.label("1", "name", "login", null, "0", format))
    }
}
