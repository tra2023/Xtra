package com.github.andreyasadchy.xtra.ui.chat

/**
 * State of the chat composer: the reply indicator and whether the send/clear affordances show.
 *
 * The fragment used to write this straight onto its Views in four places (start a reply, cancel a
 * reply, send, and the text-changed callback). Holding it as a value makes those transitions
 * testable, and gives a future Compose composer something to collect.
 *
 * Visibility itself is applied by the caller ([setReplying]), which sets [replying] and the View in
 * one step — so there is exactly one place that can change it:
 *
 * - starting a reply sets it true whenever the reply id is non-blank; a null message leaves the
 *   indicator on screen with no text, which is what the original `message?.let { … }` did;
 * - cancelling and sending set it false. Sending also clears the draft, so [onSent] is called on
 *   every send, empty or not.
 */
class ChatComposerState {

    /** True while the reply indicator is on screen. */
    var replying: Boolean = false
        private set

    /** Label of the reply indicator; null means it has no text (the indicator may still be shown). */
    var replyLabel: String? = null
        private set

    /** Whether the send and clear buttons are offered for the current draft. */
    fun sendVisible(draft: String): Boolean = ChatDraft.hasContent(draft)

    /**
     * Begins a reply and records its label. Returns false, leaving the state untouched, when the
     * reply id is blank — the caller's existing guard. The caller then applies [replying] through
     * [setReplying], which is also what turns the indicator on.
     */
    fun startReply(
        replyId: String?,
        userName: String?,
        userLogin: String?,
        message: String?,
        nameDisplay: String?,
        format: (String?, String?) -> String,
    ): Boolean {
        if (replyId.isNullOrBlank()) return false
        replyLabel = ReplyIndicator.label(replyId, userName, userLogin, message, nameDisplay, format)
        return true
    }

    /** The only setter for [replying]; the caller mirrors it onto its View. */
    fun setReplying(value: Boolean) {
        replying = value
    }

    /** Clears the composer after a send, empty or not: the reply goes away with the draft. */
    fun onSent() {
        replyLabel = null
    }
}
