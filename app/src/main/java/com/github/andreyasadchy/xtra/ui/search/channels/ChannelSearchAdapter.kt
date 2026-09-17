package com.github.andreyasadchy.xtra.ui.search.channels

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
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.collections.collectionName
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

class ChannelSearchAdapter(
    private val fragment: Fragment,
) : PagingDataAdapter<User, ChannelSearchAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.login == newItem.login && oldItem.name == newItem.name &&
                    oldItem.profileImage == newItem.profileImage && oldItem.followerCount == newItem.followerCount &&
                    oldItem.isLive == newItem.isLive
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
        fun bind(item: User?) {
            val context = composeView.context
            composeView.bindCollection(item) { user ->
                ChannelCollectionRow(
                    name = context.collectionName(user.name, user.login),
                    image = user.profileImage,
                    roundImage = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
                    details = listOfNotNull(context.collectionCount(user.followerCount, R.plurals.followers)),
                    labels = listOfNotNull(context.getString(R.string.live).takeIf { user.isLive == true }),
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
