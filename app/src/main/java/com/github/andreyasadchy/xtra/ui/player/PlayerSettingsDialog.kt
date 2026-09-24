package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.media3.common.Tracks
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerSettingsDialog : BottomSheetDialogFragment() {

    companion object {
        private const val TYPE = "type"
        private const val SPEED = "speed"
        private const val VOD_GAMES = "vod_games"
        private const val QUALITY = "quality"
        private const val BOOKMARK = "bookmark"
        private const val SUBTITLES = "subtitles"

        fun newInstance(type: String?, speedText: String?, vodGames: Boolean): PlayerSettingsDialog {
            return PlayerSettingsDialog().apply {
                arguments = Bundle().apply {
                    putString(TYPE, type)
                    putString(SPEED, speedText)
                    putBoolean(VOD_GAMES, vodGames)
                }
            }
        }
    }

    private var menuState by mutableStateOf(PlayerMenuSnapshot())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = savedInstanceState ?: requireArguments()
        menuState = menuState.copy(
            quality = menuState.quality ?: source.getString(QUALITY),
            speed = menuState.speed ?: source.getString(SPEED),
            vodGames = menuState.vodGames || (source.getBoolean(VOD_GAMES) &&
                (savedInstanceState != null || requireArguments().getString(TYPE) == BasePlaybackService.VIDEO)),
            bookmarked = menuState.bookmarked ?: source.takeIf { it.containsKey(BOOKMARK) }?.getBoolean(BOOKMARK),
            subtitlesSelected = menuState.subtitlesSelected ?: source.takeIf { it.containsKey(SUBTITLES) }?.getBoolean(SUBTITLES),
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return playerSheetComposeView(inflater) { modifier, padding ->
            PlayerMenuSheetContent(menuEntries(menuState), modifier, padding)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val prefs = requireContext().prefs()
        val player = parentFragment as? PlayerFragment
        if (prefs.getBoolean(C.PLAYER_MENU_QUALITY, false)) {
            player?.setQualityText()
        }
        if (requireArguments().getString(TYPE) == BasePlaybackService.VIDEO && prefs.getBoolean(C.PLAYER_MENU_BOOKMARK, true)) {
            player?.checkBookmark()
        }
        player?.setSubtitlesButton()
    }

    private fun menuEntries(state: PlayerMenuSnapshot): List<PlayerMenuEntry> = buildList {
        val context = requireContext()
        val prefs = context.prefs()
        val type = requireArguments().getString(TYPE)
        val player = parentFragment as? PlayerFragment
        val stream = type == BasePlaybackService.STREAM
        val chatEnabled = !prefs.getBoolean(C.CHAT_DISABLE, false)
        val landscape = player?.getIsPortrait() == false
        fun entry(key: String, label: Int, value: String? = null, action: () -> Unit) {
            add(PlayerMenuEntry(key, getString(label), value) {
                action()
                dismiss()
            })
        }
        if (prefs.getBoolean(C.PLAYER_MENU_QUALITY, false)) {
            entry("quality", R.string.quality, state.quality) {
                if (!state.quality.isNullOrBlank()) player?.showQualityDialog()
            }
        }
        if (!stream && prefs.getBoolean(C.PLAYER_MENU_SPEED, false)) {
            entry("speed", R.string.playback_speed, state.speed) { player?.showSpeedDialog() }
        }
        if (stream && prefs.getBoolean(C.PLAYER_MENU_VIEWER_LIST, true)) {
            entry("viewers", R.string.viewer_list) { player?.openViewerList() }
        }
        if (state.vodGames && prefs.getBoolean(C.PLAYER_MENU_GAMES, false)) {
            entry("games", R.string.chapters) { player?.showVodGames() }
        }
        if (type != BasePlaybackService.OFFLINE_VIDEO && prefs.getBoolean(C.PLAYER_MENU_DOWNLOAD, true)) {
            entry("download", R.string.download) { player?.showDownloadDialog() }
        }
        state.bookmarked?.let {
            entry("bookmark", if (it) R.string.remove_bookmark else R.string.add_bookmark) { player?.saveBookmark() }
        }
        if (prefs.getBoolean(C.PLAYER_MENU_SHARE, true)) {
            entry("share", R.string.share) { player?.share() }
        }
        if (stream && prefs.getBoolean(C.PLAYER_MENU_FIND_VOD, true)) {
            entry("findVod", R.string.find_unlisted_video) { player?.findUnlistedVideo() }
        }
        if (type != BasePlaybackService.CLIP && prefs.getBoolean(C.PLAYER_MENU_SLEEP, true)) {
            entry("timer", R.string.sleep_timer) { player?.showSleepTimerDialog() }
        }
        if (landscape && prefs.getBoolean(C.PLAYER_MENU_ASPECT, false)) {
            entry("ratio", R.string.aspect_ratio) { player.setResizeMode() }
        }
        if (prefs.getBoolean(C.PLAYER_MENU_VOLUME, false)) {
            entry("volume", R.string.volume) { player?.showVolumeDialog() }
        }
        state.subtitlesSelected?.takeIf { prefs.getBoolean(C.PLAYER_MENU_SUBTITLES, true) }?.let { selected ->
            entry("subtitles", if (selected) R.string.hide_subtitles else R.string.show_subtitles) {
                player?.toggleSubtitles(!selected)
                prefs.edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, !selected) }
            }
        }
        if (stream && prefs.getBoolean(C.PLAYER_MENU_RESTART, false)) {
            entry("restart", R.string.restart_player) { player?.restartPlayer() }
        }
        if (stream && chatEnabled) {
            val loggedIn = !context.tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                (!TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank())
            if (loggedIn && prefs.getBoolean(C.PLAYER_MENU_CHAT_BAR, true)) {
                entry("chatBar", if (prefs.getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)) R.string.hide_chat_bar else R.string.show_chat_bar) {
                    player?.toggleChatBar()
                }
            }
        }
        if (landscape && prefs.getBoolean(C.PLAYER_MENU_CHAT_TOGGLE, false)) {
            val opened = prefs.getBoolean(C.KEY_CHAT_OPENED, true)
            entry("chatToggle", if (opened) R.string.hide_chat else R.string.show_chat) {
                if (opened) player.hideChat() else player.showChat()
            }
        }
        if ((stream || type == BasePlaybackService.VIDEO) && chatEnabled && prefs.getBoolean(C.PLAYER_MENU_RELOAD_EMOTES, true)) {
            entry("emotes", R.string.reload_emotes) { player?.reloadEmotes() }
        }
        if (stream && chatEnabled && prefs.getBoolean(C.PLAYER_MENU_CHAT_DISCONNECT, true)) {
            val active = player?.isActive() == true
            entry("chatConnection", if (active) R.string.disconnect_chat else R.string.connect_chat) {
                if (active) player.disconnect() else player?.reconnect()
            }
        }
        if (stream && prefs.getBoolean(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false)) {
            entry("mediaTags", R.string.show_media_playlist_tags) { player?.showPlaylistTags(true) }
            entry("multivariantTags", R.string.show_multivariant_playlist_tags) { player?.showPlaylistTags(false) }
        }
    }

    fun setQuality(text: String?) {
        if (!text.isNullOrBlank()) menuState = menuState.copy(quality = text)
    }

    fun setSpeed(text: String?) {
        if (!text.isNullOrBlank()) menuState = menuState.copy(speed = text)
    }

    fun setVodGames() {
        menuState = menuState.copy(vodGames = true)
    }

    fun setBookmarkText(isBookmarked: Boolean) {
        menuState = menuState.copy(bookmarked = isBookmarked)
    }

    fun setSubtitles(subtitles: Tracks.Group? = null) {
        menuState = menuState.copy(subtitlesSelected = subtitles?.isSelected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(QUALITY, menuState.quality)
        outState.putString(SPEED, menuState.speed)
        outState.putBoolean(VOD_GAMES, menuState.vodGames)
        menuState.bookmarked?.let { outState.putBoolean(BOOKMARK, it) }
        menuState.subtitlesSelected?.let { outState.putBoolean(SUBTITLES, it) }
        super.onSaveInstanceState(outState)
    }
}
