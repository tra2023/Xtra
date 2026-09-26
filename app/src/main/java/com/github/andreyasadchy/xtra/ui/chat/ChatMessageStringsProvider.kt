package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessageStrings
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

/** Localized strings of the chat renderer, resolved through the app resources. */
fun Context.chatMessageStrings(): ChatMessageStrings = ChatMessageStrings(
    firstChatMsg = getString(R.string.chat_first),
    rewardChatMsg = getString(R.string.chat_reward),
    redeemedChatMsg = { getString(R.string.redeemed, it) },
    redeemedNoMsg = { userName, rewardTitle -> getString(R.string.user_redeemed, userName, rewardTitle) },
    replyMessage = { userName, _ -> getString(R.string.replying_to_message, userName, "") },
    messageIdLabel = { TwitchApiHelper.getMessageIdString(this, it) ?: it },
)

/** Fallback used until the fragment replaces [ChatState.options.strings] with the real resources. */
internal val PlaceholderChatMessageStrings = ChatMessageStrings(
    firstChatMsg = "First time chat",
    rewardChatMsg = "Channel point redemption",
    redeemedChatMsg = { "Redeemed $it" },
    redeemedNoMsg = { userName, rewardTitle -> "$userName redeemed $rewardTitle" },
    replyMessage = { userName, _ -> "Replying to $userName: " },
    messageIdLabel = { it },
)
