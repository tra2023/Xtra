package com.github.andreyasadchy.xtra.ui.channel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs

/**
 * Android factory for the shared [ChannelPagerViewModel] (`:core:database`). It owns the one
 * Android-specific concern: reading the navigation arguments out of `SavedStateHandle`.
 */
val ChannelPagerViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        val args = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
        ChannelPagerViewModel(
            localChannelFollowsRepository = xtraModule.localChannelFollowsRepository,
            offlineVideosRepository = xtraModule.offlineVideosRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            notificationsRepository = xtraModule.notificationsRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            xtraHttpClient = xtraModule.xtraHttpClient,
            channelId = args.channelId,
            channelLogin = args.channelLogin,
        )
    }
}
