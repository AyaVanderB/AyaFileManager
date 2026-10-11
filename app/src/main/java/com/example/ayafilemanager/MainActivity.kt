package com.example.ayafilemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ayafilemanager.permission.StoragePermissionManager
import com.example.ayafilemanager.ui.explorer.FileExplorerScreen
import com.example.ayafilemanager.ui.explorer.FileExplorerViewModel
import com.example.ayafilemanager.ui.explorer.FileExplorerViewModelFactory
import com.example.ayafilemanager.ui.theme.AyafilemanagerTheme

class MainActivity : ComponentActivity() {
    private val permissionManager by lazy { StoragePermissionManager(this) }
    private val viewModel: FileExplorerViewModel by viewModels {
        FileExplorerViewModelFactory(
            (application as AyaFileManagerApplication).appContainer.fileSearchRepository,
            (application as AyaFileManagerApplication).appContainer.directoryBrowserRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AyaFilemanagerApp(permissionManager, viewModel)
        }
    }
}

@Composable
private fun AyaFilemanagerApp(permissionManager: StoragePermissionManager, viewModel: FileExplorerViewModel) {
    var access by remember { mutableStateOf(permissionManager.snapshot()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = permissionManager.snapshot()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = permissionManager.snapshot()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(access.canIndexMediaStore, access.hasAllFilesAccess) {
        if (access.canIndexMediaStore) viewModel.rebuildIndex()
    }
    AyafilemanagerTheme {
        FileExplorerScreen(
            viewModel = viewModel,
            storageAccess = access,
            onRequestMediaPermissions = { permissionLauncher.launch(access.missingMediaPermissions.toTypedArray()) },
            onRequestAllFilesAccess = { permissionManager.openAllFilesAccessSettings(lifecycleOwner as ComponentActivity) },
        )
    }
}
