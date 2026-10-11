package com.example.ayafilemanager.domain.repository

import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileSearchQuery
import com.example.ayafilemanager.domain.model.IndexingReport
import kotlinx.coroutines.flow.Flow

interface FileSearchRepository {
    /** Emits immediately from Room and re-emits only when the local index changes. */
    fun search(query: FileSearchQuery): Flow<List<FileNode>>

    /** MediaStore is read only during this explicit rebuild; no recursive file walk occurs. */
    suspend fun rebuildIndex(): IndexingReport
}
