package com.example

import android.app.Application
import com.example.data.local.CodeCanvasDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.LibraryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.editor.ShellExecutionEngine

class AppContainer(application: Application) {
    private val database by lazy { CodeCanvasDatabase.getInstance(application) }
    private val settingsDataStore by lazy { SettingsDataStore(application) }

    val settingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val workspaceRepository by lazy {
        WorkspaceRepository(
            projectDao = database.projectDao(),
            nodeDao = database.nodeDao(),
            libraryDao = database.libraryDao(),
            settingsDataStore = settingsDataStore
        )
    }
    val libraryRepository by lazy { LibraryRepository(database.libraryDao()) }
    val shellExecutionEngine by lazy { ShellExecutionEngine(application) }
}

class CodeCanvasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
