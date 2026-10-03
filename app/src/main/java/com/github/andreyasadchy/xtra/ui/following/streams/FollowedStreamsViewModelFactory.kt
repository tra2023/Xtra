package com.github.andreyasadchy.xtra.ui.following.streams

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [FollowedStreamsViewModel] (`:core:database`). */
val FollowedStreamsViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        FollowedStreamsViewModel(
            settings = application.xtraSettings(),
            localChannelFollowsRepository = xtraModule.localChannelFollowsRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
        )
    }
}
