package com.github.andreyasadchy.xtra.ui.games

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentGamesBinding
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.collections.GameCollectionRow
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesViewModel.Companion.GamesViewModelFactory
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GamesFragment : PagedListFragment(), Scrollable, GamesSortDialog.OnFilter {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private val args: GamesFragmentArgs by navArgs()
    private val viewModel: GamesViewModel by viewModels { GamesViewModelFactory }
    // Compose owns list + load states now (PagedListFragment.PagingContent): signals drive refresh / scroll-top.
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val activity = requireActivity() as MainActivity
            val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
            val navController = findNavController()
            val appBarConfiguration = AppBarConfiguration(setOf(R.id.rootGamesFragment, R.id.rootTopFragment, R.id.followPagerFragment, R.id.followMediaFragment, R.id.savedPagerFragment, R.id.savedMediaFragment))
            toolbar.setupWithNavController(navController, appBarConfiguration)
            toolbar.menu.findItem(R.id.login).title = if (isLoggedIn) getString(R.string.log_out) else getString(R.string.log_in)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search -> {
                        findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
                        true
                    }
                    R.id.settings -> {
                        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
                        true
                    }
                    R.id.login -> {
                        if (isLoggedIn) {
                            activity.getAlertDialogBuilder().apply {
                                setTitle(getString(R.string.logout_title))
                                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let { setMessage(getString(R.string.logout_msg, it)) }
                                setNegativeButton(getString(R.string.no), null)
                                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
                            }.show()
                        } else {
                            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                        }
                        true
                    }
                    else -> false
                }
            }
            if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                recyclerViewLayout.recyclerView.let {
                    appBar.setLiftOnScrollTargetView(it)
                    it.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            appBar.isLifted = recyclerView.canScrollVertically(-1)
                        }
                    })
                    it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        appBar.isLifted = it.canScrollVertically(-1)
                    }
                }
            } else {
                appBar.setLiftable(false)
                appBar.background = null
            }
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                }
                if (activity.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                    val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    recyclerViewLayout.recyclerView.updatePadding(bottom = systemBars.bottom)
                }
                WindowInsetsCompat.CONSUMED
            }
        }
        // Compose owns list + load states (PagedListFragment.PagingContent via paging-compose).
        // Views survivors: toolbar, sort bar. The hidden RecyclerView container keeps
        // its overlays off; refresh gesture + scroll-top live in Compose now.
        binding.recyclerViewLayout.recyclerView.isVisible = false
        binding.recyclerViewLayout.progressBar.isVisible = false
        binding.recyclerViewLayout.nothingHere.isVisible = false
        binding.recyclerViewLayout.scrollTop.isVisible = false
        binding.recyclerViewLayout.swipeRefresh.isEnabled = false
        val composeView = createPagingView()
        (binding.recyclerViewLayout.root as ViewGroup).addView(
            composeView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pagingContent = {
            val refreshTick by composeRefreshSignal.collectAsState()
            val scrollTick by composeScrollTopSignal.collectAsState()
            val context = LocalContext.current
            val showTags = context.prefs().getBoolean(C.UI_TAGS, true)
            val showBroadcasters = context.prefs().getBoolean(C.UI_BROADCASTERS_COUNT, true)
            val truncate = context.prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                keyForItem = { it.id ?: it.name ?: it.hashCode().toString() },
            ) { game ->
                GameCollectionRow(
                    name = game.name,
                    image = game.boxArt,
                    viewers = game.viewerCount?.let { count ->
                        context.resources.getQuantityString(
                            R.plurals.viewers,
                            count,
                            TwitchApiHelper.formatCount(count, truncate),
                        )
                    },
                    broadcasters = game.broadcasterCount?.takeIf { showBroadcasters }?.let { count ->
                        context.resources.getQuantityString(
                            R.plurals.broadcasters,
                            count,
                            TwitchApiHelper.formatCount(count, truncate),
                        )
                    },
                    tags = if (showTags) game.tags.orEmpty().filter { it.name != null } else emptyList(),
                    tagLabel = { it.name.orEmpty() },
                    tagEnabled = { it.id != null },
                    onTagClick = ::addTag,
                    onClick = { openGame(game) },
                )
            }
        }
    }

    private fun openGame(game: Game) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = game.id,
                    gameSlug = game.slug,
                    gameName = game.name,
                    boxArt = game.boxArt,
                )
            }
        )
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                viewModel.setFilter(viewModel.tags.ifEmpty { navTags() })
                viewModel.filtersText.value = if (viewModel.tags.isNotEmpty()) {
                    buildString {
                        append(
                            resources.getQuantityString(
                                R.plurals.tags,
                                viewModel.tags.size,
                                viewModel.tags.mapNotNull { it.name }.joinToString()
                            )
                        )
                    }
                } else null
            }
            // No adapter: PagingContent collects viewModel.flow and owns load states.
        }
        with(binding) {
            sortBar.root.visibility = View.VISIBLE
            sortBar.root.setOnClickListener {
                val tags = viewModel.tags.mapNotNull { tag ->
                    val id = tag.id
                    val name = tag.name
                    if (id != null && name != null) {
                        id to name
                    } else null
                }.toMap()
                GamesSortDialog.newInstance(
                    tagIds = tags.keys.toTypedArray(),
                    tagNames = tags.values.toTypedArray(),
                ).show(childFragmentManager, null)
            }
            sortBar.sortText.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.filtersText.collectLatest {
                        if (it != null) {
                            sortBar.filtersText.visibility = View.VISIBLE
                            sortBar.filtersText.text = it
                        } else {
                            sortBar.filtersText.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun navTags(): Array<Tag>? {
        val ids = args.tagIds
        if (ids.isNullOrEmpty()) return null
        val names = args.tagNames
        return ids.mapIndexed { index, id -> Tag(id = id, name = names?.getOrNull(index)) }.toTypedArray()
    }

    private fun addTag(tag: Tag) {
        viewLifecycleOwner.lifecycleScope.launch {
            // New filter emits a new PagingData via flatMapLatest; Compose reloads.
            val tags = viewModel.tags.plus(tag).sortedBy { it.id }.toTypedArray()
            viewModel.setFilter(tags)
            viewModel.filtersText.value = buildString {
                append(
                    resources.getQuantityString(
                        R.plurals.tags,
                        viewModel.tags.size,
                        viewModel.tags.mapNotNull { it.name }.joinToString()
                    )
                )
            }
        }
    }

    override fun onChange(tags: Array<Tag>) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.setFilter(tags)
            viewModel.filtersText.value = if (viewModel.tags.isNotEmpty()) {
                buildString {
                    append(
                        resources.getQuantityString(
                            R.plurals.tags,
                            viewModel.tags.size,
                            viewModel.tags.mapNotNull { it.name }.joinToString()
                        )
                    )
                }
            } else null
        }
    }

    override fun scrollToTop() {
        binding.appBar.setExpanded(true, true)
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