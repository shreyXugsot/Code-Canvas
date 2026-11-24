package com.example.data.repository

import com.example.data.local.LibraryDao
import com.example.data.local.LibraryItemEntity
import com.example.data.local.NodeDao
import com.example.data.local.NodeEntity
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.SettingsDataStore
import com.example.editor.ValidationResult
import com.example.editor.ValidationUtils
import com.example.model.CodeLanguage
import com.example.model.FavoriteFileItem
import com.example.model.LibraryItemType
import com.example.model.ProjectSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String) : RepoResult<Nothing>()
}

class WorkspaceRepository(
    private val projectDao: ProjectDao,
    private val nodeDao: NodeDao,
    private val libraryDao: LibraryDao,
    private val settingsDataStore: SettingsDataStore
) {
    fun observeProjectSummaries(): Flow<List<ProjectSummary>> {
        return combine(
            projectDao.observeAllProjects(),
            nodeDao.observeAllNodes()
        ) { projects, allNodes ->
            val nodesByProject = allNodes.groupBy { it.projectId }
            projects.map { project ->
                val projectNodes = nodesByProject[project.id].orEmpty()
                val files = projectNodes.filter { !it.isFolder }
                val folders = projectNodes.filter { it.isFolder }
                val previewNode = files
                    .sortedByDescending { it.updatedAt }
                    .firstOrNull { it.content.isNotBlank() } ?: files.firstOrNull()

                val previewLines = previewNode?.content
                    ?.lineSequence()
                    ?.take(4)
                    ?.joinToString("\n")

                ProjectSummary(
                    id = project.id,
                    name = project.name,
                    language = CodeLanguage.fromNameOrDefault(project.language),
                    description = project.description,
                    fileCount = files.size,
                    folderCount = folders.size,
                    updatedAt = project.updatedAt,
                    lastOpenedAt = project.lastOpenedAt,
                    previewFileName = previewNode?.name,
                    previewSnippet = previewLines
                )
            }
        }
    }

    fun observeFavoriteFiles(): Flow<List<FavoriteFileItem>> {
        return combine(
            nodeDao.observeFavoriteFiles(),
            projectDao.observeAllProjects()
        ) { favNodes, projects ->
            val projectMap = projects.associateBy { it.id }
            favNodes.mapNotNull { node ->
                val project = projectMap[node.projectId] ?: return@mapNotNull null
                FavoriteFileItem(
                    fileId = node.id,
                    projectId = project.id,
                    projectName = project.name,
                    fileName = node.name,
                    language = CodeLanguage.fromNameOrDefault(node.language),
                    lineCount = if (node.content.isEmpty()) 0 else node.content.count { it == '\n' } + 1,
                    updatedAt = node.updatedAt
                )
            }
        }
    }

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = projectDao.observeProjectById(projectId)

    fun observeProjectNodes(projectId: Long): Flow<List<NodeEntity>> = nodeDao.observeNodesByProject(projectId)

    fun observeNode(nodeId: Long): Flow<NodeEntity?> = nodeDao.observeNodeById(nodeId)

    suspend fun getNode(nodeId: Long): NodeEntity? = nodeDao.getNodeById(nodeId)

    suspend fun getProjectSnapshot(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)

    suspend fun getProjectNodesSnapshot(projectId: Long): List<NodeEntity> = nodeDao.getNodesForProject(projectId)

    suspend fun markProjectOpened(projectId: Long) {
        try {
            projectDao.touchLastOpened(projectId, System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }

    suspend fun createProject(
        rawName: String,
        language: CodeLanguage,
        description: String = "",
        createStarterFile: Boolean = false
    ): RepoResult<Long> {
        return try {
            val existing = projectDao.getAllProjectNames()
            when (val check = ValidationUtils.validateProjectName(rawName, existing)) {
                is ValidationResult.Invalid -> return RepoResult.Error(check.message)
                ValidationResult.Valid -> Unit
            }
            val now = System.currentTimeMillis()
            val trimmed = rawName.trim()
            val projectId = projectDao.insertProject(
                ProjectEntity(
                    name = trimmed,
                    language = language.name,
                    description = description.trim(),
                    createdAt = now,
                    updatedAt = now,
                    lastOpenedAt = now
                )
            )
            if (createStarterFile) {
                val ext = language.defaultExtension
                val fileName = when (language) {
                    CodeLanguage.JSON -> "config.json"
                    CodeLanguage.MARKDOWN -> "README.md"
                    CodeLanguage.HTML -> "index.html"
                    CodeLanguage.CSS -> "styles.css"
                    else -> "main.$ext"
                }
                nodeDao.insertNode(
                    NodeEntity(
                        projectId = projectId,
                        parentId = null,
                        name = fileName,
                        isFolder = false,
                        language = language.name,
                        content = language.starterTemplate(trimmed),
                        isFavorite = false,
                        updatedAt = now
                    )
                )
            }
            RepoResult.Success(projectId)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Unable to create project.")
        }
    }

    suspend fun renameProject(projectId: Long, rawNewName: String): RepoResult<Unit> {
        return try {
            val current = projectDao.getProjectById(projectId)
                ?: return RepoResult.Error("Project no longer exists.")
            val otherNames = projectDao.getAllProjectNames()
                .filterNot { it.equals(current.name, ignoreCase = true) }
            when (val check = ValidationUtils.validateProjectName(rawNewName, otherNames)) {
                is ValidationResult.Invalid -> return RepoResult.Error(check.message)
                ValidationResult.Valid -> Unit
            }
            projectDao.updateProject(
                current.copy(
                    name = rawNewName.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to rename project.")
        }
    }

    suspend fun deleteProject(projectId: Long): RepoResult<Unit> {
        return try {
            projectDao.deleteProjectById(projectId)
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to delete project.")
        }
    }

    suspend fun createNode(
        projectId: Long,
        parentId: Long?,
        rawName: String,
        isFolder: Boolean,
        initialContent: String = "",
        explicitLanguage: CodeLanguage? = null
    ): RepoResult<Long> {
        return try {
            val siblings = nodeDao.getSiblingNames(projectId, parentId)
            when (val check = ValidationUtils.validateNodeName(rawName, isFolder, siblings)) {
                is ValidationResult.Invalid -> return RepoResult.Error(check.message)
                ValidationResult.Valid -> Unit
            }
            val project = projectDao.getProjectById(projectId)
                ?: return RepoResult.Error("Project not found.")
            val trimmed = rawName.trim()
            val fallbackLang = CodeLanguage.fromNameOrDefault(project.language)
            val resolvedLanguage = if (isFolder) {
                CodeLanguage.PLAIN_TEXT
            } else {
                explicitLanguage ?: CodeLanguage.inferFromFileName(trimmed, fallbackLang)
            }
            val now = System.currentTimeMillis()
            val newId = nodeDao.insertNode(
                NodeEntity(
                    projectId = projectId,
                    parentId = parentId,
                    name = trimmed,
                    isFolder = isFolder,
                    language = resolvedLanguage.name,
                    content = initialContent,
                    isFavorite = false,
                    updatedAt = now
                )
            )
            projectDao.touchUpdated(projectId, now)
            RepoResult.Success(newId)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Unable to create item.")
        }
    }

    suspend fun renameNode(nodeId: Long, rawNewName: String): RepoResult<Unit> {
        return try {
            val node = nodeDao.getNodeById(nodeId)
                ?: return RepoResult.Error("Item no longer exists.")
            val siblings = nodeDao.getSiblingNames(node.projectId, node.parentId)
                .filterNot { it.equals(node.name, ignoreCase = true) }
            when (val check = ValidationUtils.validateNodeName(rawNewName, node.isFolder, siblings)) {
                is ValidationResult.Invalid -> return RepoResult.Error(check.message)
                ValidationResult.Valid -> Unit
            }
            val trimmed = rawNewName.trim()
            val now = System.currentTimeMillis()
            val updatedLang = if (node.isFolder) {
                node.language
            } else {
                CodeLanguage.inferFromFileName(trimmed, CodeLanguage.fromNameOrDefault(node.language)).name
            }
            nodeDao.updateNode(node.copy(name = trimmed, language = updatedLang, updatedAt = now))
            projectDao.touchUpdated(node.projectId, now)
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Unable to rename item.")
        }
    }

    suspend fun deleteNodeRecursive(nodeId: Long): RepoResult<Unit> {
        return try {
            val target = nodeDao.getNodeById(nodeId) ?: return RepoResult.Success(Unit)
            val allNodes = nodeDao.getNodesForProject(target.projectId)
            val toDelete = mutableListOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(nodeId)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                toDelete.add(current)
                allNodes.filter { it.parentId == current }.forEach { child ->
                    queue.add(child.id)
                }
            }
            nodeDao.deleteNodesByIds(toDelete)
            projectDao.touchUpdated(target.projectId, System.currentTimeMillis())
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to delete item.")
        }
    }

    suspend fun saveFileContent(
        fileId: Long,
        content: String,
        language: CodeLanguage
    ): RepoResult<Unit> {
        return try {
            val current = nodeDao.getNodeById(fileId)
                ?: return RepoResult.Error("File was deleted or no longer exists.")
            val now = System.currentTimeMillis()
            nodeDao.updateNode(
                current.copy(
                    content = content,
                    language = language.name,
                    updatedAt = now
                )
            )
            projectDao.touchUpdated(current.projectId, now)
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to save file changes.")
        }
    }

    suspend fun toggleFavorite(fileId: Long): RepoResult<Boolean> {
        return try {
            val current = nodeDao.getNodeById(fileId)
                ?: return RepoResult.Error("File not found.")
            val next = !current.isFavorite
            nodeDao.setFavorite(fileId, next)
            RepoResult.Success(next)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to update favorite.")
        }
    }

    suspend fun seedInitialWorkspaceIfFirstLaunch() {
        if (settingsDataStore.hasSeededInitialData()) return
        if (projectDao.getProjectCount() > 0 || libraryDao.getCount() > 0) {
            settingsDataStore.markInitialDataSeeded()
            return
        }

        val now = System.currentTimeMillis()

        // Project 1: PulseWeather (Kotlin)
        val p1 = projectDao.insertProject(
            ProjectEntity(
                name = "PulseWeather",
                language = CodeLanguage.KOTLIN.name,
                description = "Offline-ready forecast parser & Compose widget models",
                createdAt = now - 86_400_000L * 2,
                updatedAt = now - 1_800_000L,
                lastOpenedAt = now - 600_000L
            )
        )
        val srcFolder1 = nodeDao.insertNode(
            NodeEntity(projectId = p1, parentId = null, name = "src", isFolder = true, language = CodeLanguage.PLAIN_TEXT.name, updatedAt = now - 1_800_000L)
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p1,
                parentId = srcFolder1,
                name = "ForecastEngine.kt",
                isFolder = false,
                language = CodeLanguage.KOTLIN.name,
                isFavorite = true,
                updatedAt = now - 1_200_000L,
                content = """
                    package dev.pulse.weather

                    import kotlin.math.roundToInt

                    data class HourlySnapshot(
                        val hourLabel: String,
                        val tempCelsius: Double,
                        val condition: String
                    )

                    // Converts raw sensor readings into display cards
                    fun formatSummary(snapshots: List<HourlySnapshot>): String {
                        val peak = snapshots.maxByOrNull { it.tempCelsius } ?: return "No readings"
                        val rounded = peak.tempCelsius.roundToInt()
                        return "Peak ${'$'}rounded°C at ${'$'}{peak.hourLabel} (${'$'}{peak.condition})"
                    }
                """.trimIndent()
            )
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p1,
                parentId = srcFolder1,
                name = "ThemeTokens.kt",
                isFolder = false,
                language = CodeLanguage.KOTLIN.name,
                isFavorite = false,
                updatedAt = now - 3_600_000L,
                content = """
                    package dev.pulse.weather.ui

                    object WeatherPalette {
                        const val STORM_SLATE = 0xFF1E293B
                        const val ELECTRIC_SKY = 0xFF38BDF8
                        const val SUNSET_AMBER = 0xFFF59E0B
                    }
                """.trimIndent()
            )
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p1,
                parentId = null,
                name = "README.md",
                isFolder = false,
                language = CodeLanguage.MARKDOWN.name,
                isFavorite = false,
                updatedAt = now - 7_200_000L,
                content = """
                    # PulseWeather

                    Lightweight Kotlin module for formatting hourly forecasts.

                    - **Zero network dependency** inside core formatters
                    - Unit tested with `HourlySnapshot` fixtures
                """.trimIndent()
            )
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p1,
                parentId = null,
                name = "inspect.sh",
                isFolder = false,
                language = CodeLanguage.SHELL.name,
                isFavorite = false,
                updatedAt = now - 600_000L,
                content = """
                    #!/bin/sh
                    echo "Project: PulseWeather"
                    echo "Files in workspace:"
                    ls -la
                    echo "Line counts:"
                    wc -l src/*.kt README.md 2>/dev/null
                """.trimIndent()
            )
        )

        // Project 2: TaskGrid (TypeScript)
        val p2 = projectDao.insertProject(
            ProjectEntity(
                name = "TaskGrid",
                language = CodeLanguage.TYPESCRIPT.name,
                description = "Keyboard-first sprint board state & JSON schema",
                createdAt = now - 86_400_000L * 4,
                updatedAt = now - 9_600_000L,
                lastOpenedAt = now - 5_400_000L
            )
        )
        val srcFolder2 = nodeDao.insertNode(
            NodeEntity(projectId = p2, parentId = null, name = "src", isFolder = true, language = CodeLanguage.PLAIN_TEXT.name, updatedAt = now - 9_600_000L)
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p2,
                parentId = srcFolder2,
                name = "boardStore.ts",
                isFolder = false,
                language = CodeLanguage.TYPESCRIPT.name,
                isFavorite = true,
                updatedAt = now - 9_600_000L,
                content = """
                    export type Priority = "low" | "medium" | "high";

                    export interface Ticket {
                      id: string;
                      title: string;
                      priority: Priority;
                      done: boolean;
                    }

                    export function groupOpenTickets(tickets: Ticket[]): Record<Priority, Ticket[]> {
                      return tickets
                        .filter((item) => !item.done)
                        .reduce(
                          (acc, item) => {
                            acc[item.priority].push(item);
                            return acc;
                          },
                          { low: [], medium: [], high: [] } as Record<Priority, Ticket[]>
                        );
                    }
                """.trimIndent()
            )
        )
        nodeDao.insertNode(
            NodeEntity(
                projectId = p2,
                parentId = null,
                name = "workspace.json",
                isFolder = false,
                language = CodeLanguage.JSON.name,
                isFavorite = false,
                updatedAt = now - 14_400_000L,
                content = """
                    {
                      "name": "taskgrid-web",
                      "version": "0.4.2",
                      "strictMode": true,
                      "shortcuts": {
                        "newTicket": "Cmd+K",
                        "toggleDone": "Space"
                      }
                    }
                """.trimIndent()
            )
        )

        // Starter Snippets & Notes
        libraryDao.insertItem(
            LibraryItemEntity(
                title = "Debounce StateFlow Search",
                type = LibraryItemType.SNIPPET.name,
                language = CodeLanguage.KOTLIN.name,
                tags = "kotlin, coroutines, flow",
                updatedAt = now - 2_400_000L,
                content = """
                    @OptIn(FlowPreview::class)
                    val results = searchQuery
                        .debounce(250)
                        .distinctUntilChanged()
                        .flatMapLatest { query -> repository.search(query) }
                """.trimIndent()
            )
        )
        libraryDao.insertItem(
            LibraryItemEntity(
                title = "Safe Python Retry Decorator",
                type = LibraryItemType.SNIPPET.name,
                language = CodeLanguage.PYTHON.name,
                tags = "python, resilience, utils",
                updatedAt = now - 10_800_000L,
                content = """
                    import time
                    from functools import wraps

                    def retry(times=3, delay=0.5):
                        def decorator(fn):
                            @wraps(fn)
                            def wrapper(*args, **kwargs):
                                for attempt in range(times):
                                    try:
                                        return fn(*args, **kwargs)
                                    except Exception:
                                        if attempt == times - 1:
                                            raise
                                        time.sleep(delay)
                            return wrapper
                        return decorator
                """.trimIndent()
            )
        )
        libraryDao.insertItem(
            LibraryItemEntity(
                title = "Mobile Editor UX Checklist",
                type = LibraryItemType.NOTE.name,
                language = CodeLanguage.MARKDOWN.name,
                tags = "design, mobile, architecture",
                updatedAt = now - 18_000_000L,
                content = """
                    Key details to keep code readable on narrow phones:
                    • Keep gutter line numbers aligned with exact code line height
                    • Provide a quick-symbol bar for {}, (), [], ->, and quotes
                    • Avoid wrapping long code lines unless word-wrap is explicitly enabled
                """.trimIndent()
            )
        )

        settingsDataStore.markInitialDataSeeded()
    }
}
