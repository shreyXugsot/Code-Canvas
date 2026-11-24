package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.AppContainer

class AppViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long = 0L,
    private val fileId: Long = 0L
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(container.workspaceRepository, container.libraryRepository) as T
            modelClass.isAssignableFrom(ProjectsViewModel::class.java) ->
                ProjectsViewModel(container.workspaceRepository) as T
            modelClass.isAssignableFrom(ExplorerViewModel::class.java) ->
                ExplorerViewModel(projectId, container.workspaceRepository) as T
            modelClass.isAssignableFrom(EditorViewModel::class.java) ->
                EditorViewModel(
                    fileId = fileId,
                    workspaceRepository = container.workspaceRepository,
                    settingsRepository = container.settingsRepository,
                    shellExecutionEngine = container.shellExecutionEngine
                ) as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(container.libraryRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container.settingsRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
