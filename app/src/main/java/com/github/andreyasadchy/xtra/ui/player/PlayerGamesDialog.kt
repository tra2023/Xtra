package com.github.andreyasadchy.xtra.ui.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalConfiguration
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class PlayerGamesDialog : BottomSheetDialogFragment() {

    companion object {
        private const val GAMES = "games"

        fun newInstance(gamesList: List<Game>): PlayerGamesDialog {
            return PlayerGamesDialog().apply {
                arguments = Bundle().apply {
                    putString(GAMES, Json.encodeToString(ListSerializer(Game.serializer()), gamesList))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val games = requireArguments().getString(GAMES)?.let { json ->
            runCatching { Json.decodeFromString(ListSerializer(Game.serializer()), json) }.getOrNull()
        }.orEmpty()
        return playerSheetComposeView(inflater) { modifier, padding ->
            val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
            val columns = requireContext().prefs().getString(
                if (portrait) C.PORTRAIT_COLUMN_COUNT else C.LANDSCAPE_COLUMN_COUNT,
                if (portrait) "1" else "2",
            )?.toIntOrNull() ?: if (portrait) 1 else 2
            PlayerChaptersSheetContent(
                games = games,
                onGameClick = { game ->
                    game.vodPosition?.let { (parentFragment as? PlayerFragment)?.seek(it.toLong()) }
                    dismiss()
                },
                positionLabel = { durationLabel(R.string.position, it) },
                durationLabel = { durationLabel(R.string.duration, it) },
                modifier = modifier,
                columns = columns,
                contentPadding = padding,
            )
        }
    }

    private fun durationLabel(label: Int, milliseconds: Int): String? {
        return TwitchApiHelper.getDurationFromSeconds(requireContext(), (milliseconds / 1000).toString())
            .takeIf { !it.isNullOrBlank() }?.let { getString(label, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
