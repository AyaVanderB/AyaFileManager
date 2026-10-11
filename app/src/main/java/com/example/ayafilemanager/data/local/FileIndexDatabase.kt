package com.example.ayafilemanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [FileIndexEntity::class, FileSearchFtsEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FileIndexDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}
