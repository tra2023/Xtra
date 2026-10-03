package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.runtime.getValue
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
    /**
     * Compose-visible row list of the dialog.
     *
     * Kept as an immutable snapshot (`mutableStateOf`) rather than a `SnapshotStateList`: reading a
     * list element inside a row's composition would subscribe that row to every list mutation, so
     * each appended/trimmed message re-composed and re-formatted every visible row. With a plain
     * list only the call site that reads [messages] re-composes.
     */
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

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

    fun replaceMessages(list: List<ChatMessage>) {
        renderCache.messageContents.clear()
        messages = list.toList()
    }

    fun appendMessage(message: ChatMessage) {
        messages = messages + message
    }

    fun prependMessages(list: List<ChatMessage>, limit: Int) {
        val room = (limit - messages.size).coerceAtLeast(0)
        if (room > 0) {
            messages = list.take(room) + messages
        }
    }

    fun removeMessages(size: Int) {
        if (size > 0) {
            val dropped = messages.take(size)
            messages = messages.drop(size)
            // Keep the formatted-content cache bounded to the message window.
            dropped.forEach { renderCache.messageContents.remove(it.key) }
        }
    }

    /** Re-runs the parser for every row, e.g. after emotes, badges or name paints loaded. */
    fun refresh() {
        renderCache.messageContents.clear()
        generationState.value++
    }
}
