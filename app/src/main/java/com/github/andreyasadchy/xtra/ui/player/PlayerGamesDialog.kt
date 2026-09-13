package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.view.GridRecyclerView
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
        val recycleView = GridRecyclerView(requireContext()).apply {
            id = R.id.recyclerView
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            requireContext().obtainStyledAttributes(intArrayOf(R.attr.dialogLayoutPadding)).use {
                setPadding(it.getDimensionPixelSize(0, 0))
            }
            adapter = PlayerGamesDialogAdapter(this@PlayerGamesDialog).also {
                it.submitList(
                    requireArguments().getString(GAMES)?.let { json ->
                        runCatching { Json.decodeFromString(ListSerializer(Game.serializer()), json) }.getOrNull()
                    }?.toList()
                )
            }
        }
        return NestedScrollView(requireContext()).apply { addView(recycleView) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
