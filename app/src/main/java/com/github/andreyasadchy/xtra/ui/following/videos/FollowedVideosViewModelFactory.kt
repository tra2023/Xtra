package com.github.andreyasadchy.xtra.ui.following.videos

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [FollowedVideosViewModel] (`:core:database`). */
val FollowedVideosViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        FollowedVideosViewModel(
            settings = application.xtraSettings(),
            channelSortRepository = xtraModule.channelSortRepository,
            playerRepository = xtraModule.playerRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            videoBookmarker = xtraModule.videoBookmarker,
        )
    }
}
