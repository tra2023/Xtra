package com.github.andreyasadchy.xtra.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.ui.chat.ChatViewModel.Companion.ChatViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * One page of the chat emote panel, as Compose: a grid of 32dp emote images
 * rendered by the shared [EmoteGrid]. Page 0 mixes the recently used emotes with
 * the personal and third-party sets, page 1 is the user's own emotes and the
 * later pages are the third-party sets.
 *
 * The list is recomputed from [ChatViewModel]'s collections whenever one of its
 * update signals fires, exactly as the RecyclerView adapter used to.
 */
class EmotesFragment : Fragment() {

    private val viewModel by viewModels<ChatViewModel>(ownerProducer = { requireParentFragment() }, factoryProducer = { ChatViewModelFactory })
    private var recentEmotes = emptyList<RecentEmote>()
    private var emotes by mutableStateOf<List<Emote>>(emptyList())

    private val position: Int
        get() = requireArguments().getInt(KEY_POSITION)

    private val emoteQuality: String
        get() = requireContext().prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    EmoteGrid(
                        emotes = emotes,
                        imageUrl = ::emoteUrl,
                        onClick = { (parentFragment as? ChatFragment)?.appendEmote(it) },
                        thirdPartyUserAgent = "Xtra/" + BuildConfig.VERSION_NAME,
                    )
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentEmotes.collectLatest {
                    if (it.isNotEmpty()) {
                        recentEmotes = it
                        updateList()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userEmotesUpdated.collectLatest { updateList() }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.thirdPartyEmotesUpdated.collectLatest { updateList() }
            }
        }
        updateList()
    }

    private fun updateList() {
        emotes = when (position) {
            0 -> {
                if (recentEmotes.isEmpty()) {
                    emptyList()
                } else {
                    val list = userEmotes() + personalEmotes() + thirdPartyEmotes()
                    recentEmotes.mapNotNull { recent -> list.find { it.name == recent.name } }
                }
            }
            1 -> userEmotes()
            else -> personalEmotes() + thirdPartyEmotes()
        }
    }

    private fun userEmotes(): List<Emote> = synchronized(viewModel.userEmotes) { viewModel.userEmotes.toList() }

    private fun thirdPartyEmotes(): List<Emote> = synchronized(viewModel.thirdPartyEmotes) { viewModel.thirdPartyEmotes.toList() }

    private fun personalEmotes(): List<Emote> = viewModel.userSTVEmoteSetId?.let { setId ->
        synchronized(viewModel.personalEmoteSets) { viewModel.personalEmoteSets[setId] }
    } ?: emptyList()

    private fun emoteUrl(emote: Emote): String? = when (emoteQuality) {
        "4" -> emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x
        "3" -> emote.url3x ?: emote.url2x ?: emote.url1x
        "2" -> emote.url2x ?: emote.url1x
        else -> emote.url1x
    }

    companion object {
        private const val KEY_POSITION = "position"

        fun newInstance(position: Int): EmotesFragment {
            return EmotesFragment().apply {
                arguments = Bundle().apply {
                    putInt(KEY_POSITION, position)
                }
            }
        }
    }
}
