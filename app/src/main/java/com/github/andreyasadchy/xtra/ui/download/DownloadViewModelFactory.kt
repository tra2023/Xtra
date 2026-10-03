package com.github.andreyasadchy.xtra.ui.download

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [DownloadViewModel] (`:core:database` androidMain). */
val DownloadViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        DownloadViewModel(xtraModule.xtraHttpClient, xtraModule.playerRepository)
    }
}
