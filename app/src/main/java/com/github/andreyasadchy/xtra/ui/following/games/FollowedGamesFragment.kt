package com.github.andreyasadchy.xtra.ui.following.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.collections.collectionCount
import com.github.andreyasadchy.xtra.ui.collections.collectionFollowLabels
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.following.games.FollowedGamesViewModel.Companion.FollowedGamesViewModelFactory
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow

class FollowedGamesFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FollowedGamesViewModel by viewModels { FollowedGamesViewModelFactory }
    // Compose owns list + load states now (PagedListFragment.PagingContent): signals drive refresh / scroll-top.
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Compose owns list + load states. The hidden RecyclerView container keeps
        // its overlays off; refresh gesture + scroll-top live in Compose now.
        binding.recyclerView.isVisible = false
        binding.progressBar.isVisible = false
        binding.nothingHere.isVisible = false
        binding.scrollTop.isVisible = false
        binding.swipeRefresh.isEnabled = false
        val composeView = createPagingView()
        (binding.root as ViewGroup).addView(
            composeView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pagingContent = {
            val refreshTick by composeRefreshSignal.collectAsState()
            val scrollTick by composeScrollTopSignal.collectAsState()
            val context = LocalContext.current
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                enableScrollTop = false,
                keyForItem = { it.id ?: it.name ?: it.hashCode().toString() },
            ) { game ->
                GameCollectionRow(
                    name = game.name,
                    image = game.boxArt,
                    viewers = context.collectionCount(game.viewerCount, R.plurals.viewers),
                    broadcasters = context.collectionCount(
                        game.broadcasterCount.takeIf { context.prefs().getBoolean(C.UI_BROADCASTERS_COUNT, true) },
                        R.plurals.broadcasters,
                    ),
                    tags = game.tags.orEmpty().takeIf { context.prefs().getBoolean(C.UI_TAGS, true) }.orEmpty(),
                    tagLabel = { it.name.orEmpty() },
                    tagEnabled = { it.id != null },
                    onTagClick = ::openTag,
                    followLabels = context.collectionFollowLabels(game.accountFollow, game.localFollow),
                    diskCache = false,
                    onClick = { openGame(game) },
                )
            }
        }
    }

    override fun initialize() {
        // No adapter: PagingContent collects viewModel.flow and owns load states.
    }

    private fun openGame(game: Game) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    updateLocal = game.localFollow,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    updateLocal = game.localFollow,
                )
            }
        )
    }

    private fun openTag(tag: Tag) {
        findNavController().navigate(
            GamesFragmentDirections.actionGlobalGamesFragment(
                tagIds = listOfNotNull(tag.id).toTypedArray(),
                tagNames = listOfNotNull(tag.name).toTypedArray(),
            )
        )
    }

    override fun scrollToTop() {
        composeScrollTopSignal.value++
    }

    override fun onNetworkRestored() {
        composeRefreshSignal.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                composeRefreshSignal.value++
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
