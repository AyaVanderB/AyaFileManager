package com.example.ayafilemanager.data.search

import android.icu.text.Transliterator
import android.os.Build
import java.text.Normalizer
import java.util.Locale

/** Uses the platform ICU transliterator, so no large pinyin dictionary is loaded at app start. */
class PinyinNormalizer {
    private val hanToLatin by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Transliterator.getInstance("Han-Latin; Latin-ASCII") else null
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .trim()

    fun pinyin(value: String): String = normalize(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) hanToLatin?.transliterate(value) ?: value else value)
        .replace(NON_LETTER_OR_DIGIT, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    fun initials(value: String): String = pinyin(value)
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(separator = "") { it.take(1) }

    /** Produces 1–3 character terms for fast candidate lookup of arbitrary filename substrings. */
    fun nameNgrams(value: String): String {
        val compact = normalize(value).filter(Char::isLetterOrDigit)
        if (compact.isBlank()) return ""
        return buildSet {
            compact.indices.forEach { start ->
                for (length in 1..minOf(3, compact.length - start)) add(compact.substring(start, start + length))
            }
        }.joinToString(" ")
    }

    private companion object {
        val COMBINING_MARKS = Regex("\\p{M}+")
        val NON_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")
        val MULTIPLE_SPACES = Regex("\\s+")
    }
}
