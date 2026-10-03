package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.chat.ChatRenderOptions

/**
 * The scrolling chat message list, the Compose replacement for the `RecyclerView` +
 * `ChatAdapter` pair.
 *
 * The list is reversed ([LazyColumn] `reverseLayout`): the newest message ([messages.last]) is at
 * layout index 0 and sits at the bottom, exactly like `LinearLayoutManager(stackFromEnd = true)`
 * did. This anchors the list to the end on first composition and keeps new messages at the
 * bottom, so "jump to newest" is `scrollToItem(0)` and the scroll-down indicator is driven by
 * `canScrollBackward`. It also avoids shifting every visible index when the message limit trims
 * the oldest rows.
 *
 * The caller owns [listState], so it can keep the old "only auto scroll while the user is at the
 * bottom" behavior and jump to the newest message.
 *
 * Items are keyed by [ChatMessage.key]. A stable key lets the list keep the existing rows when
 * messages are appended or trimmed from the front, instead of re-composing and re-formatting the
 * whole visible list on every update (which is what the granular `notifyItem*` calls did before).
 */
@Composable
fun ChatList(
    messages: List<ChatMessage>,
    options: ChatRenderOptions,
    modifier: Modifier = Modifier,
    style: ChatMessageStyle = ChatMessageStyle(),
    generation: Int = options.generation,
    listState: LazyListState = rememberLazyListState(),
    selectedMessage: ChatMessage? = null,
    onMessageClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onImageClick: ((ChatImage) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
    ) {
        items(
            count = messages.size,
            key = { index -> messages[messages.lastIndex - index].key },
        ) { index ->
            val message = messages.getOrNull(messages.lastIndex - index)
            if (message != null) {
                ChatMessageItem(
                    message = message,
                    options = options,
                    style = style,
                    generation = generation,
                    selected = message === selectedMessage,
                    onMessageClick = onMessageClick,
                    onReplyClick = onReplyClick,
                    onImageClick = onImageClick,
                )
            }
        }
    }
}
