package com.github.andreyasadchy.xtra.ui.saved.filters

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [FiltersViewModel] (`:core:database`). */
val FiltersViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        FiltersViewModel(application.xtraModule.savedFiltersRepository)
    }
}
