package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.ChatMessageStrings
import com.github.andreyasadchy.xtra.util.chat.ChatRenderCache
import com.github.andreyasadchy.xtra.util.chat.ChatRenderOptions

/**
 * Fallback used until the host replaces [ChatState.options] `.strings` with the real
 * platform resources. Keeps the shared chat UI renderable without a Context.
 */
val PlaceholderChatMessageStrings = ChatMessageStrings(
    firstChatMsg = "First time chat",
    rewardChatMsg = "Channel point redemption",
    redeemedChatMsg = { "Redeemed $it" },
    redeemedNoMsg = { userName, rewardTitle -> "$userName redeemed $rewardTitle" },
    replyMessage = { userName, _ -> "Replying to $userName: " },
    messageIdLabel = { it },
)

/**
 * Chat rows state shared by the chat screen and the message dialogs: the loaded collections, the
 * shared parse caches and the Compose-visible message lists.
 *
 * The host owns the backing list; this holder owns the snapshot copies the Compose lists actually
 * render. The lists render directly from the [messages] snapshot, while [generation] is bumped
 * whenever the parser has to re-run.
 */
class ChatState(
    val renderCache: ChatRenderCache = ChatRenderCache(),
) {
    var messageStyle by mutableStateOf(ChatMessageStyle())
    var options by mutableStateOf(
        ChatRenderOptions(
            strings = PlaceholderChatMessageStrings,
            cache = renderCache,
        )
    )
    /** Compose-visible row list of the dialog. */
    val messages = mutableStateListOf<ChatMessage>()

    /**
     * The selected row, mirrored by every list instance. Changing it selects the row across all
     * dialogs and the chat list itself.
     */
    var selectedMessage by mutableStateOf<ChatMessage?>(null)
        private set

    /**
     * Bumped whenever the loaded collections or preferences changed; passed into the Compose
     * lists as the parse cache key.
     */
    val generation: Int
        get() = generationState.value

    private val generationState = mutableStateOf(0)

    fun select(message: ChatMessage?) {
        selectedMessage = message
    }

    fun setMessages(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
    }

    fun appendMessage(message: ChatMessage) {
        messages.add(message)
    }

    fun prependMessages(list: List<ChatMessage>, limit: Int) {
        var index = 0
        while (messages.size < limit && index < list.size) {
            messages.add(index, list[index])
            index++
        }
    }

    fun removeMessages(size: Int) {
        repeat(size) {
            if (messages.isNotEmpty()) {
                messages.removeAt(0)
            }
        }
    }

    /** Re-runs the parser for every row, e.g. after emotes, badges or name paints loaded. */
    fun refresh() {
        generationState.value++
    }
}
