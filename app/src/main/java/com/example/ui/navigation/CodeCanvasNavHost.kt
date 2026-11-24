package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.R
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AppViewModelFactory
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ExplorerViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ProjectsViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EXPLORER = "explorer/{projectId}"
    const val EDITOR = "editor/{fileId}"

    fun explorer(projectId: Long) = "explorer/$projectId"
    fun editor(fileId: Long) = "editor/$fileId"
}

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = Routes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopDestination(
        route = Routes.PROJECTS,
        labelRes = R.string.nav_projects,
        selectedIcon = Icons.Rounded.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_projects"
    ),
    TopDestination(
        route = Routes.LIBRARY,
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Rounded.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_library"
    ),
    TopDestination(
        route = Routes.SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@Composable
fun CodeCanvasAppShell(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showTopLevelNav = currentRoute in topDestinations.map { it.route }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navigateToTopTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = if (showTopLevelNav) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    composable(Routes.HOME) {
                        val vm: HomeViewModel = viewModel(
                            factory = AppViewModelFactory(container)
                        )
                        HomeScreen(
                            viewModel = vm,
                            onOpenProject = { projectId ->
                                navController.navigate(Routes.explorer(projectId))
                            },
                            onOpenFile = { fileId ->
                                navController.navigate(Routes.editor(fileId))
                            },
                            onNavigateToProjects = { navigateToTopTab(Routes.PROJECTS) },
                            onNavigateToLibrary = { navigateToTopTab(Routes.LIBRARY) }
                        )
                    }

                    composable(Routes.PROJECTS) {
                        val vm: ProjectsViewModel = viewModel(
                            factory = AppViewModelFactory(container)
                        )
                        ProjectsScreen(
                            viewModel = vm,
                            onOpenProject = { projectId ->
                                navController.navigate(Routes.explorer(projectId))
                            },
                            onShowMessage = showMessage
                        )
                    }

                    composable(Routes.LIBRARY) {
                        val vm: LibraryViewModel = viewModel(
                            factory = AppViewModelFactory(container)
                        )
                        LibraryScreen(
                            viewModel = vm,
                            onShowMessage = showMessage
                        )
                    }

                    composable(Routes.SETTINGS) {
                        val vm: SettingsViewModel = viewModel(
                            factory = AppViewModelFactory(container)
                        )
                        SettingsScreen(viewModel = vm)
                    }

                    composable(
                        route = Routes.EXPLORER,
                        arguments = listOf(navArgument("projectId") { type = NavType.LongType })
                    ) { entry ->
                        val projectId = entry.arguments?.getLong("projectId") ?: 0L
                        val vm: ExplorerViewModel = viewModel(
                            key = "explorer_$projectId",
                            factory = AppViewModelFactory(container, projectId = projectId)
                        )
                        ExplorerScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenFile = { fileId ->
                                navController.navigate(Routes.editor(fileId))
                            },
                            onShowMessage = showMessage
                        )
                    }

                    composable(
                        route = Routes.EDITOR,
                        arguments = listOf(navArgument("fileId") { type = NavType.LongType })
                    ) { entry ->
                        val fileId = entry.arguments?.getLong("fileId") ?: 0L
                        val vm: EditorViewModel = viewModel(
                            key = "editor_$fileId",
                            factory = AppViewModelFactory(container, fileId = fileId)
                        )
                        EditorScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onShowMessage = showMessage
                        )
                    }
                }
            }
        }
    }
}
