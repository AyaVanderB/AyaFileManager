package com.example.ayafilemanager.domain.model

import android.net.Uri

/** A UI-safe projection of a MediaStore entry. [id] is the stable content URI string. */
data class FileNode(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val isDirectory: Boolean,
    val iconRes: Int? = null,
    val extension: String = "",
    val type: FileType = FileType.OTHER,
) {
    val contentUri: Uri? get() = if (id.startsWith("content://")) Uri.parse(id) else Uri.fromFile(java.io.File(path))
}

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER
}

enum class FileSort { RELEVANCE, MODIFIED_DESC, SIZE_DESC, NAME_ASC }

/** Default is deliberately strict: a result's original filename must contain every keyword. */
enum class SearchMode {
    NAME_CONTAINS,
    NAME_PREFIX,
    PINYIN_CONTAINS,
    PINYIN_INITIALS_PREFIX,
    EXTENSION_EXACT,
}

data class FileSearchQuery(
    val text: String = "",
    val searchMode: SearchMode = SearchMode.NAME_CONTAINS,
    val extensions: Set<String> = emptySet(),
    val type: FileType? = null,
    val minSizeBytes: Long? = null,
    val modifiedAfterMillis: Long? = null,
    val sort: FileSort = FileSort.RELEVANCE,
    val limit: Int = 300,
    val filesOnly: Boolean = false,
)

data class IndexingReport(
    val indexedFileCount: Int,
    val elapsedMillis: Long,
)
