package com.example.ayafilemanager.data.directory

import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileType
import com.example.ayafilemanager.domain.repository.DirectoryBrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Direct, one-level directory reader used only after the user grants all-files access. */
class AndroidDirectoryBrowserRepository(private val dao: com.example.ayafilemanager.data.local.FileIndexDao) : DirectoryBrowserRepository {
    override val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    override suspend fun childrenOf(path: String): List<FileNode> = withContext(Dispatchers.IO) {
        val directory = File(path)
        require(directory.isDirectory && directory.canRead()) { "目录不存在或没有读取权限" }
        val nodes = ArrayList<FileNode>()
        val children = directory.listFiles() ?: error("无法读取目录，请检查存储权限")
        children.forEach {
            kotlin.coroutines.coroutineContext.ensureActive()
            nodes += toNode(it)
        }
        nodes.sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    override fun parentOf(path: String): String? {
        if (path == rootPath) return null
        return File(path).parent?.takeIf { it != "/" }
    }

    private suspend fun toNode(file: File): FileNode {
        val extension = file.extension.lowercase(Locale.ROOT)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
        return FileNode(
            id = file.toURI().toString(),
            name = file.name,
            path = file.absolutePath,
            size = if (file.isDirectory) dao.directorySize(file.path) ?: -1L else file.length(),
            mimeType = mimeType,
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            extension = extension,
            type = classify(mimeType, extension),
        )
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
        val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
        val DOCUMENTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub")
    }
}
