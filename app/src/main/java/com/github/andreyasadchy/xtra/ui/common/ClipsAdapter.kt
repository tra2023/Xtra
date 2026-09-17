package com.github.andreyasadchy.xtra.ui.common

import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.media.MediaRow
import com.github.andreyasadchy.xtra.ui.media.MediaRowAction
import com.github.andreyasadchy.xtra.ui.media.MediaRowData
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Instant

class ClipsAdapter(
    private val fragment: Fragment,
    private val showDownloadDialog: (Clip) -> Unit,
    private val showGame: Boolean = true,
    private val showChannel: Boolean = true,
) : PagingDataAdapter<Clip, ClipsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Clip>() {
        override fun areItemsTheSame(oldItem: Clip, newItem: Clip): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Clip, newItem: Clip): Boolean =
            oldItem.viewCount == newItem.viewCount &&
                    oldItem.title == newItem.title &&
                    oldItem.thumbnailURL == newItem.thumbnailURL &&
                    oldItem.durationSeconds == newItem.durationSeconds &&
                    oldItem.createdAt == newItem.createdAt &&
                    oldItem.channelId == newItem.channelId &&
                    oldItem.channelLogin == newItem.channelLogin &&
                    oldItem.channelName == newItem.channelName &&
                    oldItem.channelImageURL == newItem.channelImageURL &&
                    oldItem.gameId == newItem.gameId &&
                    oldItem.gameSlug == newItem.gameSlug &&
                    oldItem.gameName == newItem.gameName &&
                    oldItem.videoId == newItem.videoId &&
                    oldItem.videoOffsetSeconds == newItem.videoOffsetSeconds &&
                    oldItem.videoCreatedAt == newItem.videoCreatedAt &&
                    oldItem.videoAnimatedPreviewURL == newItem.videoAnimatedPreviewURL
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder =
        PagingViewHolder(ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        })

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: PagingViewHolder) {
        holder.bind(null)
        super.onViewRecycled(holder)
    }

    inner class PagingViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view) {
        private var item: Clip? = null
        private var bindingVersion by mutableIntStateOf(0)

        init {
            view.setContent {
                key(bindingVersion) {
                    val clip = item
                    if (clip != null) {
                        val context = view.context
                        val prefs = context.prefs()
                        val configuration = LocalConfiguration.current
                        val theme = if (prefs.getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
                            if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                                prefs.getString(C.UI_THEME_DARK_ON, "0")
                            } else {
                                prefs.getString(C.UI_THEME_DARK_OFF, "2")
                            }
                        } else {
                            prefs.getString(C.THEME, "0")
                        }
                        val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
                        val channelLogin = clip.channelLogin
                        val channelName = clip.channelName?.let { name ->
                            if (channelLogin != null && !channelLogin.equals(name, true)) {
                                when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                    "0" -> "$name($channelLogin)"
                                    "1" -> name
                                    else -> channelLogin
                                }
                            } else {
                                name
                            }
                        }
                        val download = { showDownloadDialog(clip) }
                        XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                            MediaRow(
                                data = MediaRowData(
                                    thumbnail = clip.thumbnail,
                                    title = clip.title?.takeIf { it.isNotBlank() }?.trim(),
                                    channelName = channelName.takeIf { showChannel },
                                    channelImage = clip.channelImage.takeIf { showChannel },
                                    gameName = clip.gameName.takeIf { showGame },
                                    date = clip.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { formatChatDate(it) },
                                    views = clip.viewCount?.let { count ->
                                        context.resources.getQuantityString(R.plurals.views, count, TwitchApiHelper.formatCount(count, prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
                                    },
                                    duration = clip.durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
                                ),
                                optionsText = context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                                downloadText = context.getString(R.string.download),
                                actions = listOf(
                                    MediaRowAction(context.getString(R.string.download), download),
                                    MediaRowAction(context.getString(R.string.share)) {
                                        context.startActivity(Intent.createChooser(Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/${clip.channelLogin}/clip/${clip.id}")
                                            clip.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                                            type = "text/plain"
                                        }, null))
                                    },
                                ),
                                onOpen = { (fragment.activity as MainActivity).startClip(clip) },
                                onDownload = download,
                                onChannelClick = {
                                    fragment.findNavController().navigate(
                                        ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                            channelId = clip.channelId,
                                            channelLogin = clip.channelLogin,
                                            channelName = clip.channelName,
                                            channelImage = clip.channelImage,
                                        )
                                    )
                                },
                                onGameClick = {
                                    fragment.findNavController().navigate(
                                        if (prefs.getBoolean(C.UI_GAME_PAGER, true)) {
                                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                                gameId = clip.gameId,
                                                gameSlug = clip.gameSlug,
                                                gameName = clip.gameName,
                                            )
                                        } else {
                                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                                gameId = clip.gameId,
                                                gameSlug = clip.gameSlug,
                                                gameName = clip.gameName,
                                            )
                                        }
                                    )
                                },
                                roundChannelImage = prefs.getBoolean(C.UI_ROUND_USER_IMAGE, true),
                                cardMargin = if (!material3) 0.dp else if (prefs.getBoolean(C.UI_THEME_REDUCED_PADDING, false)) 4.dp else 8.dp,
                                cornerRadius = if (!material3) 0.dp else when (prefs.getString(C.UI_THEME_ROUNDED_CORNERS, "0")) {
                                    "1" -> 9.dp
                                    "2" -> 0.dp
                                    else -> 12.dp
                                },
                                compactText = material3 && prefs.getBoolean(C.UI_THEME_COMPACT_TEXT, false),
                                material3 = material3,
                            )
                        }
                    }
                }
            }
        }

        fun bind(item: Clip?) {
            this.item = item
            bindingVersion++
        }
    }
}
