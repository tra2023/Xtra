package com.github.andreyasadchy.xtra.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.settings.LocalXtraSettings
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.TwitchFormats
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

/**
 * Android implementation of the shared media actions: player navigation via
 * [MainActivity] and the system share chooser.
 */
class AndroidXtraMediaActions(private val activity: Activity?) : XtraMediaActions {

    override fun openClip(clip: Clip) {
        (activity as? MainActivity)?.startClip(clip)
    }

    override fun openVideo(video: Video, offset: Long?, ignoreSavedPosition: Boolean) {
        (activity as? MainActivity)?.startVideo(video, offset, ignoreSavedPosition)
    }

    override fun share(url: String, title: String?) {
        activity?.startActivity(
            Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, url)
                    title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    type = "text/plain"
                },
                null,
            )
        )
    }
}

/** Resume position of [videoId] in a loaded position list, if any. */
fun List<VideoPosition>?.positionFor(videoId: String?): Long? =
    videoId?.toLongOrNull()?.let { id -> this?.find { it.id == id }?.position }

/** Android resources backing [XtraStrings]. */
fun Context.xtraStrings(): XtraStrings {
    val truncate = prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)
    return XtraStrings(
        download = getString(R.string.download),
        share = getString(R.string.share),
        options = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
        resume = getString(R.string.resume),
        addBookmark = getString(R.string.add_bookmark),
        removeBookmark = getString(R.string.remove_bookmark),
        error = { getString(R.string.error, it) },
        nothingHere = getString(R.string.nothing_here),
        retry = getString(R.string.retry),
        sort = getString(R.string.sort),
        viewers = { count -> resources.getQuantityString(R.plurals.viewers, count, TwitchFormats.formatCount(count, truncate)) },
        views = { count -> resources.getQuantityString(R.plurals.views, count, TwitchFormats.formatCount(count, truncate)) },
        broadcasters = { count -> resources.getQuantityString(R.plurals.broadcasters, count, TwitchFormats.formatCount(count, truncate)) },
        followers = { count -> resources.getQuantityString(R.plurals.followers, count, TwitchFormats.formatCount(count, truncate)) },
        uptime = { getString(R.string.uptime, it) },
        videoType = { type -> TwitchApiHelper.getType(this, type) },
    )
}

/**
 * Provides the platform-backed settings, strings and media actions consumed by
 * the shared Compose rows in `:core:ui`. Wrap every Compose root that renders
 * paging lists or list items.
 */
@Composable
fun ProvideXtraLocals(activity: Activity?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) {
        AndroidXtraSettings(context.applicationContext.prefs(), context.applicationContext.tokenPrefs())
    }
    val strings = remember(context) { context.xtraStrings() }
    val actions = remember(activity) { AndroidXtraMediaActions(activity) }
    CompositionLocalProvider(
        LocalXtraSettings provides settings,
        LocalXtraStrings provides strings,
        LocalXtraMediaActions provides actions,
        content = content,
    )
}
