package com.example.ayafilemanager.data.directory

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import com.example.ayafilemanager.data.local.FileIndexDao
import com.example.ayafilemanager.data.local.FileIndexEntity
import com.example.ayafilemanager.data.search.PinyinNormalizer
import com.example.ayafilemanager.domain.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/** Complete accessible shared-storage catalogue. Traversal happens only during background refresh. */
class StorageFileIndexer(private val context: Context, private val dao: FileIndexDao, private val normalizer: PinyinNormalizer) {
    fun canScan() = if (Build.VERSION.SDK_INT < 30) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else Environment.isExternalStorageManager()

    suspend fun rebuild(): Int = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val roots = mutableSetOf(Environment.getExternalStorageDirectory())
        if (Build.VERSION.SDK_INT >= 30) {
            context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNullTo(roots) { it.directory }
        }
        val stack = ArrayDeque<File>().apply { addAll(roots) }
        val visited = HashSet<String>()
        val directorySizes = HashMap<String, Long>()
        val batch = ArrayList<FileIndexEntity>(400)
        var count = 0
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val file = stack.removeLast()
            if (Files.isSymbolicLink(file.toPath())) continue
            if (!visited.add(file.absolutePath)) continue
            if (file.isDirectory) file.listFiles()?.let { stack.addAll(it) }
            if (file in roots) continue
            if (file.isFile) {
                val bytes = file.length()
                var parent = file.parentFile
                while (parent != null && parent !in roots) {
                    directorySizes[parent.path] = (directorySizes[parent.path] ?: 0L) + bytes
                    parent = parent.parentFile
                }
            }
            val name = file.name
            val extension = if (file.isDirectory) "" else file.extension.lowercase(java.util.Locale.ROOT)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
            val type = when {
                mime.startsWith("image/") -> FileType.IMAGE
                mime.startsWith("video/") -> FileType.VIDEO
                mime.startsWith("audio/") -> FileType.AUDIO
                extension == "apk" -> FileType.APK
                extension in setOf("zip", "rar", "7z", "tar", "gz") -> FileType.ARCHIVE
                mime.startsWith("text/") || extension in setOf("pdf", "doc", "docx", "xlsx", "xls", "ppt", "pptx", "epub") -> FileType.DOCUMENT
                else -> FileType.OTHER
            }
            var hash = -3750763034362895579L
            file.absolutePath.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
            batch += FileIndexEntity(
                rowId = (hash and Long.MAX_VALUE).coerceAtLeast(1), sourceUri = file.toURI().toString(),
                displayName = name, normalizedName = normalizer.normalize(name), nameNgrams = normalizer.nameNgrams(name),
                pinyin = normalizer.pinyin(name), pinyinInitials = normalizer.initials(name),
                path = file.absolutePath, size = if (file.isDirectory) 0 else file.length(), mimeType = mime,
                lastModified = file.lastModified(), extension = extension, type = type, indexedAt = stamp, isDirectory = file.isDirectory,
            )
            count++
            if (batch.size == 400) { dao.replaceBatch(batch); batch.clear() }
        }
        dao.replaceBatch(batch)
        directorySizes.entries.chunked(400).forEach { entries ->
            coroutineContext.ensureActive()
            dao.updateDirectorySizes(entries.associate { it.key to it.value })
        }
        dao.pruneAfterFullScan(stamp)
        count
    }
}
