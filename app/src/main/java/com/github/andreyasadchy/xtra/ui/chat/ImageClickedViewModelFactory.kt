package com.github.andreyasadchy.xtra.ui.chat

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/**
 * Android factory for the shared [ImageClickedViewModel] (`:core:ui`): it is the only part that
 * needs the application-scoped repository graph.
 */
val ImageClickedViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        ImageClickedViewModel(application.xtraModule.graphQLRepository)
    }
}
