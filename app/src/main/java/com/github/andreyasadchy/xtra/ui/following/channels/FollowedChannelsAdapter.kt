package com.github.andreyasadchy.xtra.ui.following.channels

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.ChannelCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.bindCollection
import com.github.andreyasadchy.xtra.ui.collections.collectionComposeView
import com.github.andreyasadchy.xtra.ui.collections.collectionFollowLabels
import com.github.andreyasadchy.xtra.ui.collections.collectionName
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Instant

class FollowedChannelsAdapter(
    private val fragment: Fragment,
) : PagingDataAdapter<User, FollowedChannelsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.login == newItem.login && oldItem.name == newItem.name &&
                    oldItem.profileImage == newItem.profileImage && oldItem.lastBroadcast == newItem.lastBroadcast &&
                    oldItem.followedAt == newItem.followedAt && oldItem.accountFollow == newItem.accountFollow &&
                    oldItem.localFollow == newItem.localFollow
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

    class PagingViewHolder(
        private val composeView: ComposeView,
        private val fragment: Fragment,
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: User?) {
            val context = composeView.context
            composeView.bindCollection(item) { user ->
                fun date(value: String?, label: Int): String? = value?.let {
                    Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 }?.let { time ->
                        context.getString(label, formatChatDate(time))
                    }
                }
                ChannelCollectionRow(
                    name = context.collectionName(user.name, user.login),
                    image = user.profileImage,
                    roundImage = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
                    details = listOfNotNull(
                        date(user.lastBroadcast, R.string.last_broadcast_date),
                        date(user.followedAt, R.string.followed_at),
                    ),
                    labels = context.collectionFollowLabels(user.accountFollow, user.localFollow),
                    onClick = {
                        fragment.findNavController().navigate(
                            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                channelId = user.id,
                                channelLogin = user.login,
                                channelName = user.name,
                                channelImage = user.profileImage,
                            )
                        )
                    },
                )
            }
        }
    }
}
