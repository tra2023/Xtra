package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

/**
 * Compose content of [ReplyClickedDialog], sharing [MessageThreadScreen] with the message dialog:
 * the reply-thread rows and the reply/copy buttons.
 */
@Composable
fun ReplyClickedScreenContent(
    chatState: ChatState?,
    messagingEnabled: Boolean,
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
            listState.scrollToItem(it)
        }
    }
    val context = LocalContext.current
    val settings = remember(context) {
        AndroidXtraSettings(context.applicationContext.prefs(), context.applicationContext.tokenPrefs())
    }
    MessageThreadScreen(
        header = null,
        messages = messages,
        options = state.options.copy(generation = state.generation),
        listState = listState,
        style = state.messageStyle,
        selectedMessage = selected,
        onMessageClick = state::select,
        onImageClick = onImageClick,
        buttons = {
            val labels = remember(context) {
                ButtonLabelContext(
                    reply = context.getString(R.string.reply),
                    copyMessage = context.getString(R.string.copy_message),
                    copyClip = context.getString(R.string.copy_clip),
                    copyFullMsg = context.getString(R.string.copy_fullmsg),
                    viewProfile = context.getString(R.string.view_profile),
                )
            }
            val allowCopyFullMsg = remember(settings) { settings.getBoolean(C.DEBUG_CHAT_FULL_MSG, false) }
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
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = padding,
    )
}
