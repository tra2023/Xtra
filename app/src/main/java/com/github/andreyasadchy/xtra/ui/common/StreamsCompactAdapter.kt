package com.github.andreyasadchy.xtra.ui.common

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.ui.Stream

class StreamsCompactAdapter(
    private val fragment: Fragment,
    private val selectTag: (String) -> Unit,
    private val showGame: Boolean = true,
) : PagingDataAdapter<Stream, StreamsCompactAdapter.PagingViewHolder>(StreamDiffCallback) {

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
        private val host = StreamCardHost(view, fragment, selectTag, showGame, compact = true)

        fun bind(item: Stream?) {
            host.bind(item)
        }
    }
}
