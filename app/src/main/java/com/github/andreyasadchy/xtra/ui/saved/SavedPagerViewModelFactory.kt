package com.github.andreyasadchy.xtra.ui.saved

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [SavedPagerViewModel] (`:core:database` androidMain). */
val SavedPagerViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        SavedPagerViewModel(
            contentResolver = application.contentResolver,
            offlineVideosRepository = application.xtraModule.offlineVideosRepository,
        )
    }
}
