package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [PlayerViewerListViewModel] (`:core:ui`). */
val PlayerViewerListViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        PlayerViewerListViewModel(application.xtraModule.graphQLRepository)
    }
}
