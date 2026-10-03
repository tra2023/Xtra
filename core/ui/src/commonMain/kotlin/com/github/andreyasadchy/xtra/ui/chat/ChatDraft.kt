package com.github.andreyasadchy.xtra.ui.chat

/**
 * The chat composer's draft text, as a value.
 *
 * The fragment edits through the `Editable` on its `MultiAutoCompleteTextView`; these are the same
 * operations expressed as plain strings, so the rules are testable and a Compose composer can reuse
 * them:
 *
 * - [afterAppendEmote] appends the emote name and a separating space **at the end of the draft**.
 * - [textAfterDeletingLastWord] drops the trailing word (the clear button).
 * - [consume] returns the trimmed message to send; an all-whitespace draft yields null, so nothing
 *   is sent and the caller clears the field regardless.
 * - [setMessage] replaces the whole draft, used when a clicked message is copied back into the
 *   composer.
 *
 * Note on [afterAppendEmote]: the original was `editText.text.append(name).append(' ')`, which
 * appends to the end and does not move the caret. Inserting at the caret would read better, but it
 * is a behaviour change, so the end-append is preserved and pinned by a test.
 */
object ChatDraft {

    /**
     * Draft after appending an emote: plain concatenation of the draft, the name and one separating
     * space. Existing whitespace is not normalised, matching `text.append(name).append(' ')`.
     */
    fun afterAppendEmote(text: String, emoteName: String): String = text + emoteName + " "

    /** Draft remaining after the clear button. */
    fun textAfterDeletingLastWord(text: String): String = ChatInput.textAfterDeletingLastWord(text)

    /**
     * Message to send, or null when the draft holds only whitespace. Mirrors the fragment's
     * `val text = editText.text.trim(); editText.text.clear(); if (text.isNotEmpty()) send(...)`.
     */
    fun consume(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    /** Whether the draft would send something if Enter were pressed right now. */
    fun hasContent(text: String): Boolean = consume(text) != null

    /** Replaces the draft, used when a message is copied back into the composer. */
    fun setMessage(message: String): String = message
}
