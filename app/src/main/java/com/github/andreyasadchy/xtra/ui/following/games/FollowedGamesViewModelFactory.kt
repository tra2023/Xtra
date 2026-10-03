package com.github.andreyasadchy.xtra.ui.following.games

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [FollowedGamesViewModel] (`:core:database`). */
val FollowedGamesViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        FollowedGamesViewModel(
            settings = application.xtraSettings(),
            localGameFollowsRepository = xtraModule.localGameFollowsRepository,
            graphQLRepository = xtraModule.graphQLRepository,
        )
    }
}
