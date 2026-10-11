package com.example.ayafilemanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface FileIndexDao {
    @RawQuery(observedEntities = [FileIndexEntity::class])
    fun observeSearch(query: SupportSQLiteQuery): Flow<List<FileIndexEntity>>

    @Upsert
    suspend fun upsertFiles(files: List<FileIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsRows(rows: List<FileSearchFtsEntity>)

    @Query("DELETE FROM file_search_fts WHERE rowid IN (:rowIds)")
    suspend fun deleteFtsRows(rowIds: List<Long>)

    @Query("DELETE FROM file_index WHERE indexedAt < :scanStartedAt")
    suspend fun deleteStaleFiles(scanStartedAt: Long)

    @Query("DELETE FROM file_search_fts WHERE rowid NOT IN (SELECT rowId FROM file_index)")
    suspend fun deleteOrphanedFtsRows()

    @Query("UPDATE file_index SET size = :size WHERE path = :path AND isDirectory = 1")
    suspend fun updateDirectorySize(path: String, size: Long)

    @Query("SELECT size FROM file_index WHERE path = :path AND isDirectory = 1 LIMIT 1")
    suspend fun directorySize(path: String): Long?

    @Transaction
    suspend fun updateDirectorySizes(sizes: Map<String, Long>) {
        sizes.forEach { (path, size) -> updateDirectorySize(path, size) }
    }

    @Transaction
    suspend fun replaceBatch(files: List<FileIndexEntity>) {
        if (files.isEmpty()) return
        upsertFiles(files)
        deleteFtsRows(files.map(FileIndexEntity::rowId))
        insertFtsRows(files.map { file ->
            FileSearchFtsEntity(
                rowId = file.rowId,
                normalizedName = file.normalizedName,
                nameNgrams = file.nameNgrams,
                pinyin = file.pinyin,
                pinyinInitials = file.pinyinInitials,
                extension = file.extension,
            )
        })
    }

    @Transaction
    suspend fun pruneAfterFullScan(scanStartedAt: Long) {
        deleteStaleFiles(scanStartedAt)
        deleteOrphanedFtsRows()
    }
}
