package com.github.andreyasadchy.xtra.ui.common

import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Video
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

class VideosAdapter(
    private val fragment: Fragment,
    private val showDownloadDialog: (Video) -> Unit,
    private val saveBookmark: (Video) -> Unit,
    private val showGame: Boolean = true,
    private val showChannel: Boolean = true,
) : PagingDataAdapter<Video, VideosAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean =
            oldItem.viewCount == newItem.viewCount &&
                    oldItem.thumbnailURL == newItem.thumbnailURL &&
                    oldItem.title == newItem.title &&
                    oldItem.durationSeconds == newItem.durationSeconds &&
                    oldItem.createdAt == newItem.createdAt &&
                    oldItem.type == newItem.type &&
                    oldItem.channelId == newItem.channelId &&
                    oldItem.channelLogin == newItem.channelLogin &&
                    oldItem.channelName == newItem.channelName &&
                    oldItem.channelImageURL == newItem.channelImageURL &&
                    oldItem.gameId == newItem.gameId &&
                    oldItem.gameSlug == newItem.gameSlug &&
                    oldItem.gameName == newItem.gameName &&
                    oldItem.animatedPreviewURL == newItem.animatedPreviewURL
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

    private var positions: List<VideoPosition>? = null

    fun setVideoPositions(positions: List<VideoPosition>) {
        this.positions = positions
        if (itemCount != 0) {
            notifyDataSetChanged()
        }
    }

    private var bookmarkIds by mutableStateOf(setOf<String?>())

    fun setBookmarksList(list: List<Bookmark>) {
        bookmarkIds = list.map { it.videoId }.toSet()
    }

    inner class PagingViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view) {
        private var item: Video? = null
        private var bindingVersion by mutableIntStateOf(0)

        init {
            view.setContent {
                key(bindingVersion) {
                    val video = item
                    if (video != null) {
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
                        val position = video.id?.toLongOrNull()?.let { id -> positions?.find { it.id == id }?.position }
                        val durationSeconds = video.durationSeconds
                        val startFromBeginning = position != null && durationSeconds != null && durationSeconds > 0 && position >= durationSeconds.toLong() * 1000L
                        val open = {
                            (fragment.activity as MainActivity).startVideo(video, if (startFromBeginning) 0L else position, startFromBeginning)
                        }
                        val download = { showDownloadDialog(video) }
                        val channelName = video.channelName?.let { name ->
                            if (video.channelLogin != null && !video.channelLogin.equals(name, true)) {
                                when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                    "0" -> "$name(${video.channelLogin})"
                                    "1" -> name
                                    else -> video.channelLogin
                                }
                            } else {
                                name
                            }
                        }
                        val actions = buildList {
                            add(MediaRowAction(context.getString(R.string.download), download))
                            if (!video.id.isNullOrBlank()) {
                                add(MediaRowAction(context.getString(if (video.id in bookmarkIds) R.string.remove_bookmark else R.string.add_bookmark)) {
                                    saveBookmark(video)
                                })
                            }
                            add(MediaRowAction(context.getString(R.string.share)) {
                                context.startActivity(Intent.createChooser(Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/videos/${video.id}")
                                    video.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                                    type = "text/plain"
                                }, null))
                            })
                            if (position != null && position > 0 && !startFromBeginning) {
                                add(MediaRowAction(context.getString(R.string.resume), open))
                            }
                        }
                        XtraTheme(darkTheme = theme != "2" && theme != "5", amoled = theme == "1" || theme == "6", blue = theme == "3") {
                            MediaRow(
                                data = MediaRowData(
                                    thumbnail = video.thumbnail,
                                    title = video.title?.takeIf { it.isNotBlank() }?.trim(),
                                    channelName = channelName.takeIf { showChannel },
                                    channelImage = video.channelImage.takeIf { showChannel },
                                    gameName = video.gameName.takeIf { showGame },
                                    date = video.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { formatChatDate(it) },
                                    views = video.viewCount?.let { count ->
                                        context.resources.getQuantityString(R.plurals.views, count, TwitchApiHelper.formatCount(count, prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
                                    },
                                    duration = durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
                                    type = video.type?.let { TwitchApiHelper.getType(context, it) },
                                    progress = if (position != null && durationSeconds != null && durationSeconds > 0) {
                                        (position / (durationSeconds.toLong() * 10L)).coerceIn(0L, 100L) / 100f
                                    } else {
                                        null
                                    },
                                ),
                                optionsText = context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                                downloadText = context.getString(R.string.download),
                                actions = actions,
                                onOpen = open,
                                onDownload = download,
                                onChannelClick = {
                                    fragment.findNavController().navigate(
                                        ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                            channelId = video.channelId,
                                            channelLogin = video.channelLogin,
                                            channelName = video.channelName,
                                            channelImage = video.channelImage,
                                        )
                                    )
                                },
                                onGameClick = {
                                    fragment.findNavController().navigate(
                                        if (prefs.getBoolean(C.UI_GAME_PAGER, true)) {
                                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                                gameId = video.gameId,
                                                gameSlug = video.gameSlug,
                                                gameName = video.gameName,
                                            )
                                        } else {
                                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                                gameId = video.gameId,
                                                gameSlug = video.gameSlug,
                                                gameName = video.gameName,
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

        fun bind(item: Video?) {
            this.item = item
            bindingVersion++
        }
    }
}
