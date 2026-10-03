package com.github.andreyasadchy.xtra.ui.channel.suggestions

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/**
 * Android factory for the shared [ChannelSuggestionsViewModel] (`:core:ui`). It owns the one
 * Android-specific concern: reading the navigation arguments out of `SavedStateHandle`.
 */
val ChannelSuggestionsViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        ChannelSuggestionsViewModel(
            settings = application.xtraSettings(),
            channelLogin = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle).channelLogin,
            graphQLRepository = xtraModule.graphQLRepository,
        )
    }
}
