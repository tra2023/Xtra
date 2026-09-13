package com.github.andreyasadchy.xtra.shared.settings

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

    fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }
}
