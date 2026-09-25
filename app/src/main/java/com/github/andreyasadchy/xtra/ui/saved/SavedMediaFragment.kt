package com.github.andreyasadchy.xtra.ui.saved

/**
 * Saved screen showing the configured tabs as a dropdown chooser. The scaffold,
 * import launchers, navigation and dialogs live in [BaseSavedFragment];
 * this destination only picks the tab selector.
 */
class SavedMediaFragment : BaseSavedFragment() {

    override val useTabs = false
}
