package com.github.andreyasadchy.xtra.ui.channel.clips

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [ChannelClipsViewModel] (`:core:database`). */
val ChannelClipsViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        val args = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
        ChannelClipsViewModel(
            settings = application.xtraSettings(),
            channelId = args.channelId,
            channelLogin = args.channelLogin,
            channelSortRepository = xtraModule.channelSortRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
        )
    }
}
