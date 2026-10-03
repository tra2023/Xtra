package com.github.andreyasadchy.xtra.ui.channel.about

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp

/** Android factory for the shared [ChannelAboutViewModel] (`:core:ui`). */
val ChannelAboutViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        ChannelAboutViewModel(application.xtraModule.graphQLRepository)
    }
}
