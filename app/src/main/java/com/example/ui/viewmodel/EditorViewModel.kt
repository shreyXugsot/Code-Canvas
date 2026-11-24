package com.example.ui.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RepoResult
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.editor.ShellExecutionEngine
import com.example.editor.TerminalLineKind
import com.example.editor.ValidationUtils
import com.example.model.AppSettings
import com.example.model.CodeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

enum class SaveStatus {
    SAVED,
    UNSAVED,
    SAVING,
    ERROR
}

data class TerminalLine(
    val id: Long,
    val text: String,
    val kind: TerminalLineKind
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val fileNotFound: Boolean = false,
    val fileId: Long = 0L,
    val projectId: Long = 0L,
    val projectName: String = "",
    val fileName: String = "",
    val language: CodeLanguage = CodeLanguage.PLAIN_TEXT,
    val isFavorite: Boolean = false,
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val lastSavedText: String = "",
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val errorMessage: String? = null,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchCaseSensitive: Boolean = false,
    val searchMatches: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1,
    val showUnsavedExitDialog: Boolean = false,
    val isTerminalOpen: Boolean = false,
    val isTerminalExpanded: Boolean = false,
    val isRunning: Boolean = false,
    val terminalLines: List<TerminalLine> = emptyList(),
    val lastExitCode: Int? = null,
    val lastDurationMs: Long? = null,
    val shellInput: String = ""
) {
    val isDirty: Boolean
        get() = textFieldValue.text != lastSavedText

    val lineCount: Int
        get() = if (textFieldValue.text.isEmpty()) 1 else textFieldValue.text.count { it == '\n' } + 1

    val currentLineNumber: Int
        get() {
            val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
            return textFieldValue.text.substring(0, pos).count { it == '\n' } + 1
        }

    val currentColumnNumber: Int
        get() {
            val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
            val lastNewLine = textFieldValue.text.lastIndexOf('\n', startIndex = (pos - 1).coerceAtLeast(0))
            return if (pos == 0 || lastNewLine < 0) pos + 1 else pos - lastNewLine
        }
}

class EditorViewModel(
    private val fileId: Long,
    private val workspaceRepository: WorkspaceRepository,
    private val settingsRepository: SettingsRepository,
    private val shellExecutionEngine: ShellExecutionEngine
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    private val _uiState = MutableStateFlow(EditorUiState(fileId = fileId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var executionJob: Job? = null
    private val lineIdCounter = AtomicLong(1L)

    init {
        loadFile()
    }

    private fun loadFile() {
        viewModelScope.launch {
            val node = workspaceRepository.getNode(fileId)
            if (node == null || node.isFolder) {
                _uiState.update { it.copy(isLoading = false, fileNotFound = true) }
                return@launch
            }
            workspaceRepository.markProjectOpened(node.projectId)
            val project = workspaceRepository.getProjectSnapshot(node.projectId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    fileNotFound = false,
                    fileId = node.id,
                    projectId = node.projectId,
                    projectName = project?.name.orEmpty(),
                    fileName = node.name,
                    language = CodeLanguage.fromNameOrDefault(node.language),
                    isFavorite = node.isFavorite,
                    textFieldValue = TextFieldValue(node.content, TextRange(0)),
                    lastSavedText = node.content,
                    saveStatus = SaveStatus.SAVED,
                    errorMessage = null
                )
            }
        }
    }

    fun onCodeChange(newValue: TextFieldValue) {
        _uiState.update { state ->
            val dirty = newValue.text != state.lastSavedText
            val matches = if (state.isSearchOpen && state.searchQuery.isNotEmpty()) {
                ValidationUtils.findMatchRanges(
                    newValue.text,
                    state.searchQuery,
                    ignoreCase = !state.searchCaseSensitive
                )
            } else {
                emptyList()
            }
            val nextIdx = when {
                matches.isEmpty() -> -1
                state.activeMatchIndex in matches.indices -> state.activeMatchIndex
                else -> 0
            }
            state.copy(
                textFieldValue = newValue,
                saveStatus = if (dirty) SaveStatus.UNSAVED else SaveStatus.SAVED,
                errorMessage = null,
                searchMatches = matches,
                activeMatchIndex = nextIdx
            )
        }
        scheduleAutoSaveIfEnabled()
    }

    private fun scheduleAutoSaveIfEnabled() {
        autoSaveJob?.cancel()
        if (!settings.value.autoSaveOnLeave) return
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            saveNow()
        }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSave(enabled)
        }
        if (enabled) {
            if (_uiState.value.isDirty) {
                autoSaveJob?.cancel()
                autoSaveJob = viewModelScope.launch {
                    delay(AUTO_SAVE_DELAY_MS)
                    saveNow()
                }
            }
        } else {
            autoSaveJob?.cancel()
        }
    }

    fun saveNow(onComplete: ((Boolean) -> Unit)? = null) {
        val state = _uiState.value
        if (state.isLoading || state.fileNotFound) {
            onComplete?.invoke(false)
            return
        }
        autoSaveJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING, errorMessage = null) }
            val contentToSave = _uiState.value.textFieldValue.text
            val langToSave = _uiState.value.language
            when (val res = workspaceRepository.saveFileContent(fileId, contentToSave, langToSave)) {
                is RepoResult.Success -> {
                    _uiState.update { current ->
                        val stillMatches = current.textFieldValue.text == contentToSave
                        current.copy(
                            lastSavedText = contentToSave,
                            saveStatus = if (stillMatches) SaveStatus.SAVED else SaveStatus.UNSAVED,
                            errorMessage = null
                        )
                    }
                    onComplete?.invoke(true)
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(saveStatus = SaveStatus.ERROR, errorMessage = res.message)
                    }
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun selectLanguage(language: CodeLanguage) {
        _uiState.update {
            it.copy(
                language = language,
                saveStatus = SaveStatus.UNSAVED
            )
        }
        saveNow()
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            when (val res = workspaceRepository.toggleFavorite(fileId)) {
                is RepoResult.Success -> {
                    _uiState.update { it.copy(isFavorite = res.data) }
                }
                is RepoResult.Error -> {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }
    }

    fun insertSymbol(symbol: String, cursorBackOffset: Int = 0) {
        val current = _uiState.value.textFieldValue
        val text = current.text
        val start = current.selection.min.coerceIn(0, text.length)
        val end = current.selection.max.coerceIn(start, text.length)
        val updated = buildString {
            append(text.substring(0, start))
            append(symbol)
            append(text.substring(end))
        }
        val newCursor = (start + symbol.length - cursorBackOffset).coerceIn(0, updated.length)
        onCodeChange(TextFieldValue(updated, TextRange(newCursor)))
    }

    fun toggleSearchBar() {
        _uiState.update { state ->
            val nextOpen = !state.isSearchOpen
            if (!nextOpen) {
                state.copy(
                    isSearchOpen = false,
                    searchMatches = emptyList(),
                    activeMatchIndex = -1
                )
            } else {
                val matches = if (state.searchQuery.isNotEmpty()) {
                    ValidationUtils.findMatchRanges(
                        state.textFieldValue.text,
                        state.searchQuery,
                        ignoreCase = !state.searchCaseSensitive
                    )
                } else emptyList()
                state.copy(
                    isSearchOpen = true,
                    searchMatches = matches,
                    activeMatchIndex = if (matches.isNotEmpty()) 0 else -1
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val matches = if (query.isNotEmpty()) {
                ValidationUtils.findMatchRanges(
                    state.textFieldValue.text,
                    query,
                    ignoreCase = !state.searchCaseSensitive
                )
            } else emptyList()
            val firstRange = matches.firstOrNull()
            val updatedValue = if (firstRange != null) {
                state.textFieldValue.copy(selection = TextRange(firstRange.first, firstRange.last + 1))
            } else state.textFieldValue
            state.copy(
                searchQuery = query,
                searchMatches = matches,
                activeMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                textFieldValue = updatedValue
            )
        }
    }

    fun toggleSearchCaseSensitive() {
        _uiState.update { state ->
            val nextCase = !state.searchCaseSensitive
            val matches = if (state.searchQuery.isNotEmpty()) {
                ValidationUtils.findMatchRanges(
                    state.textFieldValue.text,
                    state.searchQuery,
                    ignoreCase = !nextCase
                )
            } else emptyList()
            state.copy(
                searchCaseSensitive = nextCase,
                searchMatches = matches,
                activeMatchIndex = if (matches.isNotEmpty()) 0 else -1
            )
        }
    }

    fun stepSearchMatch(delta: Int) {
        _uiState.update { state ->
            val count = state.searchMatches.size
            if (count == 0) return@update state
            val nextIndex = ((state.activeMatchIndex + delta) % count + count) % count
            val range = state.searchMatches[nextIndex]
            state.copy(
                activeMatchIndex = nextIndex,
                textFieldValue = state.textFieldValue.copy(
                    selection = TextRange(range.first, range.last + 1)
                )
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onExitRequested(onReadyToLeave: () -> Unit) {
        val state = _uiState.value
        val hasUnsavedEdits = state.isDirty || state.saveStatus == SaveStatus.UNSAVED || state.saveStatus == SaveStatus.ERROR
        if (!hasUnsavedEdits) {
            autoSaveJob?.cancel()
            onReadyToLeave()
            return
        }

        if (settings.value.autoSaveOnLeave) {
            saveNow { ok ->
                if (ok) onReadyToLeave()
            }
        } else {
            _uiState.update { it.copy(showUnsavedExitDialog = true) }
        }
    }

    fun dismissUnsavedExitDialog() {
        _uiState.update { it.copy(showUnsavedExitDialog = false) }
    }

    fun saveAndExit(onReadyToLeave: () -> Unit) {
        _uiState.update { it.copy(showUnsavedExitDialog = false) }
        saveNow { ok ->
            if (ok) onReadyToLeave()
        }
    }

    fun toggleTerminal() {
        _uiState.update { it.copy(isTerminalOpen = !it.isTerminalOpen) }
    }

    fun toggleTerminalExpanded() {
        _uiState.update { it.copy(isTerminalExpanded = !it.isTerminalExpanded) }
    }

    fun onShellInputChange(input: String) {
        _uiState.update { it.copy(shellInput = input) }
    }

    fun clearTerminal() {
        _uiState.update {
            it.copy(
                terminalLines = emptyList(),
                lastExitCode = null,
                lastDurationMs = null
            )
        }
    }

    fun getTerminalLogText(): String {
        return _uiState.value.terminalLines.joinToString("\n") { it.text }
    }

    fun runCurrentFile() {
        val state = _uiState.value
        if (state.isLoading || state.fileNotFound || state.isRunning) return

        autoSaveJob?.cancel()
        executionJob?.cancel()

        executionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTerminalOpen = true,
                    isRunning = true,
                    lastExitCode = null,
                    lastDurationMs = null
                )
            }

            // Confirmed requirement: automatically save unsaved edits before running
            if (!ensureSavedBeforeRun()) {
                _uiState.update { it.copy(isRunning = false, lastExitCode = 1) }
                return@launch
            }

            val current = _uiState.value
            val startMs = System.currentTimeMillis()
            val workspace = withContext(Dispatchers.IO) {
                val nodes = workspaceRepository.getProjectNodesSnapshot(current.projectId)
                shellExecutionEngine.materializeWorkspace(current.projectId, nodes, current.fileId)
            }
            val relPath = workspace.activeRelativePath.ifBlank { current.fileName }
            val code = current.textFieldValue.text
            val hasShellShebang = code.lineSequence().firstOrNull()?.trim()?.startsWith("#!/") == true &&
                (code.lineSequence().firstOrNull()?.contains("sh") == true)

            val exitCode = when {
                current.language == CodeLanguage.SHELL || hasShellShebang -> {
                    appendTerminalLine("$ sh $relPath", TerminalLineKind.COMMAND)
                    val targetFile = workspace.activeFile
                    if (targetFile == null) {
                        appendTerminalLine("Unable to locate materialized script file.", TerminalLineKind.STDERR)
                        1
                    } else {
                        shellExecutionEngine.runShellScript(targetFile, workspace.rootDir) { line, kind ->
                            appendTerminalLine(line, kind)
                        }
                    }
                }
                current.language == CodeLanguage.JAVASCRIPT -> {
                    appendTerminalLine("$ js $relPath", TerminalLineKind.COMMAND)
                    val result = shellExecutionEngine.evaluateJavaScript(code)
                    if (result.lines.isEmpty()) {
                        appendTerminalLine("(executed with no console output)", TerminalLineKind.INFO)
                    } else {
                        result.lines.forEach { (line, kind) -> appendTerminalLine(line, kind) }
                    }
                    result.exitCode
                }
                current.language == CodeLanguage.JSON -> {
                    appendTerminalLine("$ json-check $relPath", TerminalLineKind.COMMAND)
                    val result = shellExecutionEngine.validateJson(code)
                    result.lines.forEach { (line, kind) -> appendTerminalLine(line, kind) }
                    result.exitCode
                }
                else -> {
                    appendTerminalLine("$ run $relPath", TerminalLineKind.COMMAND)
                    shellExecutionEngine.buildUnsupportedLanguageGuidance(current.language, relPath)
                        .forEach { (line, kind) -> appendTerminalLine(line, kind) }
                    0
                }
            }

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    lastExitCode = exitCode,
                    lastDurationMs = elapsed
                )
            }
        }
    }

    fun runShellCommand(rawCommand: String = _uiState.value.shellInput) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return
        if (cmd == "clear" || cmd == "cls") {
            clearTerminal()
            _uiState.update { it.copy(shellInput = "") }
            return
        }
        val state = _uiState.value
        if (state.isLoading || state.fileNotFound || state.isRunning) return

        autoSaveJob?.cancel()
        executionJob?.cancel()

        executionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTerminalOpen = true,
                    isRunning = true,
                    shellInput = "",
                    lastExitCode = null,
                    lastDurationMs = null
                )
            }

            if (!ensureSavedBeforeRun()) {
                _uiState.update { it.copy(isRunning = false, lastExitCode = 1) }
                return@launch
            }

            appendTerminalLine("$ $cmd", TerminalLineKind.COMMAND)
            val startMs = System.currentTimeMillis()
            val current = _uiState.value
            val workspace = withContext(Dispatchers.IO) {
                val nodes = workspaceRepository.getProjectNodesSnapshot(current.projectId)
                shellExecutionEngine.materializeWorkspace(current.projectId, nodes, current.fileId)
            }
            val exitCode = shellExecutionEngine.runShellCommand(cmd, workspace.rootDir) { line, kind ->
                appendTerminalLine(line, kind)
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    lastExitCode = exitCode,
                    lastDurationMs = elapsed
                )
            }
        }
    }

    fun stopExecution() {
        executionJob?.cancel()
        shellExecutionEngine.stopCurrentProcess()
        appendTerminalLine("Process stopped.", TerminalLineKind.INFO)
        _uiState.update {
            it.copy(
                isRunning = false,
                lastExitCode = 130
            )
        }
    }

    private suspend fun ensureSavedBeforeRun(): Boolean {
        val state = _uiState.value
        if (!state.isDirty && state.saveStatus != SaveStatus.UNSAVED) return true
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING, errorMessage = null) }
        val contentToSave = state.textFieldValue.text
        val langToSave = state.language
        return when (val res = workspaceRepository.saveFileContent(fileId, contentToSave, langToSave)) {
            is RepoResult.Success -> {
                _uiState.update { current ->
                    val stillMatches = current.textFieldValue.text == contentToSave
                    current.copy(
                        lastSavedText = contentToSave,
                        saveStatus = if (stillMatches) SaveStatus.SAVED else SaveStatus.UNSAVED,
                        errorMessage = null
                    )
                }
                true
            }
            is RepoResult.Error -> {
                appendTerminalLine("Save failed before run: ${res.message}", TerminalLineKind.STDERR)
                _uiState.update {
                    it.copy(saveStatus = SaveStatus.ERROR, errorMessage = res.message)
                }
                false
            }
        }
    }

    private fun appendTerminalLine(text: String, kind: TerminalLineKind) {
        val entry = TerminalLine(
            id = lineIdCounter.getAndIncrement(),
            text = text,
            kind = kind
        )
        _uiState.update { state ->
            val updated = (state.terminalLines + entry).takeLast(400)
            state.copy(terminalLines = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        shellExecutionEngine.stopCurrentProcess()
    }

    fun discardAndExit(onReadyToLeave: () -> Unit) {
        autoSaveJob?.cancel()
        executionJob?.cancel()
        shellExecutionEngine.stopCurrentProcess()
        _uiState.update { it.copy(showUnsavedExitDialog = false) }
        onReadyToLeave()
    }

    companion object {
        const val AUTO_SAVE_DELAY_MS = 2_000L
    }
}
