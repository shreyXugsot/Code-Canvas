package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RepoResult
import com.example.data.repository.WorkspaceRepository
import com.example.model.CodeLanguage
import com.example.model.ProjectSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val allProjectsCount: Int = 0,
    val searchQuery: String = "",
    val selectedLanguage: CodeLanguage? = null
)

class ProjectsViewModel(
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedLanguage = MutableStateFlow<CodeLanguage?>(null)

    val uiState: StateFlow<ProjectsUiState> = combine(
        workspaceRepository.observeProjectSummaries(),
        searchQuery,
        selectedLanguage
    ) { allProjects, query, langFilter ->
        val trimmed = query.trim()
        val filtered = allProjects.filter { project ->
            val matchesQuery = trimmed.isEmpty() ||
                project.name.contains(trimmed, ignoreCase = true) ||
                project.description.contains(trimmed, ignoreCase = true) ||
                project.language.displayName.contains(trimmed, ignoreCase = true)
            val matchesLang = langFilter == null || project.language == langFilter
            matchesQuery && matchesLang
        }
        ProjectsUiState(
            projects = filtered,
            allProjectsCount = allProjects.size,
            searchQuery = query,
            selectedLanguage = langFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProjectsUiState()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setLanguageFilter(language: CodeLanguage?) {
        selectedLanguage.value = if (selectedLanguage.value == language) null else language
    }

    fun createProject(
        name: String,
        language: CodeLanguage,
        description: String,
        createStarterFile: Boolean,
        onResult: (RepoResult<Long>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(workspaceRepository.createProject(name, language, description, createStarterFile))
        }
    }

    fun renameProject(
        projectId: Long,
        newName: String,
        onResult: (RepoResult<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(workspaceRepository.renameProject(projectId, newName))
        }
    }

    fun deleteProject(projectId: Long, onResult: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(workspaceRepository.deleteProject(projectId))
        }
    }
}
