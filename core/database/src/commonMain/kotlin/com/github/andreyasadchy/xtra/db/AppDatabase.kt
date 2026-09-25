package com.github.andreyasadchy.xtra.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.PlaybackState
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.BookmarkIgnoredUser
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.LocalChannelFollow
import com.github.andreyasadchy.xtra.model.ui.LocalGameFollow
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        OfflineVideo::class,
        RecentEmote::class,
        VideoPosition::class,
        LocalChannelFollow::class,
        LocalGameFollow::class,
        Bookmark::class,
        BookmarkIgnoredUser::class,
        ChannelSort::class,
        GameSort::class,
        ShownNotification::class,
        NotificationUser::class,
        SavedFilter::class,
        RecentSearch::class,
        PlaybackState::class,
    ],
    version = 43,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun offlineVideos(): OfflineVideosDao
    abstract fun recentEmotes(): RecentEmotesDao
    abstract fun videoPositions(): VideoPositionsDao
    abstract fun localChannelFollows(): LocalChannelFollowsDao
    abstract fun localGameFollows(): LocalGameFollowsDao
    abstract fun bookmarks(): BookmarksDao
    abstract fun bookmarkIgnoredUsers(): BookmarkIgnoredUsersDao
    abstract fun channelSort(): ChannelSortDao
    abstract fun gameSort(): GameSortDao
    abstract fun shownNotifications(): ShownNotificationsDao
    abstract fun notificationUsers(): NotificationUsersDao
    abstract fun savedFilters(): SavedFiltersDao
    abstract fun recentSearches(): RecentSearchesDao
    abstract fun playbackStates(): PlaybackStatesDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
}
