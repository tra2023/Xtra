package com.github.andreyasadchy.xtra.ui.common

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.SearchTagsViewModel.Companion.SearchTagsViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.TagSearchContent
import com.github.andreyasadchy.xtra.ui.search.TagSearchLoadState
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.getThemeId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchTagsDialog : DialogFragment() {

    interface OnTagSelectedListener {
        fun onTagSelected(tag: Tag)
    }

    companion object {
        private const val GET_GAME_TAGS = "getGameTags"
        private const val QUERY = "query"
        private const val APPLIED_QUERY = "appliedQuery"

        fun newInstance(getGameTags: Boolean): SearchTagsDialog {
            return SearchTagsDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(GET_GAME_TAGS, getGameTags)
                }
            }
        }
    }

    private val viewModel: SearchTagsViewModel by viewModels { SearchTagsViewModelFactory }
    private var composeView: ComposeView? = null
    private var query by mutableStateOf("")
    private var queryJob: Job? = null
    private var listener: OnTagSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? OnTagSelectedListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.getGameTags = requireArguments().getBoolean(GET_GAME_TAGS)
        savedInstanceState?.getString(APPLIED_QUERY)?.let(viewModel::setQuery)
        query = savedInstanceState?.getString(QUERY) ?: viewModel.query.value
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = requireContext().getAlertDialogBuilder()
        val theme = requireContext().getThemeId()
        val view = ComposeView(builder.context).apply {
            id = R.id.searchView
            setViewTreeLifecycleOwner(this@SearchTagsDialog)
            setViewTreeSavedStateRegistryOwner(this@SearchTagsDialog)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@SearchTagsDialog.lifecycle))
            setContent {
                val pagingFlow = remember(viewModel, this@SearchTagsDialog.lifecycle) {
                    viewModel.flow.flowWithLifecycle(this@SearchTagsDialog.lifecycle, Lifecycle.State.STARTED)
                }
                val lazyTags = pagingFlow.collectAsLazyPagingItems()
                val appliedQuery by viewModel.query.collectAsState()
                XtraTheme(themeId = theme) {
                    TagSearchContent(
                        query = query,
                        appliedQuery = appliedQuery,
                        onQueryChange = ::onQueryChange,
                        onSubmit = ::submitQuery,
                        itemCount = lazyTags.itemCount,
                        tagAt = { index -> lazyTags[index] },
                        refreshState = lazyTags.loadState.refresh.toTagSearchLoadState(),
                        prependState = lazyTags.loadState.prepend.toTagSearchLoadState(),
                        appendState = lazyTags.loadState.append.toTagSearchLoadState(),
                        searchLabel = getString(R.string.search_tags),
                        clearLabel = getString(androidx.appcompat.R.string.abc_searchview_description_clear),
                        emptyLabel = if (appliedQuery.isNotBlank()) getString(R.string.nothing_here) else "",
                        retryLabel = getString(R.string.retry),
                        onRetry = { lazyTags.retry() },
                        onTagSelected = { tag ->
                            queryJob?.cancel()
                            listener?.onTagSelected(tag)
                            dismiss()
                        },
                    )
                }
            }
        }
        composeView = view
        if (query != viewModel.query.value) {
            onQueryChange(query)
        }
        return builder.setView(view).create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun onQueryChange(value: String) {
        query = value
        queryJob?.cancel()
        queryJob = null
        if (value.isEmpty()) {
            viewModel.setQuery(value)
        } else {
            queryJob = lifecycleScope.launch {
                delay(750)
                withResumed {
                    viewModel.setQuery(value)
                }
            }
        }
    }

    private fun submitQuery() {
        queryJob?.cancel()
        queryJob = null
        viewModel.setQuery(query)
    }

    private fun LoadState.toTagSearchLoadState(): TagSearchLoadState = TagSearchLoadState(
        isLoading = this is LoadState.Loading,
        error = (this as? LoadState.Error)?.let { getString(R.string.error, it.error.message.orEmpty()) },
    )

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(QUERY, query)
        outState.putString(APPLIED_QUERY, viewModel.query.value)
    }

    override fun onDestroyView() {
        queryJob?.cancel()
        queryJob = null
        composeView?.disposeComposition()
        composeView = null
        super.onDestroyView()
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }
}
