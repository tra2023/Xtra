package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [PlayerViewModel] (`:core:database`). */
val PlayerViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        PlayerViewModel(
            videoBookmarker = xtraModule.videoBookmarker,
            xtraHttpClient = xtraModule.xtraHttpClient,
            json = xtraModule.json,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            playerRepository = xtraModule.playerRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            localChannelFollowsRepository = xtraModule.localChannelFollowsRepository,
            notificationsRepository = xtraModule.notificationsRepository,
        )
    }
}
