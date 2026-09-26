package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage

/**
 * Rows shown by [MessageClickedDialog]: everything from the clicked user, or everything of the
 * clicked message type for system/notice messages. Mirrors the filtering the old
 * `MessageClickedChatAdapter` did in its constructor.
 */
internal fun filterMessageDialogMessages(messages: List<ChatMessage>, selected: ChatMessage?): List<ChatMessage> {
    if (selected == null) {
        return messages
    }
    val filtered = if (selected.type == ChatMessage.USER_MESSAGE) {
        val userId = selected.userId
        val userLogin = selected.userLogin
        if (!userId.isNullOrBlank() || !userLogin.isNullOrBlank()) {
            messages.filter {
                (!userId.isNullOrBlank() && (it.userId == userId || it.replyParent?.userId == userId)) ||
                    (!userLogin.isNullOrBlank() && (it.userLogin == userLogin || it.replyParent?.userLogin == userLogin))
            }
        } else {
            emptyList()
        }
    } else {
        messages.filter { it.type == selected.type }
    }
    return filtered.ifEmpty { listOf(selected) }
}

/**
 * Rows shown by [ReplyClickedDialog]: the thread root plus every reply that points at it,
 * excluding the reply rows themselves. Mirrors `ReplyClickedChatAdapter`.
 */
internal fun filterReplyDialogMessages(messages: List<ChatMessage>, selected: ChatMessage?): List<ChatMessage> {
    if (selected == null) {
        return messages
    }
    val threadParentId = selected.reply?.threadParentId ?: selected.id
    val filtered = if (threadParentId != null) {
        messages.filter {
            (it.id == threadParentId || it.reply?.threadParentId == threadParentId) &&
                it.type != ChatMessage.REPLY_MESSAGE
        }
    } else {
        emptyList()
    }
    return filtered.ifEmpty { listOf(selected) }
}
