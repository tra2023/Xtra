package com.github.andreyasadchy.xtra.settings

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
    // Reads fall back to the default prefs file; writes are routed to tokenPrefs.
    private val tokenKeys = setOf(
        com.github.andreyasadchy.xtra.util.C.GQL_TOKEN2,
        com.github.andreyasadchy.xtra.util.C.GQL_TOKEN_WEB,
        com.github.andreyasadchy.xtra.util.C.GQL_HEADERS,
        com.github.andreyasadchy.xtra.util.C.TOKEN,
        com.github.andreyasadchy.xtra.util.C.USER_ID,
        com.github.andreyasadchy.xtra.util.C.USERNAME,
        com.github.andreyasadchy.xtra.util.C.INTEGRITY_EXPIRATION,
        com.github.andreyasadchy.xtra.util.C.UPDATE_LAST_CHECKED,
    )

    private val changeListeners = mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences>()
    private val changesFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) changesFlow.tryEmit(key)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        tokenPrefs.registerOnSharedPreferenceChangeListener(listener)
        changeListeners[listener] = prefs
    }

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

    override fun getInt(key: String, default: Int): Int {
        return try {
            prefs.getInt(key, default)
        } catch (e: ClassCastException) {
            prefs.getString(key, null)?.toIntOrNull() ?: default
        }
    }

    override fun putString(key: String, value: String?) {
        if (key in tokenKeys) {
            tokenPrefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        } else {
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
        if (key in tokenKeys) tokenPrefs.edit().remove(key).apply()
    }

    override fun observeChanges(): kotlinx.coroutines.flow.Flow<String> = changesFlow
}
