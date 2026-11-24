package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.RepoResult
import com.example.model.CodeLanguage
import com.example.model.ProjectSummary
import com.example.ui.components.CodePreviewBox
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LanguageBadge
import com.example.ui.components.RenameDialog
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.viewmodel.ProjectsViewModel

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Long) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }

    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    createError = null
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("projects_fab_new")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_new_project)
                )
            }
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
                    .widthIn(max = 960.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Projects",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${state.allProjectsCount} local workspaces",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search projects by name or language...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("projects_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedLanguage == null,
                        onClick = { viewModel.setLanguageFilter(null) },
                        label = { Text("All") }
                    )
                    listOf(
                        CodeLanguage.KOTLIN,
                        CodeLanguage.TYPESCRIPT,
                        CodeLanguage.PYTHON,
                        CodeLanguage.JAVASCRIPT,
                        CodeLanguage.JSON,
                        CodeLanguage.MARKDOWN,
                        CodeLanguage.RUST
                    ).forEach { lang ->
                        FilterChip(
                            selected = state.selectedLanguage == lang,
                            onClick = { viewModel.setLanguageFilter(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.projects.isEmpty()) {
                    val isSearching = state.searchQuery.isNotBlank() || state.selectedLanguage != null
                    EmptyStateView(
                        title = if (isSearching) "No matching projects" else "No projects yet",
                        message = if (isSearching) {
                            "Try clearing your filter or search query to see all local projects."
                        } else {
                            "Create a local project to start organizing source files and ideas."
                        },
                        showIllustration = !isSearching,
                        actionLabel = if (isSearching) "Clear filters" else stringResource(R.string.action_new_project),
                        actionTag = "projects_empty_action",
                        onAction = {
                            if (isSearching) {
                                viewModel.setSearchQuery("")
                                viewModel.setLanguageFilter(null)
                            } else {
                                createError = null
                                showCreateDialog = true
                            }
                        }
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = if (maxWidth >= 640.dp) 2 else 1
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(state.projects, key = { it.id }) { project ->
                                ProjectCard(
                                    project = project,
                                    onOpen = { onOpenProject(project.id) },
                                    onRename = {
                                        renameError = null
                                        renameTarget = project
                                    },
                                    onDelete = { deleteTarget = project }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            errorMessage = createError,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, language, description, starter ->
                viewModel.createProject(name, language, description, starter) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            showCreateDialog = false
                            createError = null
                            onOpenProject(res.data)
                        }
                        is RepoResult.Error -> createError = res.message
                    }
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = "Rename project",
            label = "Project name",
            initialValue = target.name,
            errorMessage = renameError,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameProject(target.id, newName) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            renameTarget = null
                            renameError = null
                            onShowMessage("Project renamed")
                        }
                        is RepoResult.Error -> renameError = res.message
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete \"${target.name}\"?",
            message = "This permanently deletes ${target.fileCount} files and ${target.folderCount} folders stored in this local project.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteProject(target.id) { res ->
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
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.subtleBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(project.language.markerColor, extras.secondaryGlow)
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(project.language.markerColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = project.language.markerColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (project.description.isNotBlank()) {
                                    project.description
                                } else {
                                    "${project.fileCount} files • ${project.folderCount} folders"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("project_menu_${project.id}")
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
                                text = { Text("Open explorer") },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpen()
                                }
                            )
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

                if (project.previewSnippet != null) {
                    CodePreviewBox(
                        code = project.previewSnippet,
                        language = project.language,
                        fileName = project.previewFileName,
                        maxLines = 3
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageBadge(language = project.language)
                    Text(
                        text = "${project.fileCount} ${if (project.fileCount == 1) "file" else "files"} • Edited ${formatRelativeTime(project.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
