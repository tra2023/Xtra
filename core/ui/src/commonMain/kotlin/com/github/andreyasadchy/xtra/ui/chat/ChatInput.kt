package com.github.andreyasadchy.xtra.ui.chat

/**
 * Pure text rules of the chat message composer, so the decisions the fragment makes are testable
 * without a View.
 *
 * The fragment keeps the View-side concerns (adapter, IME action, focus bookkeeping); these are the
 * two rules that used to be computed inline:
 *
 * - [textAfterDeletingLastWord] backs the clear button, replacing
 *   `text.substring(0, max(text.lastIndexOf(' '), 0))` applied to the trimmed text.
 * - [shouldShowSend] decides whether the send and clear affordances are visible.
 */
object ChatInput {

    /**
     * Text remaining after deleting the last word.
     *
     * Trailing whitespace is trimmed first, then everything from the last space onwards is dropped
     * (the fragment's previous `text.substring(0, max(text.lastIndexOf(' '), 0))`). So
     * `"hello world"` and `"hello world   "` both become `"hello"`, while a single word or only
     * whitespace leaves an empty string.
     */
    fun textAfterDeletingLastWord(text: String): String {
        val trimmed = text.trimEnd()
        val cut = trimmed.lastIndexOf(' ')
        return if (cut <= 0) "" else trimmed.substring(0, cut)
    }

    /** Send and clear are only offered once the composer holds something other than whitespace. */
    fun shouldShowSend(text: String?): Boolean = !text.isNullOrBlank()
}
