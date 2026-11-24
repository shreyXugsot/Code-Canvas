package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.NodeEntity
import com.example.data.repository.RepoResult
import com.example.model.CodeLanguage
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LanguageBadge
import com.example.ui.components.RenameDialog
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.viewmodel.ExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    onBack: () -> Unit,
    onOpenFile: (Long) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<NodeEntity?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }

    var deleteTarget by remember { mutableStateOf<NodeEntity?>(null) }

    // Zero-permission Android system document picker for optional file import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val fileName = resolveDisplayName(context, uri) ?: "imported_file.txt"
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Could not read file contents")
            val lang = CodeLanguage.inferFromFileName(
                fileName,
                CodeLanguage.fromNameOrDefault(state.project?.language ?: CodeLanguage.PLAIN_TEXT.name)
            )
            viewModel.createFile(fileName, text, lang) { res ->
                when (res) {
                    is RepoResult.Success -> onShowMessage("Imported $fileName")
                    is RepoResult.Error -> onShowMessage(res.message)
                }
            }
        } catch (e: Exception) {
            onShowMessage(e.message ?: "Unable to import selected file.")
        }
    }

    // Back inside subfolder navigates up the breadcrumb stack before exiting Explorer
    BackHandler(enabled = state.currentFolderId != null) {
        viewModel.navigateUp()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.project?.name ?: "Project",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.totalFileCount} files • ${state.totalFolderCount} folders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!viewModel.navigateUp()) onBack()
                        },
                        modifier = Modifier.testTag("explorer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
                        modifier = Modifier.testTag("explorer_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileOpen,
                            contentDescription = "Import file"
                        )
                    }
                    IconButton(
                        onClick = {
                            createError = null
                            showNewFolderDialog = true
                        },
                        modifier = Modifier.testTag("explorer_new_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = stringResource(R.string.action_new_folder)
                        )
                    }
                    IconButton(
                        onClick = {
                            createError = null
                            showNewFileDialog = true
                        },
                        modifier = Modifier.testTag("explorer_new_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NoteAdd,
                            contentDescription = stringResource(R.string.action_new_file)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                // Breadcrumb bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { idx, segment ->
                        val isLast = idx == state.breadcrumbs.lastIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLast) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLast) { viewModel.openFolder(segment.folderId) }
                        ) {
                            Text(
                                text = segment.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLast) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (state.isLoaded && state.visibleNodes.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStateView(
                            title = if (state.currentFolderId == null) "This project is empty" else "Empty folder",
                            message = "Add a source file, create a subfolder, or import a text file from your device.",
                            actionLabel = stringResource(R.string.action_new_file),
                            actionTag = "explorer_empty_new_file",
                            onAction = {
                                createError = null
                                showNewFileDialog = true
                            },
                            secondaryActionLabel = stringResource(R.string.action_new_folder),
                            onSecondaryAction = {
                                createError = null
                                showNewFolderDialog = true
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.visibleNodes, key = { it.id }) { node ->
                            ExplorerNodeRow(
                                node = node,
                                childCount = state.childCountsByFolderId[node.id] ?: 0,
                                onClick = {
                                    if (node.isFolder) {
                                        viewModel.openFolder(node.id)
                                    } else {
                                        onOpenFile(node.id)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(node.id) },
                                onRename = {
                                    renameError = null
                                    renameTarget = node
                                },
                                onDelete = { deleteTarget = node }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        val defaultLang = CodeLanguage.fromNameOrDefault(state.project?.language ?: CodeLanguage.KOTLIN.name)
        CreateNodeDialog(
            isFolder = false,
            defaultLanguage = defaultLang,
            errorMessage = createError,
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name, lang ->
                viewModel.createFile(name = name, language = lang) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            showNewFileDialog = false
                            createError = null
                            onOpenFile(res.data)
                        }
                        is RepoResult.Error -> createError = res.message
                    }
                }
            }
        )
    }

    if (showNewFolderDialog) {
        CreateNodeDialog(
            isFolder = true,
            defaultLanguage = CodeLanguage.PLAIN_TEXT,
            errorMessage = createError,
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name, _ ->
                viewModel.createFolder(name) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            showNewFolderDialog = false
                            createError = null
                        }
                        is RepoResult.Error -> createError = res.message
                    }
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = if (target.isFolder) "Rename folder" else "Rename file",
            label = if (target.isFolder) "Folder name" else "File name",
            initialValue = target.name,
            errorMessage = renameError,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameNode(target.id, newName) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            renameTarget = null
                            renameError = null
                            onShowMessage("Renamed")
                        }
                        is RepoResult.Error -> renameError = res.message
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        val extraInfo = if (target.isFolder) {
            " This will also delete any nested files and subfolders inside it."
        } else ""
        ConfirmDeleteDialog(
            title = "Delete \"${target.name}\"?",
            message = "This action removes \"${target.name}\" from the project.$extraInfo",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteNode(target.id) { res ->
                    deleteTarget = null
                    when (res) {
                        is RepoResult.Success -> onShowMessage("Deleted ${target.name}")
                        is RepoResult.Error -> onShowMessage(res.message)
                    }
                }
            }
        )
    }
}

@Composable
private fun ExplorerNodeRow(
    node: NodeEntity,
    childCount: Int,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    val language = CodeLanguage.fromNameOrDefault(node.language)
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("explorer_node_${node.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.subtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (node.isFolder) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            language.markerColor.copy(alpha = 0.14f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (node.isFolder) Icons.Rounded.Folder else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = if (node.isFolder) MaterialTheme.colorScheme.primary else language.markerColor,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = if (node.isFolder) MaterialTheme.typography.titleSmall.fontFamily else JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (node.isFolder) {
                    Text(
                        text = "$childCount ${if (childCount == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val lineCount = if (node.content.isEmpty()) 0 else node.content.count { it == '\n' } + 1
                    Text(
                        text = "${language.displayName} • $lineCount lines • ${formatRelativeTime(node.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!node.isFolder) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("node_fav_${node.id}")
                ) {
                    Icon(
                        imageVector = if (node.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (node.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("node_menu_${node.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateNodeDialog(
    isFolder: Boolean,
    defaultLanguage: CodeLanguage,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, language: CodeLanguage) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(defaultLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "New folder" else "New file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!isFolder && it.contains('.')) {
                            selectedLanguage = CodeLanguage.inferFromFileName(it, selectedLanguage)
                        }
                    },
                    label = { Text(if (isFolder) "Folder name" else "File name") },
                    placeholder = { Text(if (isFolder) "e.g. components" else "e.g. Formatter.kt") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_node_input")
                )

                if (!isFolder) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Syntax language",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(lang.displayName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedLanguage) },
                modifier = Modifier.testTag("confirm_create_node_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
