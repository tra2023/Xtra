package com.github.andreyasadchy.xtra.ui.search

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.ui.collections.RecentSearchCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.bindCollection
import com.github.andreyasadchy.xtra.ui.collections.collectionComposeView

class RecentSearchAdapter(
    private val select: (RecentSearch) -> Unit,
    private val delete: (RecentSearch) -> Unit,
) : ListAdapter<RecentSearch, RecentSearchAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<RecentSearch>() {
        override fun areItemsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return oldItem.query == newItem.query
        }

        override fun areContentsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return true
        }
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(parent.collectionComposeView())

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.bind(null)
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(
        private val composeView: ComposeView,
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: RecentSearch?) {
            composeView.bindCollection(item) { search ->
                RecentSearchCollectionRow(
                    query = search.query,
                    historyIcon = painterResource(R.drawable.baseline_history_black_24),
                    deleteIcon = painterResource(R.drawable.baseline_delete_black_24),
                    deleteLabel = stringResource(R.string.delete),
                    onClick = { select(search) },
                    onDelete = { delete(search) },
                )
            }
        }
    }
}
