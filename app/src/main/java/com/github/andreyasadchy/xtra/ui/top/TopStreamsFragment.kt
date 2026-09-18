package com.github.andreyasadchy.xtra.ui.top

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentGamesBinding
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.StreamListItem
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.RECENT
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS
import com.github.andreyasadchy.xtra.ui.common.StreamsSortDialog.Companion.SORT_VIEWERS_ASC
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.top.TopStreamsViewModel.Companion.TopStreamsViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TopStreamsFragment : PagedListFragment(), Scrollable, StreamsSortDialog.OnFilter {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: TopStreamsViewModel by viewModels { TopStreamsViewModelFactory }
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
            // Pinned AppBar (no scroll flags) with a Compose list: no scroll target
            // to wire lift to. Only the opt-out flat style remains.
            if (!requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
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
        val compact = requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") == "all"
        val enableScrollTop = !args.tags.isNullOrEmpty() || !args.languages.isNullOrEmpty()
        val composeView = createPagingView()
        (binding.recyclerViewLayout.root as ViewGroup).addView(
            composeView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pagingContent = {
            val refreshTick by composeRefreshSignal.collectAsState()
            val scrollTick by composeScrollTopSignal.collectAsState()
            PagingContent(
                flow = viewModel.flow,
                refreshSignal = refreshTick,
                retrySignal = 0,
                scrollTopSignal = scrollTick,
                enableScrollTop = enableScrollTop,
                keyForItem = { it.id ?: it.channelId ?: it.channelLogin ?: it.hashCode().toString() },
            ) { stream ->
                StreamListItem(
                    stream = stream,
                    compact = compact,
                    onStreamClick = { (activity as? MainActivity)?.startStream(it) },
                    onChannelClick = ::openChannel,
                    onGameClick = ::openGame,
                    onTagClick = ::addTag,
                )
            }
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val sortValues = viewModel.getGameSort("top_streams")
                viewModel.setFilter(
                    sort = sortValues?.streamSort,
                    tags = args.tags ?: sortValues?.streamTags?.split(',')?.toTypedArray(),
                    languages = args.languages ?: sortValues?.streamLanguages?.split(',')?.toTypedArray(),
                )
                viewModel.sortText.value = getString(
                    R.string.sort_by,
                    getString(
                        when (viewModel.sort) {
                            SORT_VIEWERS -> R.string.viewers_high
                            SORT_VIEWERS_ASC -> R.string.viewers_low
                            RECENT -> R.string.recent
                            else -> R.string.viewers_high
                        }
                    )
                )
                viewModel.filtersText.value = if (viewModel.tags.isNotEmpty() || viewModel.languages.isNotEmpty()) {
                    buildString {
                        if (viewModel.tags.isNotEmpty()) {
                            append(
                                resources.getQuantityString(
                                    R.plurals.tags,
                                    viewModel.tags.size,
                                    viewModel.tags.joinToString()
                                )
                            )
                        }
                        if (viewModel.languages.isNotEmpty()) {
                            if (isNotEmpty()) {
                                append(". ")
                            }
                            append(
                                resources.getQuantityString(
                                    R.plurals.languages,
                                    viewModel.languages.size,
                                    viewModel.languages.joinToString()
                                )
                            )
                        }
                    }
                } else null
            }
            // No adapter: PagingContent collects viewModel.flow and owns load states.
        }
        with(binding) {
            sortBar.root.visibility = View.VISIBLE
            sortBar.root.setOnClickListener {
                StreamsSortDialog.newInstance(
                    sort = viewModel.sort,
                    tags = viewModel.tags,
                    languages = viewModel.languages
                ).show(childFragmentManager, null)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.sortText.collectLatest {
                        sortBar.sortText.text = it
                    }
                }
            }
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

    private fun openChannel(item: Stream) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = item.channelId,
                channelLogin = item.channelLogin,
                channelName = item.channelName,
                channelImage = item.channelImage,
                streamId = item.id,
            )
        )
    }

    private fun openGame(item: Stream) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = item.gameId,
                    gameSlug = item.gameSlug,
                    gameName = item.gameName,
                )
            }
        )
    }

    private fun addTag(tag: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // New filter emits a new PagingData via flatMapLatest; Compose reloads.
            val tags = viewModel.tags.plus(tag).sortedArray()
            viewModel.setFilter(viewModel.sort, tags, viewModel.languages)
            viewModel.filtersText.value = buildString {
                if (viewModel.tags.isNotEmpty()) {
                    append(
                        resources.getQuantityString(
                            R.plurals.tags,
                            viewModel.tags.size,
                            viewModel.tags.joinToString()
                        )
                    )
                }
                if (viewModel.languages.isNotEmpty()) {
                    if (isNotEmpty()) {
                        append(". ")
                    }
                    append(
                        resources.getQuantityString(
                            R.plurals.languages,
                            viewModel.languages.size,
                            viewModel.languages.joinToString()
                        )
                    )
                }
            }
        }
    }

    override fun onChange(sort: String, sortText: CharSequence, tags: Array<String>, languages: Array<String>, changed: Boolean, saveFilters: Boolean, saveSort: Boolean, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (changed) {
                viewModel.setFilter(sort, tags, languages)
                viewModel.sortText.value = getString(R.string.sort_by, sortText)
                viewModel.filtersText.value = if (viewModel.tags.isNotEmpty() || viewModel.languages.isNotEmpty()) {
                    buildString {
                        if (viewModel.tags.isNotEmpty()) {
                            append(
                                resources.getQuantityString(
                                    R.plurals.tags,
                                    viewModel.tags.size,
                                    viewModel.tags.joinToString()
                                )
                            )
                        }
                        if (viewModel.languages.isNotEmpty()) {
                            if (isNotEmpty()) {
                                append(". ")
                            }
                            append(
                                resources.getQuantityString(
                                    R.plurals.languages,
                                    viewModel.languages.size,
                                    viewModel.languages.joinToString()
                                )
                            )
                        }
                    }
                } else null
            }
            if (saveFilters && (tags.isNotEmpty() || languages.isNotEmpty())) {
                viewModel.saveFilters(
                    SavedFilter(
                        tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                        languages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                    )
                )
            }
            if (saveDefault) {
                val item = viewModel.getGameSort("top_streams")?.apply {
                    streamSort = sort
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                } ?: GameSort(
                    id = "top_streams",
                    streamSort = sort,
                    streamTags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                    streamLanguages = languages.takeIf { it.isNotEmpty() }?.joinToString(",")
                )
                viewModel.saveGameSort(item)
            }
        }
    }

    override fun deleteSavedSort() {}

    override fun scrollToTop() {
        with(binding) {
            appBar.setExpanded(true, true)
            composeScrollTopSignal.value++
        }
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
