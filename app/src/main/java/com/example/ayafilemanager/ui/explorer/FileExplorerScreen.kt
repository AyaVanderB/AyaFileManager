package com.example.ayafilemanager.ui.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ChevronRight
import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileSort
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ayafilemanager.permission.StorageAccess


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel,
    storageAccess: StorageAccess,
    onRequestMediaPermissions: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = LocalContext.current.getSharedPreferences("explorer", android.content.Context.MODE_PRIVATE)
    var layout by rememberSaveable { mutableStateOf(preferences.getString("layout", "LIST") ?: "LIST") }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.currentDirectory, state.query) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    val iconSize = 32.dp
    var menu by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileNode?>(null) }
    var editAction by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var pendingFile by remember { mutableStateOf<FileNode?>(null) }
    var pendingMove by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var pathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var recentPage by remember { mutableStateOf(false) }
    var recentRange by remember { mutableStateOf(0) }
    val onFileClick: (com.example.ayafilemanager.domain.model.FileNode) -> Unit = { file ->
        if (file.isDirectory) {
            recentPage = false
            viewModel.open(file)
        } else {
            openFileWithApp(context, file)
        }
    }
    BackHandler(enabled = recentPage || state.isShowingSearchResults || state.canGoBack || state.canNavigateUp) {
        if (recentPage) { recentPage = false; viewModel.showRecent(null) }
        else if (state.isShowingSearchResults) viewModel.clearSearch() else if (state.canGoBack) viewModel.navigateBack() else viewModel.navigateUp()
    }
    editAction?.let { action ->
        AlertDialog(onDismissRequest = { editAction = null }, title = { Text(action) },
            text = { OutlinedTextField(editName, { editName = it }, singleLine = true, label = { Text("名称") }) },
            confirmButton = { TextButton(enabled = editName.isNotBlank() && !state.isWorking, onClick = {
                if (action == "新建文件夹") viewModel.createFolder(editName) else selectedFile?.let { viewModel.rename(it, editName) }
                editAction = null; selectedFile = null
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { editAction = null }) { Text("取消") } })
    }
    if (editAction == null) selectedFile?.let { file ->
        AlertDialog(onDismissRequest = { selectedFile = null }, title = { Text(file.name, maxLines = 2) },
            text = { Column {
                Text(file.path, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { clipboard.setText(AnnotatedString(file.path)); selectedFile = null }) { Text("复制完整路径") }
                TextButton(onClick = { viewModel.enterPath(java.io.File(file.path).parent.orEmpty()); recentPage = false; selectedFile = null }) { Text("打开所在目录") }
                if (!file.isDirectory) TextButton(onClick = { shareFile(context, file); selectedFile = null }) { Text("分享") }
                if (storageAccess.hasAllFilesAccess) {
                    TextButton(enabled = !state.isWorking, onClick = { editName = file.name; editAction = "重命名" }) { Text("重命名") }
                    TextButton(onClick = { pendingFile = file; pendingMove = false; selectedFile = null }) { Text("复制，前往目标目录粘贴") }
                    TextButton(onClick = { pendingFile = file; pendingMove = true; selectedFile = null }) { Text("移动，前往目标目录粘贴") }
                }
            } }, confirmButton = { TextButton(onClick = { selectedFile = null }) { Text("关闭") } })
    }
    if (pathDialog) {
        AlertDialog(onDismissRequest = { pathDialog = false }, title = { Text("通过路径进入") },
            text = { OutlinedTextField(pathInput, { pathInput = it }, label = { Text("文件或文件夹的绝对路径") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { viewModel.enterPath(pathInput); recentPage = false; pathDialog = false }) { Text("进入") } },
            dismissButton = { TextButton(onClick = { pathDialog = false }) { Text("取消") } })
    }
    state.selectedPath?.let { path ->
        AlertDialog(onDismissRequest = viewModel::clearSelectedPath, title = { Text("文件地址") },
            text = { SelectionContainer { Text(path) } },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(path)) }) { Text("复制地址") } },
            dismissButton = { TextButton(onClick = { viewModel.enterPath(java.io.File(path).parent.orEmpty()); recentPage = false }) { Text("打开所在目录") } })
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (recentPage) "最近修改" else "文件管理", style = MaterialTheme.typography.titleMedium) },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    if (!recentPage && !state.isShowingSearchResults && state.canNavigateUp) {
                        IconButton(onClick = viewModel::navigateUp) { Icon(Icons.Outlined.KeyboardArrowUp, "返回上级目录") }
                    }
                },
                actions = {
                    IconButton(onClick = { layout = if (layout == "GRID") "LIST" else "GRID"; preferences.edit().putString("layout", layout).apply() }) {
                        Icon(if (layout == "GRID") Icons.Outlined.List else Icons.Outlined.GridView, "切换视图")
                    }
                    IconButton(onClick = viewModel::refresh, enabled = storageAccess.canIndexMediaStore && !state.isWorking) {
                        if (state.isRebuildingIndex) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, "刷新当前目录")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("新建文件夹") }, enabled = storageAccess.hasAllFilesAccess && !state.isShowingSearchResults && !state.isWorking,
                                onClick = { menu = false; editName = ""; editAction = "新建文件夹" })
                            DropdownMenuItem(text = { Text(if (state.showHidden) "隐藏点号文件" else "显示隐藏文件") }, onClick = { menu = false; viewModel.toggleHidden() })
                            DropdownMenuItem(text = { Text("重建搜索索引") }, enabled = !state.isRebuildingIndex,
                                onClick = { menu = false; viewModel.rebuildIndex() })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = !recentPage, onClick = { recentPage = false; viewModel.showRecent(null) }, icon = { Icon(Icons.Outlined.GridView, null) }, label = { Text("文件") })
                NavigationBarItem(selected = recentPage, onClick = { recentPage = true; viewModel.showRecent(recentRange) }, icon = { Icon(Icons.Outlined.History, null) }, label = { Text("最近修改") })
            }
        },
    ) { padding ->
        if (!storageAccess.canIndexMediaStore) {
            PermissionEmptyState(storageAccess, onRequestMediaPermissions, onRequestAllFilesAccess, Modifier.padding(padding))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                if (recentPage) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("今天", "近一周", "近一个月").forEachIndexed { index, label ->
                            FilterChip(selected = recentRange == index, onClick = { recentRange = index; viewModel.showRecent(index) }, label = { Text(label) })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("按修改时间浏览 · 可在菜单显示隐藏文件", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.files.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!storageAccess.hasAllFilesAccess) {
                        Button(onClick = onRequestAllFilesAccess) { Text("授予全部文件访问，收录非媒体文件") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = state.canGoBack, onClick = viewModel::navigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回上一位置", Modifier.size(20.dp)) }
                        IconButton(enabled = state.canGoForward, onClick = viewModel::navigateForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "前进", Modifier.size(20.dp)) }
                        TextButton(onClick = { viewModel.enterPath(state.rootPath) }) { Text("内部存储") }
                        val insideRoot = state.currentDirectory == state.rootPath || state.currentDirectory.startsWith(state.rootPath + "/")
                        val relative = if (insideRoot) state.currentDirectory.removePrefix(state.rootPath).trim('/') else ""
                        if (!insideRoot) Text(state.currentDirectory, style = MaterialTheme.typography.bodySmall)
                        val segments = relative.split('/').filter { it.isNotBlank() }
                        segments.forEachIndexed { index, segment ->
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                            TextButton(onClick = { viewModel.enterPath(state.rootPath + "/" + segments.take(index + 1).joinToString("/")) }) { Text(segment) }
                        }
                        TextButton(onClick = { pathInput = state.currentDirectory; pathDialog = true }) { Text("编辑路径") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("下载" to "Download", "相机" to "DCIM", "文档" to "Documents").forEach { (label, folder) ->
                            AssistChip(onClick = { viewModel.enterPath(state.rootPath + "/" + folder) }, label = { Text(label) },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) })
                        }
                    }
                    SearchBarWithFilter(
                        query = state.query,
                        onQueryChange = viewModel::updateText,
                        onSearchModeChange = viewModel::setSearchMode,
                        onTypeChange = viewModel::selectType,
                        onLargeFilesClick = viewModel::toggleLargeFiles,
                        onRecentClick = viewModel::toggleRecentFiles,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!state.isShowingSearchResults) {
                        if (!storageAccess.hasAllFilesAccess) {
                            Button(onClick = onRequestAllFilesAccess, modifier = Modifier.padding(top = 6.dp)) {
                                Text("授予全部文件访问以浏览目录")
                            }
                        }
                    }

                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.files.size} 项" + if (state.isShowingSearchResults) " · 全局搜索" else " · 文件夹优先",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.isShowingSearchResults && !recentPage) TextButton(onClick = viewModel::clearSearch) { Text("清除筛选") }
                    var sorting by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { sorting = true }) { Text("排序") }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            listOf("名称" to FileSort.NAME_ASC, "修改时间" to FileSort.MODIFIED_DESC, "大小" to FileSort.SIZE_DESC).forEach { (label, sort) ->
                                DropdownMenuItem(text = { Text(label + if (state.query.sort == sort) " ✓" else "") }, onClick = { viewModel.setSort(sort); sorting = false })
                            }
                        }
                    }
                }
                pendingFile?.let { file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (pendingMove) "移动：" else "复制：") + file.name, Modifier.weight(1f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !state.isShowingSearchResults && !state.isWorking, onClick = { viewModel.paste(file, pendingMove) }) { Text("粘贴") }
                        TextButton(onClick = { pendingFile = null }) { Text("取消") }
                    }
                }
                if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.files.isEmpty()) {
                    Text(if (state.isRebuildingIndex) "正在整理文件，请稍候…" else "此处没有可显示的文件", modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (layout == "GRID") {
                    LazyVerticalGrid(
                        modifier = Modifier.weight(1f),
                        state = gridState,
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.files, key = { it.id }) { file -> FileGridItem(file, iconSize, onClick = onFileClick, onLongClick = { selectedFile = it }) }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(state.files, key = { it.id }) { file ->
                            FileListItem(file, onClick = onFileClick, onLongClick = { selectedFile = it }, showPath = state.isShowingSearchResults)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionEmptyState(
    storageAccess: StorageAccess,
    onRequestMediaPermissions: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要存储访问权限", style = MaterialTheme.typography.headlineSmall)
        Text("授权后会从系统 MediaStore 创建本地搜索索引；文件内容不会上传。", modifier = Modifier.padding(top = 8.dp, bottom = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (storageAccess.needsRuntimeRequest) Button(onClick = onRequestMediaPermissions) { Text("允许读取媒体") }
        if (!storageAccess.hasAllFilesAccess) {
            Button(onClick = onRequestAllFilesAccess, modifier = Modifier.padding(top = 10.dp)) { Text("授予全部文件访问") }
        }
    }
}
