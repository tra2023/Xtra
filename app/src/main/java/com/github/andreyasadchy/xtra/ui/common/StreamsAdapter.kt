package com.github.andreyasadchy.xtra.ui.common

import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.browse.StreamCard
import com.github.andreyasadchy.xtra.ui.browse.StreamCardState
import com.github.andreyasadchy.xtra.ui.browse.streamCardState
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Clock
import kotlin.time.Instant

class StreamsAdapter(
    private val fragment: Fragment,
    private val selectTag: (String) -> Unit,
    private val showGame: Boolean = true,
) : PagingDataAdapter<Stream, StreamsAdapter.PagingViewHolder>(StreamDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder =
        PagingViewHolder(ComposeView(parent.context))

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: PagingViewHolder) {
        holder.bind(null)
        super.onViewRecycled(holder)
    }

    inner class PagingViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view) {
        private val host = StreamCardHost(view, fragment, selectTag, showGame, compact = false)

        fun bind(item: Stream?) {
            host.bind(item)
        }
    }
}

internal object StreamDiffCallback : DiffUtil.ItemCallback<Stream>() {
    override fun areItemsTheSame(oldItem: Stream, newItem: Stream): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Stream, newItem: Stream): Boolean =
        oldItem.viewerCount == newItem.viewerCount &&
                oldItem.gameId == newItem.gameId &&
                oldItem.gameSlug == newItem.gameSlug &&
                oldItem.gameName == newItem.gameName &&
                oldItem.title == newItem.title &&
                oldItem.channelId == newItem.channelId &&
                oldItem.channelLogin == newItem.channelLogin &&
                oldItem.channelName == newItem.channelName &&
                oldItem.channelImageURL == newItem.channelImageURL &&
                oldItem.thumbnailURL == newItem.thumbnailURL &&
                oldItem.createdAt == newItem.createdAt &&
                oldItem.tags == newItem.tags
}

internal class StreamCardHost(
    view: ComposeView,
    private val fragment: Fragment,
    private val selectTag: (String) -> Unit,
    private val showGame: Boolean,
    private val compact: Boolean,
) {
    private data class Binding(
        val stream: Stream,
        val card: StreamCardState,
        val theme: String,
        val roundUserImage: Boolean,
        val material3: Boolean,
        val reducedPadding: Boolean,
        val roundedCorners: String,
        val compactText: Boolean,
    )

    private var binding by mutableStateOf<Binding?>(null, neverEqualPolicy())

    init {
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        view.setContent {
            binding?.let { bound ->
                key(bound.stream) {
                    XtraTheme(
                        darkTheme = bound.theme != "2" && bound.theme != "5",
                        amoled = bound.theme == "1" || bound.theme == "6",
                        blue = bound.theme == "3",
                    ) {
                        StreamCard(
                            state = bound.card,
                            onStreamClick = { (fragment.activity as MainActivity).startStream(bound.stream) },
                            onChannelClick = { openChannel(bound.stream) },
                            onGameClick = { openGame(bound.stream) },
                            onTagClick = selectTag,
                            compact = compact,
                            roundUserImage = bound.roundUserImage,
                            cardMargin = if (!bound.material3) 0.dp else if (bound.reducedPadding) 4.dp else 8.dp,
                            cornerRadius = if (!bound.material3) 0.dp else when (bound.roundedCorners) {
                                "1" -> 9.dp
                                "2" -> 0.dp
                                else -> 12.dp
                            },
                            compactText = bound.material3 && bound.compactText,
                        )
                    }
                }
            }
        }
    }

    fun bind(item: Stream?) {
        if (item == null) {
            binding = null
            return
        }
        val context = fragment.requireContext()
        val prefs = context.prefs()
        val viewers = item.viewerCount?.let { count ->
            val formatted = TwitchApiHelper.formatCount(count, prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true))
            if (compact) formatted else context.resources.getQuantityString(R.plurals.viewers, count, formatted)
        }
        val uptime = if (prefs.getBoolean(C.UI_UPTIME, true)) {
            item.createdAt?.let { Instant.parseOrNull(it) }
                ?.takeIf { it.toEpochMilliseconds() > 0 }
                ?.let { Clock.System.now() - it }
                ?.takeIf { it.isPositive() }
                ?.let { DateUtils.formatElapsedTime(it.inWholeSeconds) }
                ?.let { if (compact) it else context.getString(R.string.uptime, it) }
        } else null
        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
            when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> prefs.getString(C.UI_THEME_DARK_ON, "0") ?: "0"
                else -> prefs.getString(C.UI_THEME_DARK_OFF, "2") ?: "2"
            }
        } else {
            prefs.getString(C.THEME, "0") ?: "0"
        }
        binding = Binding(
            stream = item,
            card = streamCardState(
                stream = item,
                nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
                showGame = showGame,
                showTags = prefs.getBoolean(C.UI_TAGS, true),
                viewers = viewers,
                uptime = uptime,
                nowMillis = System.currentTimeMillis(),
            ),
            theme = theme,
            roundUserImage = prefs.getBoolean(C.UI_ROUND_USER_IMAGE, true),
            material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true),
            reducedPadding = prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false),
            roundedCorners = prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0") ?: "0",
            compactText = prefs.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
        )
    }

    private fun openChannel(item: Stream) {
        fragment.findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = item.channelId,
                channelLogin = item.channelLogin,
                channelName = item.channelName,
                channelImage = item.channelImage,
                streamId = item.id,
            )
        )
    }

    private fun openGame(item: Stream) {
        fragment.findNavController().navigate(
            if (fragment.requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            }
        )
    }
}
