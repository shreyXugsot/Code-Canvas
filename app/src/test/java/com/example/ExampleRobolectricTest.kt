package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CodeCanvasDatabase
import com.example.data.local.NodeEntity
import com.example.data.local.SettingsDataStore
import com.example.data.repository.RepoResult
import com.example.data.repository.WorkspaceRepository
import com.example.editor.ShellExecutionEngine
import com.example.editor.TerminalLineKind
import com.example.model.CodeLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var db: CodeCanvasDatabase
    private lateinit var repository: WorkspaceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CodeCanvasDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkspaceRepository(
            projectDao = db.projectDao(),
            nodeDao = db.nodeDao(),
            libraryDao = db.libraryDao(),
            settingsDataStore = SettingsDataStore(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("CodeCanvas", appName)
    }

    @Test
    fun `create project prevents duplicate names and supports recursive folder delete`() = runBlocking {
        val first = repository.createProject("AlphaApp", CodeLanguage.KOTLIN, createStarterFile = false)
        assertTrue(first is RepoResult.Success)
        val projectId = (first as RepoResult.Success).data

        val duplicate = repository.createProject("alphaapp", CodeLanguage.PYTHON, createStarterFile = false)
        assertTrue(duplicate is RepoResult.Error)

        val folderRes = repository.createNode(projectId, null, "src", isFolder = true)
        assertTrue(folderRes is RepoResult.Success)
        val folderId = (folderRes as RepoResult.Success).data

        val fileRes = repository.createNode(projectId, folderId, "Main.kt", isFolder = false, initialContent = "fun main() {}")
        assertTrue(fileRes is RepoResult.Success)

        val dupFile = repository.createNode(projectId, folderId, "main.kt", isFolder = false)
        assertTrue(dupFile is RepoResult.Error)

        val nodesBefore = repository.observeProjectNodes(projectId).first()
        assertEquals(2, nodesBefore.size)

        repository.deleteNodeRecursive(folderId)
        val nodesAfter = repository.observeProjectNodes(projectId).first()
        assertEquals(0, nodesAfter.size)
    }

    @Test
    fun `app shell navigates across Home, Projects, Library, Settings, and Editor`() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_new_project_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_new_project_button").assertIsDisplayed()

        // Navigate to Projects tab
        composeRule.onNodeWithTag("nav_projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_search_input").assertIsDisplayed()

        // Open new project dialog and test validation on empty name
        composeRule.onNodeWithTag("projects_fab_new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_create_project_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Project name cannot be empty.").assertIsDisplayed()

        // Create a valid project and open Explorer
        composeRule.onNodeWithTag("project_name_input").performTextInput("PocketNotes")
        composeRule.onNodeWithTag("confirm_create_project_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("explorer_new_file_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open the starter file in Editor
        composeRule.onNodeWithText("main.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("code_editor_textfield").fetchSemanticsNodes().isNotEmpty()
        }

        // Toggle Auto-save OFF via Editor overflow menu
        composeRule.onNodeWithTag("editor_more_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_menu_autosave").performClick()
        composeRule.waitForIdle()

        // Edit file and tap Back -> Unsaved changes confirmation dialog must appear
        composeRule.onNodeWithTag("code_editor_textfield").performTextInput("\n// manual edit")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Unsaved changes").assertIsDisplayed()

        // Save & exit navigates back to Explorer
        composeRule.onNodeWithTag("unsaved_dialog_save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("explorer_back_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Go back to Projects -> Library -> Settings
        composeRule.onNodeWithTag("explorer_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_mode_LIGHT").performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("setting_auto_save"))
        composeRule.onNodeWithTag("setting_auto_save").assertIsDisplayed().assertIsOff()
        composeRule.waitForIdle()
    }

    @Test
    fun `shell execution engine validates json and runs sh scripts in sandbox`() = runBlocking {
        val engine = ShellExecutionEngine(context)

        val validJson = engine.validateJson("""{"app": "CodeCanvas", "count": 2}""")
        assertEquals(0, validJson.exitCode)
        assertTrue(validJson.lines.any { it.first.contains("Valid JSON object") })

        val invalidJson = engine.validateJson("""{"broken": """)
        assertEquals(1, invalidJson.exitCode)

        val nodes = listOf(
            NodeEntity(
                id = 10L,
                projectId = 99L,
                parentId = null,
                name = "scripts",
                isFolder = true,
                language = CodeLanguage.PLAIN_TEXT.name,
                updatedAt = 1L
            ),
            NodeEntity(
                id = 11L,
                projectId = 99L,
                parentId = 10L,
                name = "hello.sh",
                isFolder = false,
                language = CodeLanguage.SHELL.name,
                content = "#!/system/bin/sh\necho \"hello_from_sandbox\"\necho \"warn_line\" >&2\nexit 0\n",
                updatedAt = 1L
            )
        )
        val workspace = engine.materializeWorkspace(projectId = 99L, nodes = nodes, activeFileId = 11L)
        assertEquals("scripts/hello.sh", workspace.activeRelativePath)
        assertTrue(workspace.activeFile?.exists() == true)

        val scriptLines = mutableListOf<Pair<String, TerminalLineKind>>()
        val scriptExit = engine.runShellScript(workspace.activeFile!!, workspace.rootDir) { line, kind ->
            scriptLines.add(line to kind)
        }
        assertEquals(0, scriptExit)
        assertTrue(scriptLines.any { it.first == "hello_from_sandbox" && it.second == TerminalLineKind.STDOUT })
        assertTrue(scriptLines.any { it.first == "warn_line" && it.second == TerminalLineKind.STDERR })

        val cmdLines = mutableListOf<Pair<String, TerminalLineKind>>()
        val cmdExit = engine.runShellCommand("ls scripts && exit 7", workspace.rootDir) { line, kind ->
            cmdLines.add(line to kind)
        }
        assertEquals(7, cmdExit)
        assertTrue(cmdLines.any { it.first == "hello.sh" })
    }

    @Test
    fun `editor run button auto-saves dirty shell script and opens interactive terminal drawer`() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_new_project_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open seeded PulseWeather project from Home recent projects
        composeRule.onNodeWithText("PulseWeather").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("explorer_new_file_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open inspect.sh in Editor (wait for Explorer Room flow to emit nodes)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("inspect.sh").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("inspect.sh").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("code_editor_textfield").fetchSemanticsNodes().isNotEmpty()
        }

        // Edit the script so it becomes UNSAVED and prints a computed token not literally in source
        composeRule.onNodeWithTag("code_editor_textfield")
            .performTextReplacement("#!/bin/sh\nprintf 'SHELL_%s\\n' 'VERIFIED'\n")
        composeRule.waitForIdle()

        // Tap Run -> should auto-save, open TerminalDrawer, and display stdout + exit 0
        composeRule.onNodeWithTag("editor_run_button").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag("terminal_drawer").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("exit 0", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("SHELL_VERIFIED").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("SHELL_VERIFIED").assertIsDisplayed()
        composeRule.onNodeWithText("Saved locally").assertIsDisplayed()

        // Run an interactive command in the shell input bar
        composeRule.onNodeWithTag("terminal_command_input").performTextInput("printf 'CMD_%s\\n' 'PASS'")
        composeRule.onNodeWithTag("terminal_send_button").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("CMD_PASS").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("CMD_PASS").assertIsDisplayed()

        // Close drawer via Close button
        composeRule.onNodeWithTag("terminal_close_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("terminal_drawer").fetchSemanticsNodes().isEmpty()
        }
    }
}
