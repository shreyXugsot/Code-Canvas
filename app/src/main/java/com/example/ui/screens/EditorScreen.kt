package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.editor.SyntaxVisualTransformation
import com.example.model.CodeLanguage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LanguageBadge
import com.example.ui.components.TerminalDrawer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.SaveStatus

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extras = LocalCodeCanvasExtras.current
    val context = LocalContext.current

    var languageMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var localWordWrap by remember(settings.wordWrap) { mutableStateOf(settings.wordWrap) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(state.textFieldValue.text)
            }
            onShowMessage("Exported ${state.fileName}")
        } catch (e: Exception) {
            onShowMessage(e.message ?: "Failed to export file.")
        }
    }

    val handleExit = {
        viewModel.onExitRequested(onBack)
    }

    BackHandler { handleExit() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extras.editorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Editor Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = handleExit,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = state.fileName.ifBlank { "Editor" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SaveStatusIndicator(
                            status = state.saveStatus,
                            onSaveClick = { viewModel.saveNow() }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.isRunning) {
                                viewModel.stopExecution()
                            } else {
                                viewModel.runCurrentFile()
                            }
                        },
                        enabled = !state.isLoading && !state.fileNotFound,
                        modifier = Modifier.testTag("editor_run_button")
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isRunning) {
                                stringResource(R.string.action_stop)
                            } else {
                                stringResource(R.string.action_run)
                            },
                            tint = if (state.isRunning) DangerCoral else SuccessEmerald
                        )
                    }

                    IconButton(
                        onClick = { viewModel.saveNow { ok -> if (ok) onShowMessage("Saved") } },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.action_save),
                            tint = if (state.saveStatus == SaveStatus.UNSAVED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = viewModel::toggleSearchBar,
                        modifier = Modifier.testTag("editor_search_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            tint = if (state.isSearchOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { moreMenuOpen = true },
                            modifier = Modifier.testTag("editor_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.isFavorite) "Remove from favorites" else "Add to favorites")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                        contentDescription = stringResource(R.string.cd_favorite),
                                        tint = if (state.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    viewModel.toggleFavorite()
                                },
                                modifier = Modifier.testTag("editor_favorite_button")
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Auto-save")
                                        Text(
                                            text = if (settings.autoSaveOnLeave) "On • 2s pause" else "Off • Manual save",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoMode,
                                        contentDescription = null,
                                        tint = if (settings.autoSaveOnLeave) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = settings.autoSaveOnLeave,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    val next = !settings.autoSaveOnLeave
                                    viewModel.setAutoSave(next)
                                    moreMenuOpen = false
                                    onShowMessage(
                                        if (next) "Auto-save enabled (2s pause)" else "Auto-save disabled"
                                    )
                                },
                                modifier = Modifier.testTag("editor_menu_autosave")
                            )
                            DropdownMenuItem(
                                text = { Text(if (localWordWrap) "Disable word wrap" else "Enable word wrap") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.WrapText, contentDescription = null) },
                                onClick = {
                                    localWordWrap = !localWordWrap
                                    moreMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export copy...") },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                                onClick = {
                                    moreMenuOpen = false
                                    exportLauncher.launch(state.fileName.ifBlank { "codecanvas_export.txt" })
                                }
                            )
                        }
                    }
                }

                // Error banner if save failed
                state.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { viewModel.clearError() }
                            )
                        }
                    }
                }

                // Collapsible Search-in-File bar
                AnimatedVisibility(visible = state.isSearchOpen) {
                    EditorSearchBar(
                        state = state,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onToggleCase = viewModel::toggleSearchCaseSensitive,
                        onPrev = { viewModel.stepSearchMatch(-1) },
                        onNext = { viewModel.stepSearchMatch(1) },
                        onClose = viewModel::toggleSearchBar
                    )
                }
                HorizontalDivider(color = extras.subtleBorder)
            }
        }

        // Main Editor Body
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.fileNotFound -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "File not found",
                        message = "This file may have been removed or renamed.",
                        showIllustration = false,
                        actionLabel = "Go back",
                        onAction = onBack
                    )
                }
            }

            else -> {
                val fontSizeSp = settings.editorFontSizeSp.sp
                val lineHeightSp = (settings.editorFontSizeSp * 1.48f).sp
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()

                val visualTransformation = remember(
                    state.language,
                    extras.syntaxPalette,
                    state.searchMatches,
                    state.activeMatchIndex
                ) {
                    SyntaxVisualTransformation(
                        language = state.language,
                        palette = extras.syntaxPalette,
                        searchMatches = state.searchMatches,
                        activeMatchIndex = state.activeMatchIndex
                    )
                }

                val lineNumbersText = remember(state.lineCount) {
                    (1..state.lineCount).joinToString("\n")
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(verticalScroll)
                ) {
                    // Gutter with aligned monospaced line numbers
                    Box(
                        modifier = Modifier
                            .background(extras.editorGutterBg)
                            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 32.dp)
                    ) {
                        Text(
                            text = lineNumbersText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSizeSp,
                                lineHeight = lineHeightSp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.widthIn(min = 26.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(extras.subtleBorder)
                    )

                    val editorBoxModifier = if (localWordWrap) {
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    }

                    Box(modifier = editorBoxModifier) {
                        if (state.textFieldValue.text.isEmpty()) {
                            Text(
                                text = "// Start typing ${state.language.displayName} code...",
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = fontSizeSp,
                                    lineHeight = lineHeightSp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                            )
                        }

                        BasicTextField(
                            value = state.textFieldValue,
                            onValueChange = viewModel::onCodeChange,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSizeSp,
                                lineHeight = lineHeightSp,
                                color = extras.syntaxPalette.plain
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = visualTransformation,
                            modifier = Modifier
                                .then(
                                    if (localWordWrap) Modifier.fillMaxWidth()
                                    else Modifier.widthIn(min = 320.dp)
                                )
                                .testTag("code_editor_textfield")
                        )
                    }
                }

                // Quick Symbol Accessory Strip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, extras.subtleBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickSymbolKey("TAB") { viewModel.insertSymbol("  ") }
                        QuickSymbolKey("{ }") { viewModel.insertSymbol("{}", cursorBackOffset = 1) }
                        QuickSymbolKey("( )") { viewModel.insertSymbol("()", cursorBackOffset = 1) }
                        QuickSymbolKey("[ ]") { viewModel.insertSymbol("[]", cursorBackOffset = 1) }
                        QuickSymbolKey("\" \"") { viewModel.insertSymbol("\"\"", cursorBackOffset = 1) }
                        QuickSymbolKey("=") { viewModel.insertSymbol(" = ") }
                        QuickSymbolKey("->") { viewModel.insertSymbol(" -> ") }
                        QuickSymbolKey("//") { viewModel.insertSymbol("// ") }
                        QuickSymbolKey(";") { viewModel.insertSymbol(";") }
                        QuickSymbolKey(":") { viewModel.insertSymbol(": ") }
                    }
                }

                // Collapsible In-Editor Shell Terminal Drawer
                AnimatedVisibility(visible = state.isTerminalOpen) {
                    TerminalDrawer(
                        projectName = state.projectName,
                        fileName = state.fileName,
                        isRunning = state.isRunning,
                        isExpanded = state.isTerminalExpanded,
                        lines = state.terminalLines,
                        lastExitCode = state.lastExitCode,
                        lastDurationMs = state.lastDurationMs,
                        shellInput = state.shellInput,
                        onShellInputChange = viewModel::onShellInputChange,
                        onRunCommand = viewModel::runShellCommand,
                        onStop = viewModel::stopExecution,
                        onClear = viewModel::clearTerminal,
                        onCopyOutput = {
                            val text = viewModel.getTerminalLogText()
                            if (text.isNotEmpty()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Shell Output", text))
                                onShowMessage("Copied terminal output")
                            }
                        },
                        onToggleExpanded = viewModel::toggleTerminalExpanded,
                        onClose = viewModel::toggleTerminal
                    )
                }

                // VS Code-inspired Bottom Status Bar with Shell Toggle & Language Selector
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ln ${state.currentLineNumber}, Col ${state.currentColumnNumber}  •  ${if (settings.autoSaveOnLeave) "Auto-save" else "Manual"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.isTerminalOpen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleTerminal() }
                                .testTag("editor_terminal_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = stringResource(R.string.action_shell),
                                    tint = if (state.isTerminalOpen) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (state.isRunning) "Running" else stringResource(R.string.action_shell),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isTerminalOpen) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Box {
                            LanguageBadge(
                                language = state.language,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { languageMenuOpen = true }
                                    .testTag("editor_language_selector")
                            )
                            DropdownMenu(
                                expanded = languageMenuOpen,
                                onDismissRequest = { languageMenuOpen = false }
                            ) {
                                CodeLanguage.entries.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.displayName) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(lang.markerColor)
                                            )
                                        },
                                        onClick = {
                                            languageMenuOpen = false
                                            viewModel.selectLanguage(lang)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showUnsavedExitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnsavedExitDialog,
            title = {
                Text(
                    text = "Unsaved changes",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "You have unsaved edits in \"${state.fileName}\". Would you like to save them before leaving?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveAndExit(onBack) },
                    modifier = Modifier.testTag("unsaved_dialog_save")
                ) {
                    Text("Save & exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = viewModel::dismissUnsavedExitDialog,
                        modifier = Modifier.testTag("unsaved_dialog_cancel")
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { viewModel.discardAndExit(onBack) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("unsaved_dialog_discard")
                    ) {
                        Text("Discard")
                    }
                }
            }
        )
    }
}

@Composable
private fun SaveStatusIndicator(
    status: SaveStatus,
    onSaveClick: () -> Unit
) {
    val (label, color) = when (status) {
        SaveStatus.SAVED -> "Saved locally" to SuccessEmerald
        SaveStatus.UNSAVED -> "Unsaved changes • Tap to save" to WarningAmber
        SaveStatus.SAVING -> "Saving..." to MaterialTheme.colorScheme.primary
        SaveStatus.ERROR -> "Save failed • Retry" to MaterialTheme.colorScheme.error
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = status == SaveStatus.UNSAVED || status == SaveStatus.ERROR, onClick = onSaveClick)
            .padding(vertical = 1.dp)
            .testTag("editor_save_indicator")
    ) {
        if (status == SaveStatus.SAVED) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditorSearchBar(
    state: EditorUiState,
    onQueryChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val matchSummary = when {
        state.searchQuery.isEmpty() -> "Type to find"
        state.searchMatches.isEmpty() -> "No matches"
        else -> "${state.activeMatchIndex + 1}/${state.searchMatches.size}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Find in file...") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            modifier = Modifier
                .weight(1f)
                .testTag("editor_search_input")
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (state.searchCaseSensitive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleCase)
                .testTag("editor_search_case_toggle")
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.searchCaseSensitive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }

        Text(
            text = matchSummary,
            style = MaterialTheme.typography.labelSmall,
            color = if (state.searchQuery.isNotEmpty() && state.searchMatches.isEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        IconButton(
            onClick = onPrev,
            enabled = state.searchMatches.isNotEmpty(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
        }
        IconButton(
            onClick = onNext,
            enabled = state.searchMatches.isNotEmpty(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close search")
        }
    }
}

@Composable
private fun QuickSymbolKey(
    label: String,
    onClick: () -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.subtleBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}
