package com.github.andreyasadchy.xtra.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.settings.VideoSwapSettingsViewModel.Companion.VideoSwapSettingsViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch

class VideoSwapSettingsFragment : Fragment() {

    private val viewModel: VideoSwapSettingsViewModel by viewModels { VideoSwapSettingsViewModelFactory }
    private var uiState by mutableStateOf(VideoSwapSettingsUiState())
    private var bottomInset by mutableIntStateOf(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = requireContext().prefs()
        viewModel.initialize(
            platform = prefs.getString(C.TOKEN_PLATFORM, "web"),
            playerType = prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
        )
        uiState = viewModel.state.value
        bottomInset = 0
        return ComposeView(requireContext()).apply {
            id = R.id.layout
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.canScrollBackward }.collect { scrolled ->
                        activity?.findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                            if (prefs.getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                                appBar.isLifted = scrolled
                            }
                        }
                    }
                }
                XtraTheme(themeId = theme) {
                    VideoSwapSettingsScreen(
                        state = uiState,
                        labels = VideoSwapLabels(
                            add = getString(R.string.add_item),
                            defaultValues = getString(R.string.default_values),
                            platform = getString(R.string.platform_param),
                            playerType = getString(R.string.player_type_param),
                            enabled = getString(R.string.enabled_setting),
                                            edit = getString(R.string.edit),
                            delete = getString(R.string.delete),
                            deleteMessage = getString(R.string.delete_item_message),
                            confirm = getString(android.R.string.ok),
                            cancel = getString(android.R.string.cancel),
                            reorder = getString(R.string.order),
                            moveUp = getString(R.string.ascending),
                            moveDown = getString(R.string.descending),
                            retry = getString(R.string.retry),
                        ),
                        onAdd = viewModel::add,
                        onEdit = { id, platform, playerType ->
                            if (viewModel.state.value.items.any { it.id == id && it.isDefault }) {
                                prefs.edit {
                                    putString(C.TOKEN_PLATFORM, platform)
                                    putString(C.TOKEN_PLAYER_TYPE, playerType)
                                }
                                viewModel.updateDefault(platform, playerType)
                            } else {
                                viewModel.edit(id, platform, playerType)
                            }
                        },
                        onToggle = viewModel::toggle,
                        onDelete = viewModel::delete,
                        onReorder = viewModel::reorder,
                        onRetry = viewModel::load,
                        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        listState = listState,
                        bottomPadding = with(LocalDensity.current) { bottomInset.toDp() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { uiState = it }
            }
        }
        requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
            if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                appBar.setLiftOnScrollTargetView(view)
            } else {
                appBar.setLiftable(false)
                appBar.background = null
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomInset = insets.bottom
            view.updatePadding(left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(view)
    }
}
