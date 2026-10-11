package com.example.ayafilemanager.ui.explorer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ayafilemanager.domain.model.FileSearchQuery
import com.example.ayafilemanager.domain.model.FileType
import com.example.ayafilemanager.domain.model.SearchMode

@Composable
fun SearchBarWithFilter(
    query: FileSearchQuery,
    onQueryChange: (String) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
    onTypeChange: (FileType?) -> Unit,
    onLargeFilesClick: () -> Unit,
    onRecentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModes by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedTextField(
            value = query.text,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.text.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Clear, "清除搜索") } }
            } else null,
            placeholder = { Text("搜索全部文件 · 支持拼音和后缀") },
        )
        val activeFilters = listOf(query.type != null, query.minSizeBytes != null, query.modifiedAfterMillis != null).count { it }
        TextButton(onClick = { showModes = !showModes }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            Text(if (showModes) "收起筛选" else "${query.searchMode.label} · 筛选${if (activeFilters > 0) " ($activeFilters)" else ""}")
        }
        if (showModes) Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchMode.values().forEach { mode ->
                FilterChip(
                    selected = query.searchMode == mode,
                    onClick = { onSearchModeChange(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        if (showModes) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeFilterChip("全部", null, query.type, onTypeChange)
            TypeFilterChip("图片", FileType.IMAGE, query.type, onTypeChange)
            TypeFilterChip("视频", FileType.VIDEO, query.type, onTypeChange)
            TypeFilterChip("音频", FileType.AUDIO, query.type, onTypeChange)
            TypeFilterChip("安装包", FileType.APK, query.type, onTypeChange)
            TypeFilterChip("文档", FileType.DOCUMENT, query.type, onTypeChange)
            TypeFilterChip("压缩包", FileType.ARCHIVE, query.type, onTypeChange)
            FilterChip(
                selected = query.minSizeBytes != null,
                onClick = onLargeFilesClick,
                label = { Text("大文件") },
            )
            FilterChip(
                selected = query.modifiedAfterMillis != null,
                onClick = onRecentClick,
                label = { Text("最近 7 天") },
            )
        }
    }
}

private val SearchMode.label: String
    get() = when (this) {
        SearchMode.NAME_CONTAINS -> "文件名包含"
        SearchMode.NAME_PREFIX -> "文件整体前缀"
        SearchMode.PINYIN_CONTAINS -> "拼音包含"
        SearchMode.PINYIN_INITIALS_PREFIX -> "拼音首字母"
        SearchMode.EXTENSION_EXACT -> "后缀精确"
    }

@Composable
private fun TypeFilterChip(
    label: String,
    value: FileType?,
    selected: FileType?,
    onChange: (FileType?) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onChange(if (selected == value) null else value) },
        label = { Text(label) },
    )
}

@Composable
private fun chipColors(selected: Boolean) = if (selected) {
    AssistChipDefaults.assistChipColors()
} else {
    AssistChipDefaults.assistChipColors()
}
