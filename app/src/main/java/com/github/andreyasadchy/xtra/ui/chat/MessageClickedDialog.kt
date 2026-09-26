package com.github.andreyasadchy.xtra.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.chat.MessageClickedViewModel.Companion.MessageClickedViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageClickedDialog : BottomSheetDialogFragment(), IntegrityDialog.Listener {

    interface OnButtonClickListener {
        fun onCreateMessageClickedChatState(): ChatState?
        fun onReplyClicked(replyId: String?, userLogin: String?, userName: String?, message: String?)
        fun onCopyMessageClicked(message: String)
        fun onViewProfileClicked(id: String?, login: String?, name: String?, channelImage: String?)
        fun onReplyThreadClicked(message: ChatMessage)
    }

    companion object {
        private const val KEY_MESSAGING = "messaging"
        private const val KEY_CHANNEL_ID = "channelId"
        private val savedUsers = mutableListOf<Pair<User, String?>>()

        fun newInstance(messagingEnabled: Boolean, channelId: String?): MessageClickedDialog {
            return MessageClickedDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_MESSAGING, messagingEnabled)
                    putString(KEY_CHANNEL_ID, channelId)
                }
            }
        }
    }

    private val viewModel: MessageClickedViewModel by viewModels { MessageClickedViewModelFactory }

    private lateinit var listener: OnButtonClickListener
    private var chatState: ChatState? = null
    private var inspectedUser by mutableStateOf<User?>(null)
    private var userFailed by mutableStateOf(false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnButtonClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val state = listener.onCreateMessageClickedChatState().also { chatState = it }
        state?.selectedMessage?.let { selected ->
            val targetId = requireArguments().getString(KEY_CHANNEL_ID)
            val saved = selected.userId?.let { id -> savedUsers.find { it.first.id == id && it.second == targetId } }
            if (saved != null) {
                inspectedUser = saved.first
                userFailed = false
            } else {
                loadUser(selected)
            }
        }
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        val messagingEnabled = requireArguments().getBoolean(KEY_MESSAGING)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    MessageClickedScreenContent(
                        chatState = state,
                        messagingEnabled = messagingEnabled,
                        inspectedUser = inspectedUser,
                        userFailed = userFailed,
                        padding = padding,
                        onReply = ::onReplyButton,
                        onCopyMessage = ::onCopyMessageButton,
                        onCopyClip = ::onCopyClipButton,
                        onCopyFullMsg = ::onCopyFullMsgButton,
                        onViewProfile = ::onViewProfileButton,
                        onReplyThread = { message -> listener.onReplyThreadClicked(message) },
                        onImageClick = ::onImageClick,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.integrity.collect {
                        (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                    }
                }
                launch {
                    viewModel.user.collectLatest { pair ->
                        if (pair != null) {
                            val user = pair.first
                            val error = pair.second
                            if (user != null) {
                                savedUsers.add(Pair(user, requireArguments().getString(KEY_CHANNEL_ID)))
                                inspectedUser = user
                                userFailed = false
                                viewModel.user.value = Pair(null, false)
                            } else if (error == true) {
                                userFailed = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadUser(selected: ChatMessage) {
        val targetId = requireArguments().getString(KEY_CHANNEL_ID)
        viewModel.loadUser(
            channelId = selected.userId,
            channelLogin = selected.userLogin,
            targetId = if (selected.userId != targetId) targetId else null,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
            enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    private fun onReplyButton(message: ChatMessage) {
        listener.onReplyClicked(message.id, message.userLogin, message.userName, message.message)
        dismiss()
    }

    private fun onCopyMessageButton(message: ChatMessage) {
        listener.onCopyMessageClicked(message.message.orEmpty())
        dismiss()
    }

    private fun onCopyClipButton(message: ChatMessage) {
        clipboard()?.setPrimaryClip(ClipData.newPlainText("label", message.message))
        dismiss()
    }

    private fun onCopyFullMsgButton(message: ChatMessage) {
        clipboard()?.setPrimaryClip(ClipData.newPlainText("label", message.fullMsg))
        dismiss()
    }

    private fun onViewProfileButton(user: User) {
        listener.onViewProfileClicked(user.id, user.login, user.name, user.profileImage)
        dismiss()
    }

    private fun onImageClick(image: ChatImage) {
        val click = image.click
        ImageClickedDialog.newInstance(
            image.url4x ?: image.url3x ?: image.url2x ?: image.url1x,
            click?.name,
            click?.format,
            click?.isAnimated ?: image.isAnimated,
            click?.source,
            click?.thirdParty ?: image.thirdParty,
            click?.emoteId,
        ).show(childFragmentManager, "imageDialog")
    }

    private fun clipboard(): ClipboardManager? = getSystemService(requireContext(), ClipboardManager::class.java)

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == "refresh") {
            chatState?.selectedMessage?.let { loadUser(it) }
        }
    }
}
