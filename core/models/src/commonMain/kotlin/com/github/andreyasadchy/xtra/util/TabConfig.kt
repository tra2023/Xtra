package com.github.andreyasadchy.xtra.util

/**
 * Tab-row configuration parsing shared by the pager hosts (Following, game,
 * Saved, channel, search), which all duplicated the same preference merge.
 *
 * Entries look like `"key:default:enabled"` (see `C.DEFAULT_*_TABS`): a stored
 * preference keeps the user's order and toggles, while entries added by
 * updates are merged back in at their default positions and unknown entries
 * are dropped.
 */

/** Keys of the enabled tabs, in order. May be empty if everything is toggled off. */
fun parseEnabledTabs(pref: String?, defaults: String): List<String> {
    val defaultTabs = defaults.split(',')
    val tabList = if (pref != null) {
        val list = pref.split(',').filter { item ->
            defaultTabs.find { it.first() == item.first() } != null
        }.toMutableList()
        defaultTabs.forEachIndexed { index, item ->
            if (list.find { it.first() == item.first() } == null) {
                list.add(index, item)
            }
        }
        list
    } else {
        defaultTabs
    }
    return tabList.mapNotNull {
        val split = it.split(':')
        val key = split[0]
        val enabled = split[2] != "0"
        if (enabled) key else null
    }
}

/**
 * Index of the default tab (the entry whose middle flag isn't `"0"`),
 * falling back through [fallbacks] and finally to `0`.
 */
fun defaultTabIndex(tabs: List<String>, pref: String?, defaults: String, vararg fallbacks: String): Int {
    val tabList = pref?.split(',') ?: defaults.split(',')
    val defaultItem = tabList.find { it.split(':')[1] != "0" }?.split(':')?.get(0)
    return (listOfNotNull(defaultItem) + fallbacks.toList())
        .firstNotNullOfOrNull { tabs.indexOf(it).takeIf { index -> index != -1 } }
        ?: 0
}
