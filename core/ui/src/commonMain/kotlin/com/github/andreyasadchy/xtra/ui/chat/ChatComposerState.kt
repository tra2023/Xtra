package com.github.andreyasadchy.xtra.ui.chat

/**
 * State of the chat composer: the reply indicator and whether the send/clear affordances show.
 *
 * The fragment currently writes this straight onto its Views in four places (start a reply, cancel a
 * reply, send, and the text-changed callback). Holding it as a value makes those transitions
 * testable, and gives a future Compose composer something to collect.
 *
 * The transitions mirror the View behaviour exactly:
 *
 * - [startReply] shows the indicator whenever the reply id is non-blank; a null message leaves it on
 *   screen with no text, which is what the original `message?.let { … }` did.
 * - [cancelReply] and [onSent] hide it. Sending clears the draft and the reply together.
 * - The send/clear affordances follow the draft's content, per [ChatDraft.hasContent].
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
     * Begins a reply. Returns false, leaving the state untouched, when the reply id is blank — the
     * caller's existing guard.
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
        replying = true
        replyLabel = ReplyIndicator.label(replyId, userName, userLogin, message, nameDisplay, format)
        return true
    }

    /** Cancels the reply, as the indicator's close button does. */
    fun cancelReply() {
        replying = false
        replyLabel = null
    }

    /** Clears the composer after a message was sent (or an empty send attempt was made). */
    fun onSent() {
        cancelReply()
    }
}
