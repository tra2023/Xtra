package com.github.andreyasadchy.xtra.ui.search.videos

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

/** Android factory for the shared [VideoSearchViewModel] (`:core:ui`). */
val VideoSearchViewModelFactory = viewModelFactory {
    initializer {
        val application = (this[APPLICATION_KEY] as XtraApp)
        val xtraModule = application.xtraModule
        VideoSearchViewModel(
            settings = AndroidXtraSettings(application.prefs(), application.tokenPrefs()),
            recentSearchesRepository = xtraModule.recentSearchesRepository,
            playerRepository = xtraModule.playerRepository,
            bookmarksRepository = xtraModule.bookmarksRepository,
            graphQLRepository = xtraModule.graphQLRepository,
            helixRepository = xtraModule.helixRepository,
            videoBookmarker = xtraModule.videoBookmarker,
        )
    }
}
