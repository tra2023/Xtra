package com.github.andreyasadchy.xtra.ui.search

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [SearchPagerViewModel] (`:core:ui`). */
val SearchPagerViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        SearchPagerViewModel(application.xtraModule.graphQLRepository)
    }
}
