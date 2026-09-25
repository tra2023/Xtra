package com.github.andreyasadchy.xtra.model.ui

/**
 * Filter values for the saved-bookmarks sort dialog.
 *
 * Lives in `:core:models` (instead of the app `BookmarksSortDialog`) so that
 * view models, repositories and shared UI can reference the values without
 * depending on Android dialog classes.
 */
object BookmarksSort {
    const val SORT_EXPIRES_AT = "expires_at"
    const val SORT_CREATED_AT = "created_at"
    const val SORT_SAVED_AT = "saved_at"

    const val ORDER_ASC = "asc"
    const val ORDER_DESC = "desc"

    const val DEFAULT_SORT = SORT_SAVED_AT
    const val DEFAULT_ORDER = ORDER_DESC

    fun sanitizeSort(value: String?): String = when (value) {
        SORT_EXPIRES_AT, SORT_CREATED_AT, SORT_SAVED_AT -> value
        else -> DEFAULT_SORT
    }

    fun sanitizeOrder(value: String?): String = when (value) {
        ORDER_ASC, ORDER_DESC -> value
        else -> DEFAULT_ORDER
    }
}

/**
 * Filter values for the followed-channels sort dialog.
 *
 * Lives in `:core:models` (instead of the app `FollowedChannelsSortDialog`)
 * so that view models, repositories and shared UI can reference the values
 * without depending on Android dialog classes.
 */
object FollowedChannelsSort {
    const val SORT_FOLLOWED_AT = "created_at"
    const val SORT_ALPHABETICALLY = "login"
    const val SORT_LAST_BROADCAST = "last_broadcast"

    const val ORDER_ASC = "asc"
    const val ORDER_DESC = "desc"

    const val DEFAULT_SORT = SORT_LAST_BROADCAST
    const val DEFAULT_ORDER = ORDER_DESC

    fun sanitizeSort(value: String?): String = when (value) {
        SORT_FOLLOWED_AT, SORT_ALPHABETICALLY, SORT_LAST_BROADCAST -> value
        else -> DEFAULT_SORT
    }

    fun sanitizeOrder(value: String?): String = when (value) {
        ORDER_ASC, ORDER_DESC -> value
        else -> DEFAULT_ORDER
    }
}
