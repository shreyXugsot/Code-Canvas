package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.editor.SyntaxHighlighter
import com.example.model.AccentPreset
import com.example.model.CodeLanguage
import com.example.model.ThemeMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extras = LocalCodeCanvasExtras.current

    val samplePreview = remember(extras.syntaxPalette) {
        SyntaxHighlighter.highlight(
            text = """
                // Live CodeCanvas editor preview
                fun greet(user: String): String {
                    val count = 42
                    return "Hello, ${'$'}user (#${'$'}count)"
                }
            """.trimIndent(),
            language = CodeLanguage.KOTLIN,
            palette = extras.syntaxPalette
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Customize appearance, editor typography, and local behavior",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Theme Mode
            item {
                SettingsCard(title = "Appearance theme") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_mode_${mode.name}")
                            )
                        }
                    }
                }
            }

            // Accent Color Picker
            item {
                SettingsCard(
                    title = "Accent palette",
                    subtitle = "Selected: ${settings.accentPreset.label}"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val isSelected = settings.accentPreset == preset
                            val swatchPrimary = if (extras.isDark) preset.primaryDark else preset.primaryLight
                            val swatchSecondary = if (extras.isDark) preset.secondaryDark else preset.secondaryLight

                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else extras.subtleBorder,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(colors = listOf(swatchPrimary, swatchSecondary))
                                    )
                                    .clickable { viewModel.setAccentPreset(preset) }
                                    .testTag("accent_swatch_${preset.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "${preset.label} selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Editor Font Size + Live Preview
            item {
                SettingsCard(
                    title = "Editor font size",
                    subtitle = "${settings.editorFontSizeSp} sp • JetBrains Mono"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.setEditorFontSize(settings.editorFontSizeSp - 1) },
                            enabled = settings.editorFontSizeSp > 11,
                            modifier = Modifier.testTag("font_size_decrease")
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease font size")
                        }

                        Slider(
                            value = settings.editorFontSizeSp.toFloat(),
                            onValueChange = { viewModel.setEditorFontSize(it.roundToInt()) },
                            valueRange = 11f..22f,
                            steps = 10,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("font_size_slider")
                        )

                        FilledTonalIconButton(
                            onClick = { viewModel.setEditorFontSize(settings.editorFontSizeSp + 1) },
                            enabled = settings.editorFontSizeSp < 22,
                            modifier = Modifier.testTag("font_size_increase")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase font size")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(extras.editorBg)
                            .border(1.dp, extras.subtleBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = samplePreview,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = settings.editorFontSizeSp.sp,
                            lineHeight = (settings.editorFontSizeSp * 1.48f).sp
                        )
                    }
                }
            }

            // Editor Behavior
            item {
                SettingsCard(title = "Editor behavior") {
                    SettingSwitchRow(
                        title = "Word wrap by default",
                        subtitle = "Wrap long lines inside the mobile viewport",
                        checked = settings.wordWrap,
                        onCheckedChange = viewModel::setWordWrap,
                        tag = "setting_word_wrap"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSwitchRow(
                        title = "Auto-save",
                        subtitle = "Save changes automatically 2 seconds after you pause typing",
                        checked = settings.autoSaveOnLeave,
                        onCheckedChange = viewModel::setAutoSave,
                        tag = "setting_auto_save"
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extras.subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CodeCanvas • Local Workspace",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "All projects, source files, notes, snippets, and settings are stored privately on this device with zero network dependency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.subtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
