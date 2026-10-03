package com.github.andreyasadchy.xtra.ui.games

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.browse.GamesBrowseController
import com.github.andreyasadchy.xtra.settings.AndroidXtraSettings
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android shell around the shared [GamesBrowseController]: the paging logic itself lives in
 * `:core:database`, this only supplies the Android settings bridge and `viewModelScope`.
 */
class GamesViewModel(
    applicationContext: Context,
    graphQLRepository: GraphQLRepository,
    helixRepository: HelixRepository,
) : ViewModel() {

    private val controller = GamesBrowseController(
        scope = viewModelScope,
        settings = AndroidXtraSettings(applicationContext.prefs(), applicationContext.tokenPrefs()),
        graphQLRepository = graphQLRepository,
        helixRepository = helixRepository,
    )

    val flow = controller.flow

    val filter: MutableStateFlow<GamesBrowseController.GamesFilter?>
        get() = controller.filter

    val filtersText = MutableStateFlow<CharSequence?>(null)

    val tags: Array<Tag>
        get() = controller.tags

    fun setFilter(tags: Array<Tag>?) {
        controller.setFilter(tags)
    }

    companion object {
        val GamesViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                GamesViewModel(application.applicationContext, xtraModule.graphQLRepository, xtraModule.helixRepository)
            }
        }
    }
}
