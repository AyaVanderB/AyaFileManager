package com.example.ayafilemanager.ui.explorer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.ayafilemanager.domain.model.FileNode
import java.io.File
import java.util.Locale

/** Shares only the selected file, with temporary read access for the chosen app. */
internal fun openFileWithApp(context: Context, node: FileNode) {
    try {
        val uri = if (node.id.startsWith("content://")) {
            android.net.Uri.parse(node.id)
        } else {
            val file = File(node.path)
            require(file.isFile && file.canRead()) { "文件不存在或没有读取权限" }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val extension = node.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val inferredType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val mimeType = node.mimeType.takeIf { it.isNotBlank() && it != "application/octet-stream" && it != "*/*" }
            ?: inferredType ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri(node.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "选择应用打开").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "手机中没有可打开此文件的应用", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "无法打开文件，请检查存储访问权限", Toast.LENGTH_LONG).show()
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, "文件不存在、无法读取或不在可共享的存储目录中", Toast.LENGTH_LONG).show()
    }
}

internal fun shareFile(context: Context, node: FileNode) {
    try {
        val uri = if (node.id.startsWith("content://")) android.net.Uri.parse(node.id)
        else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(node.path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = node.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(node.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
    }
}
