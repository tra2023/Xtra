package com.github.andreyasadchy.xtra.model.chat

import kotlin.random.Random

class ChatMessage(
    val type: Int = SYSTEM_MESSAGE,
    val id: String? = null,
    val userId: String? = null,
    val userLogin: String? = null,
    val userName: String? = null,
    val message: String? = null,
    val color: String? = null,
    val emotes: List<TwitchEmote>? = null,
    val badges: List<Badge>? = null,
    val isAction: Boolean = false,
    val isFirst: Boolean = false,
    val bits: Int? = null,
    val systemMsg: String? = null,
    val msgId: String? = null,
    val targetMsgId: String? = null,
    val reward: ChannelPointReward? = null,
    val reply: Reply? = null,
    val replyParent: ChatMessage? = null,
    val timestamp: Long? = null,
    val fullMsg: String? = null,
    /**
     * Stable per-instance identity used as the Compose list key. Not part of the message content;
     * it only lets the chat list keep existing rows when messages are added or trimmed so it does
     * not re-render (and re-format) the whole visible list.
     */
    val key: Long = Random.nextLong(),
) {
    companion object {
        const val SYSTEM_MESSAGE = 0
        const val USER_MESSAGE = 1
        const val REPLY_MESSAGE = 2
        const val NOTICE_MESSAGE = 3
    }
}