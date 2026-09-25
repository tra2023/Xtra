package com.github.andreyasadchy.xtra.ui.saved

/**
 * Saved screen showing the configured tabs as a tab row. The scaffold,
 * import launchers, navigation and dialogs live in [BaseSavedFragment];
 * this destination only picks the tab selector.
 */
class SavedPagerFragment : BaseSavedFragment() {

    override val useTabs = true
}
