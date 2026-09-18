package com.github.andreyasadchy.xtra.ui.following

/**
 * Following screen showing the configured tabs as a tab row. The scaffold,
 * ViewModels, sort/filter handling and navigation live in [BaseFollowFragment];
 * this destination only picks the tab selector.
 */
class FollowPagerFragment : BaseFollowFragment() {

    override val useTabs = true
}
