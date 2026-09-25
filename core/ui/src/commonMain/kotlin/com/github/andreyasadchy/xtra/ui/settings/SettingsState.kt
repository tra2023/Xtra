package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Reactive SharedPreferences-backed state helpers for the Compose settings
 * screens. Reads come from [LocalXtraSettings]; writes go straight back
 * through [XtraSettings.putString]/[putBoolean]/[putInt] so the desktop and
 * Android apps share identical behavior without a Context in commonMain.
 *
 * Each helper subscribes to [XtraSettings.observeChanges] filtered by [key]
 * so external edits (backup restore, drag-list dialog) are reflected.
 */
@Composable
fun rememberBooleanSetting(key: String, default: Boolean): MutableState<Boolean> {
    val settings = LocalXtraSettings.current
    val state = remember(settings, key) { mutableStateOf(settings.getBoolean(key, default)) }
    LaunchedEffect(settings, key, default) {
        settings.observeChanges().collect { changed ->
            if (changed == key) state.value = settings.getBoolean(key, default)
        }
    }
    return remember(settings, key, default) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(value) {
                    settings.putBoolean(key, value)
                    state.value = value
                }

            override fun component1(): Boolean = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberStringSetting(key: String, default: String?): MutableState<String?> {
    val settings = LocalXtraSettings.current
    val state = remember(settings, key) { mutableStateOf(settings.getString(key, default)) }
    LaunchedEffect(settings, key, default) {
        settings.observeChanges().collect { changed ->
            if (changed == key) state.value = settings.getString(key, default)
        }
    }
    return remember(settings, key, default) {
        object : MutableState<String?> {
            override var value: String?
                get() = state.value
                set(value) {
                    settings.putString(key, value)
                    state.value = value
                }

            override fun component1(): String? = value
            override fun component2(): (String?) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntSetting(key: String, default: Int): MutableState<Int> {
    val settings = LocalXtraSettings.current
    val state = remember(settings, key) { mutableIntStateOf(settings.getInt(key, default)) }
    LaunchedEffect(settings, key, default) {
        settings.observeChanges().collect { changed ->
            if (changed == key) state.intValue = settings.getInt(key, default)
        }
    }
    return remember(settings, key, default) {
        object : MutableState<Int> {
            override var value: Int
                get() = state.intValue
                set(value) {
                    settings.putInt(key, value)
                    state.intValue = value
                }

            override fun component1(): Int = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}
