package com.github.andreyasadchy.xtra.ui.chat

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.decode.BitmapFactoryDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.EmoteCard
import com.github.andreyasadchy.xtra.ui.chat.ImageClickedViewModel.Companion.ImageClickedViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class ImageClickedDialog : BottomSheetDialogFragment(), IntegrityDialog.Listener {

    companion object {
        private const val IMAGE_URL = "image_url"
        private const val IMAGE_NAME = "image_name"
        private const val IMAGE_FORMAT = "image_format"
        private const val IMAGE_ANIMATED = "image_animated"
        private const val IMAGE_SOURCE = "image_source"
        private const val IMAGE_THIRD_PARTY = "image_third_party"
        private const val EMOTE_ID = "emote_id"

        fun newInstance(url: String?, name: String?, format: String?, isAnimated: Boolean?, source: Int?, thirdParty: Boolean?, emoteId: String?): ImageClickedDialog {
            return ImageClickedDialog().apply {
                arguments = Bundle().apply {
                    putString(IMAGE_URL, url)
                    putString(IMAGE_NAME, name)
                    putString(IMAGE_FORMAT, format)
                    putBoolean(IMAGE_ANIMATED, isAnimated == true)
                    putInt(IMAGE_SOURCE, source ?: -1)
                    putBoolean(IMAGE_THIRD_PARTY, thirdParty == true)
                    putString(EMOTE_ID, emoteId)
                }
            }
        }
    }

    private val viewModel: ImageClickedViewModel by viewModels { ImageClickedViewModelFactory }
    private var imageSource by mutableStateOf<String?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val args = requireArguments()
        val prefs = requireContext().prefs()
        val animated = args.getBoolean(IMAGE_ANIMATED) && prefs.getBoolean(C.ANIMATED_EMOTES, true)
        val request = ImageRequest.Builder(requireContext()).apply {
            data(args.getString(IMAGE_URL))
            memoryCacheKeyExtra("image_clicked_animation", animated.toString())
            if (animated) {
                decoderFactory(AnimatedImageDecoder.Factory())
            } else {
                decoderFactory(BitmapFactoryDecoder.Factory())
            }
            if (args.getBoolean(IMAGE_THIRD_PARTY)) {
                httpHeaders(NetworkHeaders.Builder().apply {
                    add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME)
                }.build())
            }
        }.build()
        val imageName = args.getString(IMAGE_NAME)
        imageSource = sourceLabel(viewModel.emoteCard.value)
        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> prefs.getString(C.UI_THEME_DARK_ON, "0")
                else -> prefs.getString(C.UI_THEME_DARK_OFF, "2")
            }
        } else {
            prefs.getString(C.THEME, "0")
        }
        val padding = requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogPadding)).let {
            val value = it.getDimension(0, 8f * resources.displayMetrics.density) / resources.displayMetrics.density
            it.recycle()
            value.dp
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                    ImageClickedDialogContent(
                        model = request,
                        imageName = imageName,
                        imageSource = imageSource,
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        contentPadding = padding,
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
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.integrity.collect {
                        (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                    }
                }
                launch {
                    viewModel.emoteCard.collect {
                        imageSource = sourceLabel(it)
                    }
                }
                loadEmoteCard()
            }
        }
    }

    private fun sourceLabel(emoteCard: EmoteCard?): String? {
        if (emoteCard != null) {
            val name = if (emoteCard.channelLogin != null && !emoteCard.channelLogin.equals(emoteCard.channelName, true)) {
                when (requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                    "0" -> "${emoteCard.channelName}(${emoteCard.channelLogin})"
                    "1" -> emoteCard.channelName
                    else -> emoteCard.channelLogin
                }
            } else {
                emoteCard.channelName
            }
            when (emoteCard.type) {
                "SUBSCRIPTIONS" -> return getString(R.string.channel_sub_emote, name,
                    when (emoteCard.subTier) {
                        "TIER_1" -> "1"
                        "TIER_2" -> "2"
                        "TIER_3" -> "3"
                        else -> emoteCard.subTier
                    }
                )
                "FOLLOWER" -> return getString(R.string.channel_follower_emote, name)
                "BITS_BADGE_TIERS" -> return getString(R.string.bits_reward_emote, emoteCard.bitThreshold)
            }
        }
        return when (requireArguments().getInt(IMAGE_SOURCE, -1)) {
            Emote.PERSONAL_STV -> getString(R.string.personal_stv_emote)
            Emote.CHANNEL_STV -> getString(R.string.channel_stv_emote)
            Emote.CHANNEL_BTTV -> getString(R.string.channel_bttv_emote)
            Emote.CHANNEL_FFZ -> getString(R.string.channel_ffz_emote)
            Emote.GLOBAL_STV -> getString(R.string.global_stv_emote)
            Emote.GLOBAL_BTTV -> getString(R.string.global_bttv_emote)
            Emote.GLOBAL_FFZ -> getString(R.string.global_ffz_emote)
            else -> null
        }
    }

    private fun loadEmoteCard() {
        requireArguments().getString(EMOTE_ID)?.let {
            viewModel.loadEmoteCard(
                it,
                TwitchApiHelper.getGQLHeaders(requireContext()),
                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == "refresh") {
            loadEmoteCard()
        }
    }
}
