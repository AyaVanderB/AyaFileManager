package com.example.ayafilemanager.ui.explorer

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.compose.AsyncImage
import com.example.ayafilemanager.domain.model.FileNode
import com.example.ayafilemanager.domain.model.FileType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileNode,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: (FileNode) -> Unit = {},
    onLongClick: (FileNode) -> Unit = {},
) {
    Card(
        modifier = modifier.combinedClickable(onClick = { onClick(file) }, onLongClick = { onLongClick(file) }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!file.isDirectory && (file.type == FileType.IMAGE || file.type == FileType.VIDEO)) {
                    AsyncImage(
                        model = file.contentUri,
                        contentDescription = file.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    FileTypeIcon(file, iconSize)
                }
                if (!file.isDirectory && file.extension.isNotBlank()) {
                    Text(
                        text = file.extension.uppercase(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Text(
                if (file.size < 0) "等待索引统计" else Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, file.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(modifiedLabel(file.lastModified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(file: FileNode, modifier: Modifier = Modifier, onClick: (FileNode) -> Unit = {}, onLongClick: (FileNode) -> Unit = {}, showPath: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth()
            .combinedClickable(onClick = { onClick(file) }, onLongClick = { onLongClick(file) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (!file.isDirectory && file.type == FileType.IMAGE) {
                AsyncImage(model = file.contentUri, contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            } else FileTypeIcon(file, 26.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(if (showPath) file.path else modifiedLabel(file.lastModified), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (file.isDirectory) "文件夹" else file.extension.uppercase().ifBlank { "文件" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!file.isDirectory) Text(Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, file.size),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun modifiedLabel(millis: Long): String = "修改于 " + java.time.Instant.ofEpochMilli(millis)
    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable
private fun FileTypeIcon(file: FileNode, size: Dp) {
    Icon(
        imageVector = fileTypeIcon(file),
        contentDescription = file.type.name,
        modifier = Modifier.size(size),
        tint = if (file.isDirectory) androidx.compose.ui.graphics.Color(0xFFB58A2B) else MaterialTheme.colorScheme.primary,
    )
}

private fun fileTypeIcon(file: FileNode): ImageVector = when {
    file.isDirectory -> Icons.Outlined.Folder
    file.extension == "pdf" -> Icons.Outlined.PictureAsPdf
    file.type == FileType.IMAGE -> Icons.Outlined.Image
    file.type == FileType.VIDEO -> Icons.Outlined.Movie
    file.type == FileType.AUDIO -> Icons.Outlined.AudioFile
    file.type == FileType.ARCHIVE -> Icons.Outlined.Archive
    file.type == FileType.APK -> Icons.Outlined.Android
    file.type == FileType.DOCUMENT -> Icons.Outlined.Description
    else -> Icons.Outlined.InsertDriveFile
}
