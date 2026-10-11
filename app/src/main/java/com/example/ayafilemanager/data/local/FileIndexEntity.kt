package com.example.ayafilemanager.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ayafilemanager.domain.model.FileType

@Entity(
    tableName = "file_index",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["extension"]),
        Index(value = ["type"]),
        Index(value = ["lastModified"]),
        Index(value = ["size"]),
        Index(value = ["path"]),
    ],
)
data class FileIndexEntity(
    @PrimaryKey val rowId: Long,
    val sourceUri: String,
    val displayName: String,
    val normalizedName: String,
    val nameNgrams: String,
    val pinyin: String,
    val pinyinInitials: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val extension: String,
    val type: FileType,
    val indexedAt: Long,
    val isDirectory: Boolean = false,
)

/**
 * A separate FTS table deliberately mirrors the searchable fields. Keeping it explicit makes
 * batch upserts and stale-row cleanup deterministic instead of relying on SQLite triggers.
 *
 * Room's stable Android artifact exposes FTS3/FTS4. The query compiler is isolated so it can be
 * switched to Room 3 + bundled SQLite FTS5 when that runtime becomes a product requirement.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4, 5, 6, 7, 8, 9, 10],
)
@Entity(tableName = "file_search_fts")
data class FileSearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val normalizedName: String,
    val nameNgrams: String,
    val pinyin: String,
    val pinyinInitials: String,
    val extension: String,
)
