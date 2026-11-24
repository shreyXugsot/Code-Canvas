package com.example.model

import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class AccentPreset(
    val label: String,
    val primaryDark: Color,
    val secondaryDark: Color,
    val primaryLight: Color,
    val secondaryLight: Color
) {
    ELECTRIC_BLUE(
        label = "Electric Blue",
        primaryDark = Color(0xFF4C8DFF),
        secondaryDark = Color(0xFF8B5CF6),
        primaryLight = Color(0xFF2563EB),
        secondaryLight = Color(0xFF7C3AED)
    ),
    CYBER_VIOLET(
        label = "Cyber Violet",
        primaryDark = Color(0xFF9D7BFF),
        secondaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFF7C3AED),
        secondaryLight = Color(0xFF0284C7)
    ),
    CYAN_PULSE(
        label = "Cyan Pulse",
        primaryDark = Color(0xFF22D3EE),
        secondaryDark = Color(0xFF60A5FA),
        primaryLight = Color(0xFF0891B2),
        secondaryLight = Color(0xFF2563EB)
    ),
    EMERALD_MINT(
        label = "Emerald Mint",
        primaryDark = Color(0xFF34D399),
        secondaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFF059669),
        secondaryLight = Color(0xFF0284C7)
    ),
    ROSE_CORAL(
        label = "Rose Coral",
        primaryDark = Color(0xFFFB7185),
        secondaryDark = Color(0xFFA78BFA),
        primaryLight = Color(0xFFE11D48),
        secondaryLight = Color(0xFF7C3AED)
    )
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val editorFontSizeSp: Int = 14,
    val accentPreset: AccentPreset = AccentPreset.ELECTRIC_BLUE,
    val wordWrap: Boolean = false,
    val autoSaveOnLeave: Boolean = true
)

enum class LibraryItemType(val label: String) {
    SNIPPET("Snippet"),
    NOTE("Note")
}

data class ProjectSummary(
    val id: Long,
    val name: String,
    val language: CodeLanguage,
    val description: String,
    val fileCount: Int,
    val folderCount: Int,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val previewFileName: String?,
    val previewSnippet: String?
)

data class FavoriteFileItem(
    val fileId: Long,
    val projectId: Long,
    val projectName: String,
    val fileName: String,
    val language: CodeLanguage,
    val lineCount: Int,
    val updatedAt: Long
)

data class BreadcrumbSegment(
    val folderId: Long?,
    val label: String
)
