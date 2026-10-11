package com.example.ayafilemanager.data.repository

import com.example.ayafilemanager.data.local.FileIndexDao
import com.example.ayafilemanager.data.local.FileIndexEntity
import com.example.ayafilemanager.data.mediastore.MediaStoreFileIndexer
import com.example.ayafilemanager.data.search.SearchQueryCompiler
import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileSearchQuery
import com.example.ayafilemanager.domain.model.IndexingReport
import com.example.ayafilemanager.domain.repository.FileSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class DefaultFileSearchRepository(
    private val dao: FileIndexDao,
    private val indexer: MediaStoreFileIndexer,
    private val queryCompiler: SearchQueryCompiler,
    private val storageIndexer: com.example.ayafilemanager.data.directory.StorageFileIndexer,
) : FileSearchRepository {
    override fun search(query: FileSearchQuery): Flow<List<FileNode>> =
        dao.observeSearch(queryCompiler.compile(query)).map { rows -> rows.map { it.toNode() } }

    override suspend fun rebuildIndex(): IndexingReport = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val count = if (storageIndexer.canScan()) storageIndexer.rebuild() else indexer.rebuild()
        IndexingReport(indexedFileCount = count, elapsedMillis = System.currentTimeMillis() - startedAt)
    }

    private fun FileIndexEntity.toNode() = FileNode(
        id = sourceUri,
        name = displayName,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = lastModified,
        isDirectory = isDirectory,
        extension = extension,
        type = type,
    )
}
