package com.example.ayafilemanager.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Separates ordinary MediaStore grants from the special all-files setting.
 * All-files access is requested only after the user explicitly enters the full file explorer.
 */
class StoragePermissionManager(private val context: Context) {
    fun snapshot(): StorageAccess = StorageAccess(
        hasAllFilesAccess = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) isGranted(Manifest.permission.READ_EXTERNAL_STORAGE) else Environment.isExternalStorageManager(),
        missingMediaPermissions = runtimePermissions().filterNot(::isGranted),
    )

    fun openAllFilesAccessSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appSettings = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { activity.startActivity(appSettings) }
            .getOrElse { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    private fun runtimePermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyList()
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

data class StorageAccess(
    val hasAllFilesAccess: Boolean,
    val missingMediaPermissions: List<String>,
) {
    val canIndexMediaStore: Boolean get() = hasAllFilesAccess || missingMediaPermissions.isEmpty()
    val needsRuntimeRequest: Boolean get() = missingMediaPermissions.isNotEmpty()
}
