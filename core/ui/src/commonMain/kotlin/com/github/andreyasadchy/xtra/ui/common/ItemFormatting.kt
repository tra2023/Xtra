package com.github.andreyasadchy.xtra.ui.common

/**
 * Resolves a display name from a channel name/login pair and the stored
 * `UI_NAME_DISPLAY` preference. Shared by the list rows and [streamCardState].
 */
fun displayName(name: String?, login: String?, nameDisplay: String): String? =
    if (name != null && login != null && !login.equals(name, true)) {
        when (nameDisplay) {
            "0" -> "$name($login)"
            "1" -> name
            else -> login
        }
    } else name
