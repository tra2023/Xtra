package com.github.andreyasadchy.xtra.ui.game

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs

/**
 * Android factory for the shared [GamePagerViewModel] (`:core:database`). It owns the one
 * Android-specific concern: reading the navigation arguments out of `SavedStateHandle`.
 */
val GamePagerViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
        GamePagerViewModel(
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            localGameFollowsRepository = xtraModule.localGameFollowsRepository,
            xtraHttpClient = xtraModule.xtraHttpClient,
            gameId = args.gameId,
            gameSlug = args.gameSlug,
            gameName = args.gameName,
        )
    }
}
