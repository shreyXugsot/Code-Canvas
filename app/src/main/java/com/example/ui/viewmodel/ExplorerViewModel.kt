package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NodeEntity
import com.example.data.local.ProjectEntity
import com.example.data.repository.RepoResult
import com.example.data.repository.WorkspaceRepository
import com.example.model.BreadcrumbSegment
import com.example.model.CodeLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExplorerUiState(
    val project: ProjectEntity? = null,
    val currentFolderId: Long? = null,
    val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
    val visibleNodes: List<NodeEntity> = emptyList(),
    val totalFileCount: Int = 0,
    val totalFolderCount: Int = 0,
    val childCountsByFolderId: Map<Long, Int> = emptyMap(),
    val isLoaded: Boolean = false
)

class ExplorerViewModel(
    private val projectId: Long,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            workspaceRepository.markProjectOpened(projectId)
        }
    }

    val uiState: StateFlow<ExplorerUiState> = combine(
        workspaceRepository.observeProject(projectId),
        workspaceRepository.observeProjectNodes(projectId),
        currentFolderId
    ) { project, allNodes, folderId ->
        val nodeById = allNodes.associateBy { it.id }

        // If the active folder was deleted, fall back cleanly to root
        val safeFolderId = if (folderId != null && nodeById.containsKey(folderId)) folderId else null
        if (safeFolderId != folderId) {
            currentFolderId.value = safeFolderId
        }

        val breadcrumbs = mutableListOf<BreadcrumbSegment>()
        breadcrumbs.add(BreadcrumbSegment(null, project?.name ?: "Root"))
        val chain = mutableListOf<BreadcrumbSegment>()
        var cursor = safeFolderId
        var guard = 0
        while (cursor != null && guard < 32) {
            val folder = nodeById[cursor] ?: break
            chain.add(BreadcrumbSegment(folder.id, folder.name))
            cursor = folder.parentId
            guard++
        }
        breadcrumbs.addAll(chain.reversed())

        val visible = allNodes.filter { it.parentId == safeFolderId }
        val childCounts = allNodes
            .filter { it.parentId != null }
            .groupingBy { it.parentId!! }
            .eachCount()

        ExplorerUiState(
            project = project,
            currentFolderId = safeFolderId,
            breadcrumbs = breadcrumbs,
            visibleNodes = visible,
            totalFileCount = allNodes.count { !it.isFolder },
            totalFolderCount = allNodes.count { it.isFolder },
            childCountsByFolderId = childCounts,
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExplorerUiState()
    )

    fun openFolder(folderId: Long?) {
        currentFolderId.value = folderId
    }

    fun navigateUp(): Boolean {
        val currentId = uiState.value.currentFolderId ?: return false
        val breadcrumbs = uiState.value.breadcrumbs
        val currentIdx = breadcrumbs.indexOfLast { it.folderId == currentId }
        val parentSegment = if (currentIdx > 0) breadcrumbs[currentIdx - 1] else BreadcrumbSegment(null, "")
        currentFolderId.value = parentSegment.folderId
        return true
    }

    fun createFile(
        name: String,
        content: String = "",
        language: CodeLanguage? = null,
        onResult: (RepoResult<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val res = workspaceRepository.createNode(
                projectId = projectId,
                parentId = uiState.value.currentFolderId,
                rawName = name,
                isFolder = false,
                initialContent = content,
                explicitLanguage = language
            )
            onResult(res)
        }
    }

    fun createFolder(name: String, onResult: (RepoResult<Long>) -> Unit) {
        viewModelScope.launch {
            val res = workspaceRepository.createNode(
                projectId = projectId,
                parentId = uiState.value.currentFolderId,
                rawName = name,
                isFolder = true
            )
            onResult(res)
        }
    }

    fun renameNode(nodeId: Long, newName: String, onResult: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(workspaceRepository.renameNode(nodeId, newName))
        }
    }

    fun deleteNode(nodeId: Long, onResult: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(workspaceRepository.deleteNodeRecursive(nodeId))
        }
    }

    fun toggleFavorite(nodeId: Long) {
        viewModelScope.launch {
            workspaceRepository.toggleFavorite(nodeId)
        }
    }

    fun countDescendants(folderId: Long, allNodes: List<NodeEntity>): Int {
        var count = 0
        val queue = ArrayDeque<Long>()
        queue.add(folderId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            allNodes.filter { it.parentId == id }.forEach {
                count++
                if (it.isFolder) queue.add(it.id)
            }
        }
        return count
    }
}
