package com.github.andreyasadchy.xtra.model.navigation

import kotlinx.serialization.Serializable

/**
 * Shared destinations for Android and JVM desktop. No navigation library yet
 * (avoids pulling `navigation-compose`, which has no stable JVM-desktop artifact
 * at `navigation = 2.10.1`); a 30-line [XtraNavigator] below is enough for the
 * first two screens. Swap for navigation3 / Voyager later without touching screens.
 */
@Serializable
sealed interface XtraRoute {
    @Serializable
    data object Games : XtraRoute

    @Serializable
    data object TopStreams : XtraRoute
}
