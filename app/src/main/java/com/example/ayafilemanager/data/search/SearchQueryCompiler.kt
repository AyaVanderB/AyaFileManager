package com.example.ayafilemanager.data.search

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.ayafilemanager.domain.model.FileSearchQuery
import com.example.ayafilemanager.domain.model.FileSort
import com.example.ayafilemanager.domain.model.SearchMode
import java.util.Locale

/**
 * Search is strict by default: NAME_CONTAINS returns only rows whose original filename contains
 * every typed keyword, case-insensitively. Correctness is guaranteed by the filename predicate;
 * no device-specific FTS tokenizer is allowed to exclude a valid row.
 */
class SearchQueryCompiler(private val normalizer: PinyinNormalizer) {
    fun compile(query: FileSearchQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()
        appendSearchPredicate(query, where, args)
        if (query.filesOnly) where += "file.isDirectory = 0"

        if (query.extensions.isNotEmpty()) {
            val extensions = query.extensions.map { it.removePrefix(".").lowercase(Locale.ROOT) }
            where += "file.extension IN (${extensions.joinToString { "?" }})"
            args.addAll(extensions)
        }
        query.type?.let { where += "file.type = ?"; args += it.name }
        query.minSizeBytes?.let { where += "file.size >= ?"; args += it }
        query.modifiedAfterMillis?.let { where += "file.lastModified >= ?"; args += it }

        val orderBy = when (query.sort) {
            FileSort.RELEVANCE, FileSort.MODIFIED_DESC -> "file.lastModified DESC"
            FileSort.SIZE_DESC -> "file.size DESC"
            FileSort.NAME_ASC -> "file.normalizedName COLLATE NOCASE ASC"
        }
        val predicate = where.joinToString(" AND ").takeIf(String::isNotBlank)?.let { " WHERE $it" }.orEmpty()
        return SimpleSQLiteQuery(
            "SELECT file.* FROM file_index AS file$predicate ORDER BY $orderBy" + if (query.limit > 0) " LIMIT ${query.limit}" else "",
            args.toTypedArray(),
        )
    }

    private fun appendSearchPredicate(query: FileSearchQuery, where: MutableList<String>, args: MutableList<Any>) {
        if (query.text.isBlank()) return
        when (query.searchMode) {
            SearchMode.NAME_CONTAINS -> {
                val keywords = nameKeywords(query.text)
                if (keywords.isEmpty()) return
                keywords.forEach { keyword ->
                    where += "file.normalizedName LIKE ?"
                    args += "%$keyword%"
                }
            }
            SearchMode.NAME_PREFIX -> {
                val term = normalizer.normalize(query.text)
                where += "file.normalizedName LIKE ?"
                args += "$term%"
            }
            SearchMode.PINYIN_CONTAINS -> {
                val term = normalizer.pinyin(query.text).filterNot(Char::isWhitespace)
                where += "REPLACE(file.pinyin, ' ', '') LIKE ?"
                args += "%$term%"
            }
            SearchMode.PINYIN_INITIALS_PREFIX -> {
                val term = normalizer.normalize(query.text).filter(Char::isLetterOrDigit)
                where += "file.pinyinInitials LIKE ?"
                args += "$term%"
            }
            SearchMode.EXTENSION_EXACT -> {
                where += "file.extension = ?"
                args += query.text.trim().removePrefix(".").lowercase(Locale.ROOT)
            }
        }
    }

    private fun nameKeywords(text: String): List<String> = normalizer.normalize(text)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotBlank)
        .take(8)
}
