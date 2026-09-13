package com.github.andreyasadchy.xtra.shared.settings

import android.content.SharedPreferences

/**
 * Android bridge: `:app` keeps two files (`prefs()` default + `tokenPrefs()`
 * "prefs2"). Token keys are read from [tokenPrefs] first, everything else from
 * [prefs]. Used by `GamesViewModel`/`TopStreamsViewModel` wrappers to build the
 * shared browse controllers without passing Context into commonMain.
 */
class AndroidXtraSettings(
    private val prefs: SharedPreferences,
    private val tokenPrefs: SharedPreferences,
) : XtraSettings {

    // Keys stored in the "prefs2" token file in :app (see ContextExtensions.kt).
    private val tokenKeys = setOf(
        com.github.andreyasadchy.xtra.util.C.GQL_TOKEN2,
        com.github.andreyasadchy.xtra.util.C.GQL_HEADERS,
        com.github.andreyasadchy.xtra.util.C.TOKEN,
        com.github.andreyasadchy.xtra.util.C.INTEGRITY_EXPIRATION,
    )

    override fun getString(key: String, default: String?): String? {
        return if (key in tokenKeys) {
            tokenPrefs.getString(key, prefs.getString(key, default))
        } else {
            prefs.getString(key, default)
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }
}
