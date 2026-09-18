package com.github.andreyasadchy.xtra.ui.game

/**
 * Game details screen showing the configured game tabs as a tab row. The
 * scaffold, ViewModels, sort/filter handling and navigation all live in
 * [BaseGameFragment]; this destination only picks the tab selector.
 */
class GamePagerFragment : BaseGameFragment() {

    override val useTabs = true
}
