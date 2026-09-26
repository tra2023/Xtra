package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

/**
 * Compose content of [MessageClickedDialog], sharing [MessageThreadScreen] with the reply
 * dialog: the inspected-user header, the filtered message list and the action buttons.
 *
 * Selection clicks come from [ChatState.selectedMessage], so the other open dialog and the
 * button rows below stay in sync without the old per-adapter selection bookkeeping.
 */
@Composable
fun MessageClickedScreenContent(
    chatState: ChatState?,
    messagingEnabled: Boolean,
    inspectedUser: User?,
    userFailed: Boolean,
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    onReply: (ChatMessage) -> Unit = {},
    onCopyMessage: (ChatMessage) -> Unit = {},
    onCopyClip: (ChatMessage) -> Unit = {},
    onCopyFullMsg: (ChatMessage) -> Unit = {},
    onViewProfile: (User) -> Unit = {},
    onImageClick: (ChatImage) -> Unit = {},
) {
    val state = chatState ?: return
    val listState = rememberLazyListState()
    val selected = state.selectedMessage
    // The old adapter pre-scrolled the list to the selected row on open.
    LaunchedEffect(state.messages.size) {
        state.messages.indexOf(selected).takeIf { it != -1 }?.let {
            listState.scrollToItem(it)
        }
    }
    val context = LocalContext.current
    val settings = remember(context) {
        AndroidXtraSettings(context.applicationContext.prefs(), context.applicationContext.tokenPrefs())
    }
    val nameDisplay = remember(settings) { settings.getString(C.UI_NAME_DISPLAY, "0") }
    val roundUserImage = remember(settings) { settings.getBoolean(C.UI_ROUND_USER_IMAGE, true) }
    val createdAtLabel: (String?) -> String = remember(settings) { { context.getString(R.string.created_at, it) } }
    val followedAtLabel: (String?) -> String = remember(settings) { { context.getString(R.string.followed_at, it) } }
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
                    modifier = Modifier.fillMaxSize().padding(),
                )
            }
        },
        messages = state.messages,
        options = state.options.copy(generation = state.generation),
        listState = listState,
        style = state.messageStyle,
        selectedMessage = selected,
        onMessageClick = state::select,
        onReplyClick = { message ->
            state.select(message)
        },
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
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = padding,
    )
}
