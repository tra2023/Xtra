package com.github.andreyasadchy.xtra.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ReplyClickedDialog : BottomSheetDialogFragment() {

    interface OnButtonClickListener {
        fun onCreateReplyClickedChatState(): ChatState?
        fun onReplyClicked(replyId: String?, userLogin: String?, userName: String?, message: String?)
        fun onCopyMessageClicked(message: String)
    }

    companion object {
        private const val KEY_MESSAGING = "messaging"

        fun newInstance(messagingEnabled: Boolean): ReplyClickedDialog {
            return ReplyClickedDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_MESSAGING, messagingEnabled)
                }
            }
        }
    }

    private lateinit var listener: OnButtonClickListener
    private var chatState: ChatState? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnButtonClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val state = listener.onCreateReplyClickedChatState().also { chatState = it }
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
                    ReplyClickedScreenContent(
                        chatState = state,
                        messagingEnabled = messagingEnabled,
                        padding = padding,
                        onReply = ::onReplyButton,
                        onCopyMessage = ::onCopyMessageButton,
                        onCopyClip = ::onCopyClipButton,
                        onCopyFullMsg = ::onCopyFullMsgButton,
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
}
