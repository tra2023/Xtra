package com.github.andreyasadchy.xtra.ui.team

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings
import com.github.andreyasadchy.xtra.ui.team.TeamFragmentArgs

/**
 * Android factory for the shared [TeamViewModel] (`:core:ui`). It owns the one Android-specific
 * concern: reading the navigation arguments out of `SavedStateHandle`.
 */
val TeamViewModelFactory = viewModelFactory {
    initializer {
        val savedStateHandle = createSavedStateHandle()
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        TeamViewModel(
            settings = application.xtraSettings(),
            teamName = TeamFragmentArgs.fromSavedStateHandle(savedStateHandle).teamName,
            graphQLRepository = xtraModule.graphQLRepository,
        )
    }
}
