package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage

/**
 * Dialog action buttons of the message dialog, replacing `updateButtons`:
 * reply ([R.string.reply]), copy the message ([R.string.copy_message]), copy the IRC clip
 * ([R.string.copy_clip]), copy the full IRC message ([R.string.copy_fullmsg]) and, when the
 * user lookup failed, view the profile ([R.string.view_profile]).
 */
fun messageClickedButtons(
    context: ButtonLabelContext,
    messagingEnabled: Boolean,
    selected: ChatMessage?,
    userFailed: Boolean,
    allowCopyFullMsg: Boolean,
    debugFullMsgButton: String,
    onReply: (ChatMessage) -> Unit,
    onCopyMessage: (ChatMessage) -> Unit,
    onCopyClip: (ChatMessage) -> Unit,
    onCopyFullMsg: (ChatMessage) -> Unit,
    onViewProfile: () -> Unit,
): List<Pair<String, () -> Unit>> {
    val buttons = mutableListOf<Pair<String, () -> Unit>>()
    if (selected != null && messagingEnabled && (!selected.userId.isNullOrBlank() || !selected.userLogin.isNullOrBlank())) {
        if (!selected.id.isNullOrBlank()) {
            buttons.add(context.reply to { onReply(selected) })
        }
        val message = selected.message
        if (!message.isNullOrBlank()) {
            buttons.add(context.copyMessage to { onCopyMessage(selected) })
        }
    }
    if (selected?.message != null) {
        buttons.add(context.copyClip to { onCopyClip(selected!!) })
    }
    if (selected?.fullMsg != null && allowCopyFullMsg) {
        buttons.add(debugFullMsgButton to { onCopyFullMsg(selected!!) })
    }
    if (userFailed) {
        buttons.add(context.viewProfile to onViewProfile)
    }
    return buttons
}

/** Localized labels of the dialog buttons, resolved once by the host fragment. */
class ButtonLabelContext(
    val reply: String,
    val copyMessage: String,
    val copyClip: String,
    val copyFullMsg: String,
    val viewProfile: String,
)
