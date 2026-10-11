package com.example.ayafilemanager.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.ayafilemanager.data.local.FileIndexDao
import com.example.ayafilemanager.data.local.FileIndexEntity
import com.example.ayafilemanager.data.search.PinyinNormalizer
import com.example.ayafilemanager.domain.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Queries MediaStore's already-maintained catalogue. It intentionally never calls File.listFiles()
 * or recursively walks storage. A full scan is chunked to keep Room writes and memory bounded.
 */
class MediaStoreFileIndexer(
    private val context: Context,
    private val dao: FileIndexDao,
    private val pinyinNormalizer: PinyinNormalizer,
) {
    suspend fun rebuild(): Int = withContext(Dispatchers.IO) {
        val scanStartedAt = System.currentTimeMillis()
        var count = 0
        externalVolumes().forEach { volume ->
            count += indexVolume(volume, scanStartedAt)
        }
        dao.pruneAfterFullScan(scanStartedAt)
        count
    }

    private suspend fun indexVolume(volume: String, indexedAt: Long): Int {
        val uri = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        var count = 0
        val batch = ArrayList<FileIndexEntity>(BATCH_SIZE)
        context.contentResolver.query(uri, projection, pendingSelection(), null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val mediaId = cursor.getLong(idColumn)
                val sourceUri = ContentUris.withAppendedId(uri, mediaId).toString()
                val name = cursor.getString(nameColumn).orEmpty().ifBlank { "Untitled" }
                val mimeType = cursor.getString(mimeColumn).orEmpty()
                val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
                val directPath = dataColumn.takeIf { it >= 0 }?.let(cursor::getString)
                val relativePath = relativePathColumn.takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
                val path = directPath.orEmpty().ifBlank { "/$relativePath$name" }
                batch += FileIndexEntity(
                    rowId = stableRowId(sourceUri),
                    sourceUri = sourceUri,
                    displayName = name,
                    normalizedName = pinyinNormalizer.normalize(name),
                    nameNgrams = pinyinNormalizer.nameNgrams(name),
                    pinyin = pinyinNormalizer.pinyin(name),
                    pinyinInitials = pinyinNormalizer.initials(name),
                    path = path,
                    size = cursor.getLong(sizeColumn),
                    mimeType = mimeType,
                    lastModified = cursor.getLong(modifiedColumn) * 1_000L,
                    extension = extension,
                    type = classify(mimeType, extension),
                    indexedAt = indexedAt,
                )
                if (batch.size == BATCH_SIZE) {
                    dao.replaceBatch(batch)
                    count += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            dao.replaceBatch(batch)
            count += batch.size
        }
        return count
    }

    private fun externalVolumes(): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.getExternalVolumeNames(context)
    } else {
        setOf(MediaStore.VOLUME_EXTERNAL)
    }

    private fun pendingSelection(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.MediaColumns.IS_PENDING} = 0"
    } else {
        null
    }

    private fun stableRowId(sourceUri: String): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis as a signed Long.
        sourceUri.forEach { character ->
            hash = hash xor character.code.toLong()
            hash *= 0x100000001b3L
        }
        return (hash and Long.MAX_VALUE).coerceAtLeast(1L)
    }

    private fun classify(mimeType: String, extension: String): FileType = when {
        mimeType.startsWith("image/") -> FileType.IMAGE
        mimeType.startsWith("video/") -> FileType.VIDEO
        mimeType.startsWith("audio/") -> FileType.AUDIO
        extension in ARCHIVES -> FileType.ARCHIVE
        extension == "apk" -> FileType.APK
        mimeType.startsWith("text/") || extension in DOCUMENTS -> FileType.DOCUMENT
        else -> FileType.OTHER
    }

    private companion object {
        const val BATCH_SIZE = 400
        val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
        val DOCUMENTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub")
    }
}
