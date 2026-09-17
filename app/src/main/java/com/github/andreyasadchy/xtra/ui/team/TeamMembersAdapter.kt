package com.github.andreyasadchy.xtra.ui.team

import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.TeamMemberCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.bindCollection
import com.github.andreyasadchy.xtra.ui.collections.collectionComposeView
import com.github.andreyasadchy.xtra.ui.collections.collectionName
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Clock
import kotlin.time.Instant

class TeamMembersAdapter(
    private val fragment: Fragment,
    private val selectTag: (String) -> Unit,
) : PagingDataAdapter<Stream, TeamMembersAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Stream>() {
        override fun areItemsTheSame(oldItem: Stream, newItem: Stream): Boolean =
            oldItem.channelId == newItem.channelId

        override fun areContentsTheSame(oldItem: Stream, newItem: Stream): Boolean =
            oldItem.id == newItem.id && oldItem.channelLogin == newItem.channelLogin &&
                    oldItem.channelName == newItem.channelName && oldItem.channelImage == newItem.channelImage &&
                    oldItem.viewerCount == newItem.viewerCount && oldItem.gameId == newItem.gameId &&
                    oldItem.gameSlug == newItem.gameSlug && oldItem.gameName == newItem.gameName &&
                    oldItem.title == newItem.title && oldItem.createdAt == newItem.createdAt && oldItem.tags == newItem.tags
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder =
        PagingViewHolder(parent.collectionComposeView(), fragment)

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: PagingViewHolder) {
        holder.bind(null)
        super.onViewRecycled(holder)
    }

    inner class PagingViewHolder(
        private val composeView: ComposeView,
        private val fragment: Fragment,
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: Stream?) {
            val context = composeView.context
            composeView.bindCollection(item) { stream ->
                val live = stream.viewerCount != null
                val channelClick: () -> Unit = {
                    fragment.findNavController().navigate(
                        ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                            channelId = stream.channelId,
                            channelLogin = stream.channelLogin,
                            channelName = stream.channelName,
                            channelImage = stream.channelImage,
                            streamId = stream.id,
                        )
                    )
                }
                val uptime = if (live && context.prefs().getBoolean(C.UI_UPTIME, true)) {
                    stream.createdAt?.let {
                        Instant.parseOrNull(it)?.takeIf { time -> time.toEpochMilliseconds() > 0 }?.let { createdAt ->
                            (Clock.System.now() - createdAt).takeIf { duration -> duration.isPositive() }?.let { duration ->
                                DateUtils.formatElapsedTime(duration.inWholeSeconds)
                            }
                        }
                    }
                } else null
                TeamMemberCollectionRow(
                    name = context.collectionName(stream.channelName, stream.channelLogin),
                    image = stream.channelImage,
                    roundImage = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
                    title = stream.title?.trim()?.takeIf { live && it.isNotBlank() },
                    game = stream.gameName.takeIf { live },
                    viewers = stream.viewerCount?.let {
                        TwitchApiHelper.formatCount(it, context.prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true))
                    },
                    uptime = uptime,
                    tags = stream.tags.orEmpty().takeIf { live && context.prefs().getBoolean(C.UI_TAGS, true) }.orEmpty(),
                    onClick = {
                        if (live) {
                            (fragment.activity as MainActivity).startStream(stream)
                        } else {
                            channelClick()
                        }
                    },
                    onChannelClick = channelClick,
                    onGameClick = {
                        fragment.findNavController().navigate(
                            if (context.prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = stream.gameId,
                                    gameSlug = stream.gameSlug,
                                    gameName = stream.gameName,
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = stream.gameId,
                                    gameSlug = stream.gameSlug,
                                    gameName = stream.gameName,
                                )
                            }
                        )
                    },
                    onTagClick = selectTag,
                )
            }
        }
    }
}
