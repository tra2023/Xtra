package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.ui.common.rememberNestedScrollModifier
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Compose content of the reply dialog, sharing [MessageThreadScreen] with the message dialog:
 * the reply-thread rows and the reply/copy buttons. Platform strings arrive through [labels].
 */
@Composable
fun ReplyClickedScreenContent(
    chatState: ChatState?,
    messagingEnabled: Boolean,
    labels: ButtonLabelContext,
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    onReply: (ChatMessage) -> Unit = {},
    onCopyMessage: (ChatMessage) -> Unit = {},
    onCopyClip: (ChatMessage) -> Unit = {},
    onCopyFullMsg: (ChatMessage) -> Unit = {},
    onImageClick: (ChatImage) -> Unit = {},
) {
    val state = chatState ?: return
    val listState = rememberLazyListState()
    val selected = state.selectedMessage
    val anchor = remember(state) { state.selectedMessage }
    val messages = remember(anchor, state.messages.size, state.generation) {
        filterReplyDialogMessages(state.messages, anchor)
    }
    LaunchedEffect(messages.size) {
        messages.indexOf(selected).takeIf { it != -1 }?.let {
            // The chat list is reversed (newest first), so the layout index is mirrored.
            listState.scrollToItem(messages.lastIndex - it)
        }
    }
    val settings = LocalXtraSettings.current
    val allowCopyFullMsg = remember(settings) { settings.getBoolean(C.DEBUG_CHAT_FULL_MSG, false) }
    MessageThreadScreen(
        header = null,
        messages = messages,
        options = state.options,
        generation = state.generation,
        listState = listState,
        style = state.messageStyle,
        selectedMessage = selected,
        onMessageClick = state::select,
        onImageClick = onImageClick,
        buttons = {
            messageClickedButtons(
                context = labels,
                messagingEnabled = messagingEnabled,
                selected = selected,
                userFailed = false,
                allowCopyFullMsg = allowCopyFullMsg,
                debugFullMsgButton = labels.copyFullMsg,
                onReply = onReply,
                onCopyMessage = onCopyMessage,
                onCopyClip = onCopyClip,
                onCopyFullMsg = onCopyFullMsg,
                onViewProfile = {},
            ).forEach { (label, onClick) ->
                MessageThreadButton(
                    label = label,
                    onClick = onClick,
                    contentPadding = padding,
                )
            }
        },
        modifier = modifier.then(rememberNestedScrollModifier()),
        contentPadding = padding,
    )
}
