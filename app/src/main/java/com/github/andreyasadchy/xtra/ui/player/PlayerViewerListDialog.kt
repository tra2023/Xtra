package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerViewerListViewModel.Companion.PlayerViewerListViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class PlayerViewerListDialog : BottomSheetDialogFragment(), IntegrityDialog.Listener {

    companion object {
        private const val LOGIN = "login"

        fun newInstance(login: String): PlayerViewerListDialog {
            return PlayerViewerListDialog().apply {
                arguments = Bundle().apply {
                    putString(LOGIN, login)
                }
            }
        }
    }

    private val viewModel: PlayerViewerListViewModel by viewModels { PlayerViewerListViewModelFactory }
    private var viewerList by mutableStateOf<ChannelViewerList?>(null)
    private var loading by mutableStateOf(true)
    private var failed by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return playerSheetComposeView(inflater) { modifier, _ ->
            PlayerChattersSheetContent(
                viewerList = viewerList,
                loading = loading,
                error = if (failed) getString(R.string.connection_error) else null,
                countLabel = viewerList?.count?.let {
                    getString(R.string.user_count, TwitchApiHelper.formatCount(it, requireContext().prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true)))
                },
                groupLabels = listOf(R.string.broadcaster, R.string.moderators, R.string.vips, R.string.viewers).map { getString(it) },
                emptyLabel = getString(R.string.nothing_here),
                retryLabel = getString(R.string.retry),
                onRetry = ::loadViewerList,
                modifier = modifier,
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.integrity.collect {
                        (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                    }
                }
                launch { viewModel.viewerList.collect { viewerList = it } }
                launch { viewModel.loading.collect { loading = it } }
                launch { viewModel.failed.collect { failed = it } }
                loadViewerList()
            }
        }
    }

    private fun loadViewerList() {
        viewModel.loadViewerList(
            requireArguments().getString(LOGIN),
            TwitchApiHelper.getGQLHeaders(requireContext()),
            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == "refresh") loadViewerList()
    }
}
