package com.github.andreyasadchy.xtra.ui.game.streams

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentArgs

/** Android factory for the shared [GameStreamsViewModel] (`:core:database`). */
val GameStreamsViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
        GameStreamsViewModel(
            settings = application.xtraSettings(),
            gameId = args.gameId,
            gameSlug = args.gameSlug,
            gameName = args.gameName,
            gameSortRepository = xtraModule.gameSortRepository,
            savedFiltersRepository = xtraModule.savedFiltersRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
        )
    }
}
