package com.github.andreyasadchy.xtra.ui.chat

/**
 * The reply-indicator shown above the chat composer.
 *
 * Two pieces of logic used to sit inline in `ChatFragment.onReplyClicked`, entangled with the View
 * updates around them:
 *
 * - the display name, honouring the `UI_NAME_DISPLAY` preference and only appending the login when
 *   it differs from the display name (see [displayName]);
 * - the indicator text (see [label]).
 *
 * Note on visibility, which is *not* modelled here because it is the caller's existing behaviour:
 * the indicator becomes visible as soon as the reply id is non-blank, and a null message leaves it
 * visible but empty (the original `message?.let { ... }` simply skipped setting the text). [label]
 * therefore returns null for a null message rather than suppressing the indicator.
 */
object ReplyIndicator {

    /**
     * Display name for the replied-to user.
     *
     * @param nameDisplay the `UI_NAME_DISPLAY` value: `"0"` shows `name(login)`, `"1"` shows the
     *   display name, anything else shows the login.
     */
    fun displayName(userName: String?, userLogin: String?, nameDisplay: String?): String? =
        if (userName != null && userLogin != null && !userLogin.equals(userName, true)) {
            when (nameDisplay) {
                "0" -> "$userName($userLogin)"
                "1" -> userName
                else -> userLogin
            }
        } else {
            userName ?: userLogin
        }

    /**
     * Text of the reply indicator, or null when there is nothing to show (no message, or no reply
     * id). The caller still decides visibility, so an empty message leaves its existing empty
     * indicator on screen exactly as before.
     *
     * @param format the localized `replying_to_message` template, applied as `(name, message)`.
     */
    fun label(
        replyId: String?,
        userName: String?,
        userLogin: String?,
        message: String?,
        nameDisplay: String?,
        format: (String?, String?) -> String,
    ): String? {
        if (replyId.isNullOrBlank()) return null
        if (message == null) return null
        return format(displayName(userName, userLogin, nameDisplay), message)
    }
}
