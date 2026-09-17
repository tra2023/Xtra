package com.github.andreyasadchy.xtra.ui.common

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.bindCollection
import com.github.andreyasadchy.xtra.ui.collections.collectionComposeView
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

class GamesAdapter(
    private val fragment: Fragment,
    private val selectTag: (Tag) -> Unit,
) : PagingDataAdapter<Game, GamesAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean =
            oldItem.slug == newItem.slug && oldItem.name == newItem.name &&
                    oldItem.boxArt == newItem.boxArt && oldItem.viewerCount == newItem.viewerCount &&
                    oldItem.broadcasterCount == newItem.broadcasterCount && oldItem.tags == newItem.tags
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
        fun bind(item: Game?) {
            val context = composeView.context
            composeView.bindCollection(item) { game ->
                GameCollectionRow(
                    name = game.name,
                    image = game.boxArt,
                    viewers = context.collectionCount(game.viewerCount, R.plurals.viewers),
                    broadcasters = context.collectionCount(
                        game.broadcasterCount.takeIf { context.prefs().getBoolean(C.UI_BROADCASTERS_COUNT, true) },
                        R.plurals.broadcasters,
                    ),
                    tags = game.tags.orEmpty().takeIf { context.prefs().getBoolean(C.UI_TAGS, true) }.orEmpty(),
                    tagLabel = { it.name.orEmpty() },
                    tagEnabled = { it.id != null },
                    onTagClick = selectTag,
                    onClick = {
                        fragment.findNavController().navigate(
                            if (context.prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = game.id,
                                    gameSlug = game.slug,
                                    gameName = game.name,
                                    boxArt = game.boxArt,
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = game.id,
                                    gameSlug = game.slug,
                                    gameName = game.name,
                                    boxArt = game.boxArt,
                                )
                            }
                        )
                    },
                )
            }
        }
    }
}
