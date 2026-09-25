package com.github.andreyasadchy.xtra.settings

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.prefs.Preferences

/**
 * JVM desktop bridge backed by `java.util.prefs`. Single store (no split
 * default/token files like Android). Values are seeded from the same [C] keys
 * so login tokens can be copy-pasted between platforms during development.
 */
class JvmXtraSettings(
    private val prefs: Preferences = Preferences.userRoot().node("com/github/andreyasadchy/xtra"),
) : XtraSettings {

    override fun getString(key: String, default: String?): String? =
        prefs.get(key, default)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun getInt(key: String, default: Int): Int =
        prefs.get(key, null)?.toIntOrNull() ?: prefs.getInt(key, default)

    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }

    override fun remove(key: String) {
        prefs.remove(key)
    }

    override fun observeChanges(): Flow<String> = callbackFlow {
        val listener = java.util.prefs.PreferenceChangeListener { event ->
            trySend(event.key)
        }
        prefs.addPreferenceChangeListener(listener)
        awaitClose { prefs.removePreferenceChangeListener(listener) }
    }
}
