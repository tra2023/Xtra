package com.github.andreyasadchy.xtra.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "xtra-database.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    ).fallbackToDestructiveMigration(dropAllTables = true)
}
