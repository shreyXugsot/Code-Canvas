package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibraryItemEntity
import com.example.data.repository.LibraryRepository
import com.example.data.repository.RepoResult
import com.example.data.repository.WorkspaceRepository
import com.example.model.CodeLanguage
import com.example.model.FavoriteFileItem
import com.example.model.ProjectSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentProjects: List<ProjectSummary> = emptyList(),
    val favoriteFiles: List<FavoriteFileItem> = emptyList(),
    val recentLibraryItems: List<LibraryItemEntity> = emptyList(),
    val totalProjectCount: Int = 0,
    val totalFileCount: Int = 0,
    val isLoaded: Boolean = false
)

class HomeViewModel(
    private val workspaceRepository: WorkspaceRepository,
    libraryRepository: LibraryRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            workspaceRepository.seedInitialWorkspaceIfFirstLaunch()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        workspaceRepository.observeProjectSummaries(),
        workspaceRepository.observeFavoriteFiles(),
        libraryRepository.allItems
    ) { projects, favorites, library ->
        HomeUiState(
            recentProjects = projects.take(6),
            favoriteFiles = favorites.take(8),
            recentLibraryItems = library.take(4),
            totalProjectCount = projects.size,
            totalFileCount = projects.sumOf { it.fileCount },
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun createProject(
        name: String,
        language: CodeLanguage,
        description: String,
        createStarterFile: Boolean,
        onResult: (RepoResult<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val result = workspaceRepository.createProject(name, language, description, createStarterFile)
            onResult(result)
        }
    }

    fun toggleFavorite(fileId: Long) {
        viewModelScope.launch {
            workspaceRepository.toggleFavorite(fileId)
        }
    }
}
