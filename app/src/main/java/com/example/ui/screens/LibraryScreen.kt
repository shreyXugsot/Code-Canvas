package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.LibraryItemEntity
import com.example.data.repository.RepoResult
import com.example.editor.ValidationUtils
import com.example.model.CodeLanguage
import com.example.model.LibraryItemType
import com.example.ui.components.CodePreviewBox
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LanguageBadge
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.viewmodel.LibraryTabFilter
import com.example.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var editingItem by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryItemEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    dialogError = null
                    isCreatingNew = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("library_fab_new")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add note or snippet")
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
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Notes & Snippets",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Reusable code blocks & coding notes organized by tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LibraryTabFilter.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LibraryTabFilter.entries.size
                            ),
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("library_tab_${tab.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search title, code, notes, or #tags...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_search))
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
                        .testTag("library_search_input")
                )

                if (state.availableTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.availableTags.forEach { tag ->
                            FilterChip(
                                selected = state.selectedTag == tag,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text("#$tag") },
                                modifier = Modifier.testTag("library_tag_chip_$tag")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (state.items.isEmpty()) {
                    val isFiltering = state.searchQuery.isNotBlank() ||
                        state.selectedTag != null ||
                        state.selectedTab != LibraryTabFilter.ALL
                    EmptyStateView(
                        title = if (isFiltering) "No matching items" else "Your library is empty",
                        message = if (isFiltering) {
                            "No snippets or notes match your active search or tag filter."
                        } else {
                            "Save reusable code snippets or jot down architecture notes with tags."
                        },
                        showIllustration = !isFiltering,
                        actionLabel = if (isFiltering) "Reset filters" else "New snippet or note",
                        onAction = {
                            if (isFiltering) {
                                viewModel.setSearchQuery("")
                                viewModel.selectTab(LibraryTabFilter.ALL)
                                state.selectedTag?.let { viewModel.toggleTag(it) }
                            } else {
                                editingItem = null
                                dialogError = null
                                isCreatingNew = true
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            LibraryCard(
                                item = item,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(item.content))
                                    onShowMessage("Copied to clipboard")
                                },
                                onEdit = {
                                    editingItem = item
                                    dialogError = null
                                    isCreatingNew = false
                                },
                                onDelete = { deleteTarget = item },
                                onTagClick = { tag -> viewModel.toggleTag(tag) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isCreatingNew || editingItem != null) {
        LibraryEditorDialog(
            initialItem = editingItem,
            errorMessage = dialogError,
            onDismiss = {
                isCreatingNew = false
                editingItem = null
            },
            onSave = { title, type, language, content, tags ->
                viewModel.saveItem(
                    existingId = editingItem?.id,
                    title = title,
                    type = type,
                    language = language,
                    content = content,
                    tags = tags
                ) { res ->
                    when (res) {
                        is RepoResult.Success -> {
                            isCreatingNew = false
                            editingItem = null
                            dialogError = null
                            onShowMessage("Saved ${type.label.lowercase()}")
                        }
                        is RepoResult.Error -> dialogError = res.message
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete \"${target.title}\"?",
            message = "This item will be permanently removed from your local library.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteItem(target.id) { res ->
                    deleteTarget = null
                    when (res) {
                        is RepoResult.Success -> onShowMessage("Deleted")
                        is RepoResult.Error -> onShowMessage(res.message)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryCard(
    item: LibraryItemEntity,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTagClick: (String) -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    val isSnippet = item.type == LibraryItemType.SNIPPET.name
    val language = CodeLanguage.fromNameOrDefault(item.language)
    val tags = remember(item.tags) { ValidationUtils.parseTags(item.tags) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.subtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageBadge(
                            language = if (isSnippet) language else CodeLanguage.MARKDOWN,
                            compact = true
                        )
                        Text(
                            text = if (isSnippet) "Snippet" else "Note",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• ${formatRelativeTime(item.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.testTag("library_copy_${item.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy content",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("library_edit_${item.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit item",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("library_delete_${item.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (isSnippet) {
                CodePreviewBox(
                    code = item.content,
                    language = language,
                    maxLines = 6
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            TextButton(
                                onClick = { onTagClick(tag) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEditorDialog(
    initialItem: LibraryItemEntity?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, type: LibraryItemType, language: CodeLanguage, content: String, tags: String) -> Unit
) {
    var title by remember(initialItem) { mutableStateOf(initialItem?.title.orEmpty()) }
    var type by remember(initialItem) {
        mutableStateOf(
            if (initialItem?.type == LibraryItemType.NOTE.name) LibraryItemType.NOTE
            else LibraryItemType.SNIPPET
        )
    }
    var language by remember(initialItem) {
        mutableStateOf(
            initialItem?.language?.let { CodeLanguage.fromNameOrDefault(it) } ?: CodeLanguage.KOTLIN
        )
    }
    var content by remember(initialItem) { mutableStateOf(initialItem?.content.orEmpty()) }
    var tags by remember(initialItem) { mutableStateOf(initialItem?.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "New library item" else "Edit ${type.label.lowercase()}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LibraryItemType.entries.forEachIndexed { idx, option ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(idx, LibraryItemType.entries.size),
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text(if (type == LibraryItemType.SNIPPET) "e.g. Debounce Flow" else "e.g. API Notes") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_dialog_title")
                )

                if (type == LibraryItemType.SNIPPET) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CodeLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { language = lang },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("kotlin, ui, coroutines") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_dialog_tags")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (type == LibraryItemType.SNIPPET) "Code" else "Note content") },
                    minLines = 5,
                    maxLines = 10,
                    textStyle = if (type == LibraryItemType.SNIPPET) {
                        TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_dialog_content")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, type, language, content, tags) },
                modifier = Modifier.testTag("library_dialog_save")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
