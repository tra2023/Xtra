package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.downloads.DownloadListItem
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.formatChatDate
import com.github.andreyasadchy.xtra.util.prefs

class DownloadsAdapter(
    private val fragment: Fragment,
    private val stopDownload: (OfflineVideo) -> Unit,
    private val resumeDownload: (OfflineVideo) -> Unit,
    private val convertVideo: (OfflineVideo) -> Unit,
    private val moveVideo: (OfflineVideo) -> Unit,
    private val updateChatUrl: (OfflineVideo) -> Unit,
    private val shareVideo: (OfflineVideo) -> Unit,
    val deleteVideo: (OfflineVideo) -> Unit,
) {
    fun item(video: OfflineVideo, progress: DownloadProgressState?): DownloadListItem {
        val context = fragment.requireContext()
        val prefs = context.prefs()
        val status = if (video.status in listOf(OfflineVideo.STATUS_DOWNLOADING, OfflineVideo.STATUS_QUEUED, OfflineVideo.STATUS_WAITING_FOR_STREAM) && progress == null) {
            OfflineVideo.STATUS_PENDING
        } else video.status
        val fraction = when (status) {
            OfflineVideo.STATUS_DOWNLOADING -> if (!video.live && progress != null) fraction(progress.progress, progress.maxProgress) else null
            OfflineVideo.STATUS_MOVING, OfflineVideo.STATUS_DELETING, OfflineVideo.STATUS_CONVERTING -> fraction(video.progress, video.maxProgress)
            else -> null
        }
        val statusText = when (status) {
            OfflineVideo.STATUS_DOWNLOADED -> null
            OfflineVideo.STATUS_DOWNLOADING -> if (video.live || progress == null) context.getString(R.string.downloading) else context.getString(R.string.downloading_progress, ((fraction ?: 0f) * 100).toInt())
            OfflineVideo.STATUS_MOVING -> context.getString(R.string.download_moving, ((fraction ?: 0f) * 100).toInt())
            OfflineVideo.STATUS_DELETING -> context.getString(R.string.download_deleting, ((fraction ?: 0f) * 100).toInt())
            OfflineVideo.STATUS_CONVERTING -> context.getString(R.string.download_converting, ((fraction ?: 0f) * 100).toInt())
            OfflineVideo.STATUS_QUEUED -> context.getString(R.string.download_queued)
            OfflineVideo.STATUS_WAITING_FOR_NETWORK -> context.getString(R.string.download_blocked)
            OfflineVideo.STATUS_WAITING_FOR_WIFI -> context.getString(R.string.download_blocked_wifi)
            OfflineVideo.STATUS_WAITING_FOR_STREAM -> context.getString(R.string.download_waiting_for_stream)
            else -> context.getString(R.string.download_pending)
        }
        val chatFraction = if (video.downloadChat && status == OfflineVideo.STATUS_DOWNLOADING && progress != null && !video.live) fraction(progress.chatProgress, progress.maxChatProgress) else null
        val shared = video.url?.toUri()?.scheme == ContentResolver.SCHEME_CONTENT
        val actions = buildList {
            when (status) {
                OfflineVideo.STATUS_DOWNLOADING, OfflineVideo.STATUS_QUEUED, OfflineVideo.STATUS_WAITING_FOR_NETWORK, OfflineVideo.STATUS_WAITING_FOR_WIFI, OfflineVideo.STATUS_WAITING_FOR_STREAM -> add(R.id.stopDownload to context.getString(R.string.stop_download))
                OfflineVideo.STATUS_PENDING -> {
                    if (video.live) add(R.id.stopDownload to context.getString(R.string.stop_download))
                    add(R.id.resumeDownload to context.getString(R.string.resume_download))
                }
                else -> {
                    add(R.id.moveVideo to context.getString(if (shared) R.string.move_to_app_storage else R.string.move_to_shared_storage))
                    if (video.url?.endsWith(".m3u8") == true) add(R.id.convertVideo to context.getString(R.string.convert_vod_to_file))
                    add(R.id.updateChatUrl to context.getString(R.string.change_chat_file))
                    if (shared) add(R.id.shareVideo to context.getString(R.string.share))
                }
            }
            add(R.id.delete to context.getString(R.string.delete))
        }
        val duration = video.duration
        val position = video.lastWatchPosition
        return DownloadListItem(
            id = video.id,
            title = video.name?.trim(),
            thumbnail = video.thumbnail,
            channel = if (video.channelLogin != null && !video.channelLogin.equals(video.channelName, true) && video.channelName != null) {
                when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                    "0" -> "${video.channelName}(${video.channelLogin})"
                    "1" -> video.channelName
                    else -> video.channelLogin
                }
            } else video.channelName,
            channelImage = video.channelLogo,
            roundImage = prefs.getBoolean(C.UI_ROUND_USER_IMAGE, true),
            game = video.gameName,
            details = buildList {
                video.uploadDate?.let { add(context.getString(R.string.uploaded_date, formatChatDate(it))) }
                video.downloadDate?.let { add(context.getString(R.string.downloaded_date, formatChatDate(it))) }
                video.type?.let { TwitchApiHelper.getType(context, it)?.let(::add) }
                duration?.let {
                    add(DateUtils.formatElapsedTime(it / 1000L))
                    video.sourceStartPosition?.let { start ->
                        add(context.getString(R.string.source_vod_start, DateUtils.formatElapsedTime(start / 1000L)))
                        add(context.getString(R.string.source_vod_end, DateUtils.formatElapsedTime((start + it) / 1000L)))
                    }
                }
            },
            status = statusText,
            progress = fraction,
            chatStatus = chatFraction?.let { context.getString(R.string.chat_downloading_progress, (it * 100).toInt()) },
            chatProgress = chatFraction,
            watched = if (prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true) && position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
            actions = actions,
        )
    }

    private fun fraction(progress: Int, max: Int): Float = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f

    fun action(video: OfflineVideo, action: Int) {
        when (action) {
            R.id.stopDownload -> stopDownload(video)
            R.id.resumeDownload -> resumeDownload(video)
            R.id.convertVideo -> convertVideo(video)
            R.id.moveVideo -> moveVideo(video)
            R.id.updateChatUrl -> updateChatUrl(video)
            R.id.shareVideo -> shareVideo(video)
            R.id.delete -> deleteVideo(video)
        }
    }

    fun open(video: OfflineVideo) {
        val duration = video.duration
        val position = video.lastWatchPosition
        (fragment.activity as? MainActivity)?.startOfflineVideo(video, if (position != null && duration != null && duration > 0 && position >= duration) 0 else null)
    }

    fun channel(video: OfflineVideo) {
        fragment.findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
            channelId = video.channelId, channelLogin = video.channelLogin, channelName = video.channelName, channelImage = video.channelLogo, updateLocal = true,
        ))
    }

    fun game(video: OfflineVideo) {
        fragment.findNavController().navigate(if (fragment.requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
            GamePagerFragmentDirections.actionGlobalGamePagerFragment(gameId = video.gameId, gameSlug = video.gameSlug, gameName = video.gameName)
        } else {
            GameMediaFragmentDirections.actionGlobalGameMediaFragment(gameId = video.gameId, gameSlug = video.gameSlug, gameName = video.gameName)
        })
    }
}
