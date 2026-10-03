package com.github.andreyasadchy.xtra.ui.saved.downloads

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [DownloadsViewModel] (`:core:database` androidMain). */
val DownloadsViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        DownloadsViewModel(
            contentResolver = application.contentResolver,
            offlineVideosRepository = application.xtraModule.offlineVideosRepository,
        )
    }
}
