package com.github.andreyasadchy.xtra.ui.game

/**
 * Game details screen for users who turned off "Use tabs for the Games page":
 * the same Compose scaffold as [GamePagerFragment] (collapsing banner, sort row,
 * shared paging lists) with a dropdown tab chooser instead of a tab row, and no
 * swipe gesture between tabs.
 */
class GameMediaFragment : BaseGameFragment() {

    override val useTabs = false
}
