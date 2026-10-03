package com.github.andreyasadchy.xtra.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.xtraSettings

/** Android factory for the shared [SearchTagsViewModel] (`:core:database`). */
val SearchTagsViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        SearchTagsViewModel(
            settings = application.xtraSettings(),
            graphQLRepository = application.xtraModule.graphQLRepository,
        )
    }
}
