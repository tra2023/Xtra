package com.github.andreyasadchy.xtra.ui.navigation

import com.github.andreyasadchy.xtra.model.navigation.XtraRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal backstack shared by Android (`NavController` wrapper later) and desktop
 * (`Window` + `XtraApp()` composable). Starts on [XtraRoute.TopStreams],
 * matching the current bottom-nav default in `:app`.
 */
class XtraNavigator(initial: XtraRoute = XtraRoute.TopStreams) {
    private val _backstack = MutableStateFlow(listOf(initial))
    val backstack: StateFlow<List<XtraRoute>> = _backstack.asStateFlow()

    val current: XtraRoute
        get() = _backstack.value.last()

    fun navigate(route: XtraRoute) {
        if (_backstack.value.lastOrNull() != route) {
            _backstack.value = _backstack.value + route
        }
    }

    /** Returns false when already at root (caller may exit the window/activity). */
    fun goBack(): Boolean {
        val stack = _backstack.value
        return if (stack.size > 1) {
            _backstack.value = stack.dropLast(1)
            true
        } else {
            false
        }
    }
}
