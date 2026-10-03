package com.github.andreyasadchy.xtra.ui.following.channels

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [FollowedChannelsViewModel] (`:core:database`). */
val FollowedChannelsViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        FollowedChannelsViewModel(
            settings = application.xtraSettings(),
            channelSortRepository = xtraModule.channelSortRepository,
            localChannelFollowsRepository = xtraModule.localChannelFollowsRepository,
            offlineVideosRepository = xtraModule.offlineVideosRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
        )
    }
}
