package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.rememberNestedScrollModifier
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C

/**
 * Compose content of the message dialog, sharing [MessageThreadScreen] with the reply
 * dialog: the inspected-user header, the filtered message list and the action buttons.
 *
 * Selection clicks come from [ChatState.selectedMessage], so the other open dialog and the
 * button rows below stay in sync without per-adapter selection bookkeeping.
 *
 * Platform strings and date formatting arrive from the host: [createdAtLabel] and
 * [followedAtLabel] render the header timestamps, [labels] the action buttons.
 */
@Composable
fun MessageClickedScreenContent(
    chatState: ChatState?,
    messagingEnabled: Boolean,
    inspectedUser: User?,
    userFailed: Boolean,
    labels: ButtonLabelContext,
    createdAtLabel: (String?) -> String,
    followedAtLabel: (String?) -> String,
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    onReply: (ChatMessage) -> Unit = {},
    onCopyMessage: (ChatMessage) -> Unit = {},
    onCopyClip: (ChatMessage) -> Unit = {},
    onCopyFullMsg: (ChatMessage) -> Unit = {},
    onViewProfile: (User) -> Unit = {},
    onImageClick: (ChatImage) -> Unit = {},
    onReplyThread: (ChatMessage) -> Unit = {},
) {
    val state = chatState ?: return
    val listState = rememberLazyListState()
    val selected = state.selectedMessage
    // The clicked message anchors the list; later selections do not change the filtered set.
    val anchor = remember(state) { state.selectedMessage }
    val messages = remember(anchor, state.messages.size, state.generation) {
        filterMessageDialogMessages(state.messages, anchor)
    }
    // The old adapter pre-scrolled the list to the selected row on open.
    LaunchedEffect(messages.size) {
        messages.indexOf(selected).takeIf { it != -1 }?.let {
            listState.scrollToItem(it)
        }
    }
    val settings = LocalXtraSettings.current
    val nameDisplay = remember(settings) { settings.getString(C.UI_NAME_DISPLAY, "0") }
    val roundUserImage = remember(settings) { settings.getBoolean(C.UI_ROUND_USER_IMAGE, true) }
    val allowCopyFullMsg = remember(settings) { settings.getBoolean(C.DEBUG_CHAT_FULL_MSG, false) }
    MessageThreadScreen(
        header = {
            if (inspectedUser != null) {
                MessageClickedHeader(
                    user = inspectedUser,
                    userFailed = userFailed,
                    nameDisplay = nameDisplay,
                    roundUserImage = roundUserImage,
                    createdAtLabel = createdAtLabel,
                    followedAtLabel = followedAtLabel,
                    onViewProfile = onViewProfile,
                    modifier = Modifier.fillMaxWidth().padding(),
                )
            }
        },
        messages = messages,
        options = state.options.copy(generation = state.generation),
        listState = listState,
        style = state.messageStyle,
        selectedMessage = selected,
        onMessageClick = state::select,
        onReplyClick = onReplyThread,
        onImageClick = onImageClick,
        buttons = {
            messageClickedButtons(
                context = labels,
                messagingEnabled = messagingEnabled,
                selected = selected,
                userFailed = userFailed,
                allowCopyFullMsg = allowCopyFullMsg,
                debugFullMsgButton = labels.copyFullMsg,
                onReply = onReply,
                onCopyMessage = onCopyMessage,
                onCopyClip = onCopyClip,
                onCopyFullMsg = onCopyFullMsg,
                onViewProfile = { inspectedUser?.let(onViewProfile) },
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
