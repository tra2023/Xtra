package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.Arrangement
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
 * `ChatAdapter` pair. Messages are stacked from the end (`Arrangement.Bottom`), which is what
 * `LinearLayoutManager(stackFromEnd = true)` did, so a partially filled list sits at the bottom.
 *
 * The caller owns [listState], so it can keep the old "only auto scroll while the user is at the
 * bottom" behavior (`listState.canScrollForward` is the equivalent of
 * `computeVerticalScrollRange()`-percentage checks) and jump to the newest message.
 *
 * Items are not keyed: [ChatMessage] has no stable identity (ids are optional) and index keys
 * would not survive a message limit trim any better.
 */
@Composable
fun ChatList(
    messages: List<ChatMessage>,
    options: ChatRenderOptions,
    modifier: Modifier = Modifier,
    style: ChatMessageStyle = ChatMessageStyle(),
    listState: LazyListState = rememberLazyListState(),
    selectedMessage: ChatMessage? = null,
    onMessageClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onImageClick: ((ChatImage) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(count = messages.size) { index ->
            val message = messages.getOrNull(index)
            if (message != null) {
                ChatMessageItem(
                    message = message,
                    options = options,
                    style = style,
                    selected = message === selectedMessage,
                    onMessageClick = onMessageClick,
                    onReplyClick = onReplyClick,
                    onImageClick = onImageClick,
                )
            }
        }
    }
}
