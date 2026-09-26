package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.chat.ChatRenderOptions

/**
 * Shared screen of the message/reply dialogs, matching `dialog_chat_message_click.xml`: an
 * optional header (the inspected user in the message dialog), a chat list and the dialog action
 * buttons (reply / copy / view profile).
 *
 * The old dialogs were `BottomSheetDialogFragment`s whose `RecyclerView` rows were `TextView`s.
 * This keeps the same outer 200dp list height, the same button padding and the same selection
 * behaviour; only the rendering moved to [ChatList].
 */
@Composable
fun MessageThreadScreen(
    header: @Composable (() -> Unit)?,
    messages: List<ChatMessage>,
    options: ChatRenderOptions,
    modifier: Modifier = Modifier,
    style: ChatMessageStyle = ChatMessageStyle(),
    listState: LazyListState = rememberLazyListState(),
    selectedMessage: ChatMessage? = null,
    onMessageClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onImageClick: ((ChatImage) -> Unit)? = null,
    listHeight: Dp = 200.dp,
    contentPadding: Dp = 8.dp,
    buttons: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        header?.invoke()
        ChatList(
            messages = messages,
            options = options,
            style = style,
            listState = listState,
            selectedMessage = selectedMessage,
            onMessageClick = onMessageClick,
            onReplyClick = onReplyClick,
            onImageClick = onImageClick,
            modifier = Modifier
                .fillMaxSize()
                .height(listHeight)
                .padding(horizontal = contentPadding),
        )
        buttons?.invoke()
    }
}

/** One full-width dialog action, matching `?attr/bottomSheetButtonStyle`. */
@Composable
fun MessageThreadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding),
    ) {
        Text(label)
    }
}
