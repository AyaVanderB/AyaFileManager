package com.example.ayafilemanager.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileSearchQuery
import com.example.ayafilemanager.domain.model.FileSort
import com.example.ayafilemanager.domain.model.SearchMode
import com.example.ayafilemanager.domain.model.FileType
import com.example.ayafilemanager.domain.repository.FileSearchRepository
import com.example.ayafilemanager.domain.repository.DirectoryBrowserRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FileExplorerUiState(
    val query: FileSearchQuery = FileSearchQuery(),
    val files: List<FileNode> = emptyList(),
    val isRebuildingIndex: Boolean = false,
    val status: String? = null,
    val currentDirectory: String = "",
    val canNavigateUp: Boolean = false,
    val isShowingSearchResults: Boolean = false,
    val selectedPath: String? = null,
    val showHidden: Boolean = false,
    val isWorking: Boolean = false,
    val rootPath: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FileExplorerViewModel(
    private val repository: FileSearchRepository,
    private val directoryBrowser: DirectoryBrowserRepository,
) : ViewModel() {
    private val query = MutableStateFlow(FileSearchQuery(sort = FileSort.NAME_ASC))
    private val currentDirectory = MutableStateFlow(directoryBrowser.rootPath)
    private val isRebuilding = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)
    private val selectedPath = MutableStateFlow<String?>(null)
    private val reload = MutableStateFlow(0)
    private val showHidden = MutableStateFlow(false)
    private val isWorking = MutableStateFlow(false)
    private val backStack = java.util.ArrayDeque<String>()
    private val forwardStack = java.util.ArrayDeque<String>()
    private val history = MutableStateFlow(0 to 0)
    val rootPath get() = directoryBrowser.rootPath

    private val directoryFiles = combine(currentDirectory, reload) { path, _ -> path }.flatMapLatest { path ->
        flow {
            try { emit(directoryBrowser.childrenOf(path)) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                status.value = "无法读取目录：${e.message}"
                emit(emptyList())
            }
        }
    }

    private data class ContentState(
        val query: FileSearchQuery,
        val files: List<FileNode>,
        val directory: String,
        val isSearch: Boolean,
    )

    private val contentState = combine(
        query,
        query.debounce(90).flatMapLatest { activeQuery ->
            if (!usesIndex(activeQuery)) flowOf(emptyList()) else repository.search(activeQuery)
        },
        directoryFiles,
        currentDirectory,
    ) { activeQuery, searchFiles, directoryNodes, directory ->
        val isSearch = usesIndex(activeQuery)
        ContentState(activeQuery, if (isSearch) searchFiles else directoryNodes, directory, isSearch)
    }

    private data class DisplayContent(val content: ContentState, val hidden: Boolean, val working: Boolean, val history: Pair<Int, Int>)
    private val displayContent = combine(contentState, showHidden, isWorking, history) { content, hidden, working, history -> DisplayContent(content, hidden, working, history) }

    val uiState: StateFlow<FileExplorerUiState> = combine(displayContent, isRebuilding, status, selectedPath) { display, rebuilding, message, path ->
        val (content, hidden, working) = display
        val visible = content.files.filter { hidden || !it.name.startsWith(".") }
        val ordering = when (content.query.sort) {
            FileSort.NAME_ASC -> compareBy<FileNode> { it.name.lowercase(java.util.Locale.ROOT) }
            FileSort.SIZE_DESC -> compareByDescending<FileNode> { it.size }
            FileSort.MODIFIED_DESC -> compareByDescending<FileNode> { it.lastModified }
            FileSort.RELEVANCE -> null
        }
        FileExplorerUiState(
            query = content.query,
            files = if (ordering == null) visible else visible.sortedWith(
                if (content.isSearch) ordering else compareByDescending<FileNode> { it.isDirectory }.then(ordering)),
            showHidden = hidden,
            isWorking = working,
            rootPath = rootPath,
            canGoBack = display.history.first > 0,
            canGoForward = display.history.second > 0,
            isRebuildingIndex = rebuilding,
            status = message,
            currentDirectory = content.directory,
            canNavigateUp = directoryBrowser.parentOf(content.directory) != null,
            isShowingSearchResults = content.isSearch,
            selectedPath = path,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FileExplorerUiState())

    fun updateText(text: String) = updateQuery { it.copy(text = text) }

    fun setSearchMode(mode: SearchMode) = updateQuery { it.copy(searchMode = mode) }

    fun selectType(type: FileType?) = updateQuery { it.copy(type = type) }

    fun toggleLargeFiles() = updateQuery {
        it.copy(minSizeBytes = if (it.minSizeBytes == LARGE_FILE_BYTES) null else LARGE_FILE_BYTES)
    }

    fun toggleRecentFiles() = updateQuery {
        it.copy(modifiedAfterMillis = if (it.modifiedAfterMillis == null) sevenDaysAgo() else null)
    }

    fun setSort(sort: FileSort) = updateQuery { it.copy(sort = sort) }

    fun showRecent(range: Int?) {
        val start = com.example.ayafilemanager.domain.model.RecentTimeRange.values()[range ?: 0].startMillis()
        query.value = FileSearchQuery(modifiedAfterMillis = if (range == null) null else start, filesOnly = range != null, limit = 0, sort = if (range == null) FileSort.NAME_ASC else FileSort.MODIFIED_DESC)
    }

    fun enterPath(input: String) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val file = if (input.trim().startsWith("file:")) java.io.File(java.net.URI(input.trim())) else java.io.File(input.trim())
                    require(file.isAbsolute && file.exists()) { "路径不存在或无访问权限" }
                    require(file.canRead()) { "没有读取该路径的权限" }
                    file.canonicalFile
                }
            }
            result.onSuccess { file ->
                query.value = FileSearchQuery(sort = query.value.sort)
                navigateTo(if (file.isDirectory) file.path else file.parent.orEmpty())
                status.value = null
                selectedPath.value = if (file.isFile) file.path else null
                reload.value++
            }.onFailure { status.value = it.message }
        }
    }

    fun clearSelectedPath() { selectedPath.value = null }

    private fun usesIndex(value: FileSearchQuery) = value.text.isNotBlank() || value.type != null || value.minSizeBytes != null || value.modifiedAfterMillis != null

    fun open(node: FileNode) {
        if (node.isDirectory) {
            query.value = FileSearchQuery(sort = query.value.sort)
            navigateTo(node.path)
            status.value = null
            reload.value++
        } else {
            // Intent-based opening is intentionally the next layer; expose the source path now.
            status.value = "文件地址：${node.path}"
            selectedPath.value = node.path
        }
    }

    fun clearSearch() { query.value = FileSearchQuery(sort = query.value.sort) }
    fun refresh() { reload.value++ }
    fun toggleHidden() { showHidden.value = !showHidden.value }

    fun createFolder(name: String) {
        val directory = currentDirectory.value
        runOperation { FileOperations.createFolder(directory, name) }
    }

    fun rename(node: FileNode, name: String) = runOperation { FileOperations.rename(node.path, name) }

    fun paste(node: FileNode, move: Boolean) {
        val directory = currentDirectory.value
        runOperation { FileOperations.paste(node.path, directory, move) }
    }

    private fun runOperation(operation: () -> String) {
        if (isWorking.value) return
        isWorking.value = true
        viewModelScope.launch {
            try {
                status.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { operation() }
                reload.value++
                rebuildIndex()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                status.value = e.message ?: "操作失败"
            } finally { isWorking.value = false }
        }
    }

    private fun navigateTo(path: String) {
        if (path != currentDirectory.value) {
            backStack.addLast(currentDirectory.value)
            forwardStack.clear()
            currentDirectory.value = path
            history.value = backStack.size to forwardStack.size
        }
    }

    fun navigateBack() {
        if (backStack.isEmpty()) return
        forwardStack.addLast(currentDirectory.value)
        currentDirectory.value = backStack.removeLast()
        clearSearch()
        status.value = null
        history.value = backStack.size to forwardStack.size
        refresh()
    }

    fun navigateForward() {
        if (forwardStack.isEmpty()) return
        backStack.addLast(currentDirectory.value)
        currentDirectory.value = forwardStack.removeLast()
        clearSearch()
        status.value = null
        history.value = backStack.size to forwardStack.size
        refresh()
    }

    fun navigateUp() {
        clearSearch()
        directoryBrowser.parentOf(currentDirectory.value)?.let { navigateTo(it) }
    }

    fun rebuildIndex() {
        if (isRebuilding.value) return
        viewModelScope.launch {
            isRebuilding.value = true
            status.value = "正在建立本地索引…"
            try {
            runCatching { repository.rebuildIndex() }
                .onSuccess { report ->
                    status.value = "已索引 ${report.indexedFileCount} 个文件，用时 ${report.elapsedMillis} ms"
                    reload.value++
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    status.value = "索引失败：${it.message ?: "未知错误"}"
                }
            } finally { isRebuilding.value = false }
        }
    }

    private fun updateQuery(transform: (FileSearchQuery) -> FileSearchQuery) {
        query.value = transform(query.value)
    }

    private fun sevenDaysAgo(): Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1_000L

    private companion object {
        const val LARGE_FILE_BYTES = 100L * 1024 * 1024
    }
}

class FileExplorerViewModelFactory(
    private val repository: FileSearchRepository,
    private val directoryBrowser: DirectoryBrowserRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(FileExplorerViewModel::class.java) -> FileExplorerViewModel(repository, directoryBrowser) as T
        else -> error("Unknown ViewModel class: ${modelClass.name}")
    }
}
