package com.example.ayafilemanager.domain.repository

import com.example.ayafilemanager.domain.model.FileNode

/** Reads one directory level at a time; it never recursively enumerates storage. */
interface DirectoryBrowserRepository {
    val rootPath: String
    suspend fun childrenOf(path: String): List<FileNode>
    fun parentOf(path: String): String?
}
