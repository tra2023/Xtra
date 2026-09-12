package com.github.andreyasadchy.xtra.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.databinding.FragmentEmotesListItemBinding
import com.github.andreyasadchy.xtra.model.chat.Emote

class EmotesAdapter(
    private val fragment: Fragment,
    private val clickListener: (Emote) -> Unit,
    private val emoteQuality: String,
) : ListAdapter<Emote, EmotesAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<Emote>() {
        override fun areItemsTheSame(oldItem: Emote, newItem: Emote): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Emote, newItem: Emote): Boolean {
            return true
        }
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FragmentEmotesListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: FragmentEmotesListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Emote?) {
            with(binding) {
                if (item != null) {
                    fragment.requireContext().imageLoader.enqueue(
                        ImageRequest.Builder(fragment.requireContext()).apply {
                            data(
                                when (emoteQuality) {
                                    "4" -> item.url4x ?: item.url3x ?: item.url2x ?: item.url1x
                                    "3" -> item.url3x ?: item.url2x ?: item.url1x
                                    "2" -> item.url2x ?: item.url1x
                                    else -> item.url1x
                                }
                            )
                            if (item.thirdParty) {
                                httpHeaders(NetworkHeaders.Builder().apply {
                                    add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME)
                                }.build())
                            }
                            crossfade(true)
                            target(root)
                        }.build()
                    )
                    root.setOnClickListener { clickListener(item) }
                }
            }
        }
    }
}