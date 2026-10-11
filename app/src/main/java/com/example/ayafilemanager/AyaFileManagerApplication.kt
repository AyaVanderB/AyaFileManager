package com.example.ayafilemanager

import android.app.Application
import androidx.room.Room
import com.example.ayafilemanager.data.local.FileIndexDatabase
import com.example.ayafilemanager.data.directory.AndroidDirectoryBrowserRepository
import com.example.ayafilemanager.data.mediastore.MediaStoreFileIndexer
import com.example.ayafilemanager.data.repository.DefaultFileSearchRepository
import com.example.ayafilemanager.data.search.PinyinNormalizer
import com.example.ayafilemanager.data.search.SearchQueryCompiler
import com.example.ayafilemanager.domain.repository.FileSearchRepository
import com.example.ayafilemanager.domain.repository.DirectoryBrowserRepository

class AyaFileManagerApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}

/** Manual DI keeps the feature testable without making a DI framework part of the core contract. */
class AppContainer(application: Application) {
    private val pinyinNormalizer = PinyinNormalizer()
    private val database = Room.databaseBuilder(
        application,
        FileIndexDatabase::class.java,
        "file-index.db",
    ).fallbackToDestructiveMigration(false).build()

    val fileSearchRepository: FileSearchRepository = DefaultFileSearchRepository(
        dao = database.fileIndexDao(),
        indexer = MediaStoreFileIndexer(application, database.fileIndexDao(), pinyinNormalizer),
        queryCompiler = SearchQueryCompiler(pinyinNormalizer),
        storageIndexer = com.example.ayafilemanager.data.directory.StorageFileIndexer(application, database.fileIndexDao(), pinyinNormalizer),
    )
    val directoryBrowserRepository: DirectoryBrowserRepository = AndroidDirectoryBrowserRepository(database.fileIndexDao())
}
