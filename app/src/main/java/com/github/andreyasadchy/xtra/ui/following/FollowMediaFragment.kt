package com.github.andreyasadchy.xtra.ui.following

/**
 * Following screen for users who turned off "Use tabs for the Following page":
 * the same Compose scaffold as [FollowPagerFragment] with a dropdown tab chooser
 * instead of a tab row, and no swipe gesture between tabs.
 */
class FollowMediaFragment : BaseFollowFragment() {

    override val useTabs = false
}
