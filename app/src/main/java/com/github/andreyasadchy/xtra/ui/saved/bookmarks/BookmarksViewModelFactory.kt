package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [BookmarksViewModel] (`:core:database`). */
val BookmarksViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        BookmarksViewModel(
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            channelSortRepository = xtraModule.channelSortRepository,
            playerRepository = xtraModule.playerRepository,
            xtraHttpClient = xtraModule.xtraHttpClient,
        )
    }
}
