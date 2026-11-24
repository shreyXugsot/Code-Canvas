package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.TerminalLineKind
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCodeCanvasExtras
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.TerminalLine

@Composable
fun TerminalDrawer(
    projectName: String,
    fileName: String,
    isRunning: Boolean,
    isExpanded: Boolean,
    lines: List<TerminalLine>,
    lastExitCode: Int?,
    lastDurationMs: Long?,
    shellInput: String,
    onShellInputChange: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onCopyOutput: () -> Unit,
    onToggleExpanded: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = LocalCodeCanvasExtras.current
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    val drawerHeight = if (isExpanded) 300.dp else 196.dp
    val terminalSurface = if (extras.isDark) Color(0xFF090C12) else MaterialTheme.colorScheme.surfaceContainerLowest

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("terminal_drawer"),
        color = terminalSurface,
        border = BorderStroke(1.dp, extras.subtleBorder)
    ) {
        Column {
            // Terminal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "SHELL",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (projectName.isNotBlank()) {
                    Text(
                        text = "• $projectName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                } else if (lastExitCode != null) {
                    val ok = lastExitCode == 0
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (ok) SuccessEmerald else DangerCoral).copy(alpha = 0.16f)
                    ) {
                        val timing = if (lastDurationMs != null) " • ${lastDurationMs}ms" else ""
                        Text(
                            text = "exit $lastExitCode$timing",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ok) SuccessEmerald else DangerCoral,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isRunning) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("terminal_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop process",
                            tint = DangerCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onCopyOutput,
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("terminal_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy output",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onClear,
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("terminal_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear terminal",
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("terminal_expand_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse terminal" else "Expand terminal",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("terminal_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close terminal",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            HorizontalDivider(color = extras.subtleBorder)

            // Scrollable Monospace Output Log
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(drawerHeight)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = "Ready in sandbox workspace. Tap Run (▶) or enter a shell command below (e.g. ls, pwd, cat $fileName).",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(lines, key = { it.id }) { line ->
                            val lineColor = when (line.kind) {
                                TerminalLineKind.COMMAND -> MaterialTheme.colorScheme.primary
                                TerminalLineKind.STDOUT -> extras.syntaxPalette.plain
                                TerminalLineKind.STDERR -> DangerCoral
                                TerminalLineKind.INFO -> MaterialTheme.colorScheme.secondary
                            }
                            Text(
                                text = line.text,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = if (line.kind == TerminalLineKind.COMMAND) FontWeight.SemiBold else FontWeight.Normal,
                                    color = lineColor
                                )
                            )
                        }
                    }
                }
            }

            // Quick Shell Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TerminalQuickChip("ls -la") { onRunCommand("ls -la") }
                TerminalQuickChip("pwd") { onRunCommand("pwd") }
                if (fileName.isNotBlank()) {
                    TerminalQuickChip("wc -l $fileName") { onRunCommand("wc -l $fileName") }
                }
                TerminalQuickChip("clear") { onRunCommand("clear") }
            }

            HorizontalDivider(color = extras.subtleBorder)

            // Interactive `sh >` Command Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "sh >",
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (shellInput.isEmpty()) {
                        Text(
                            text = "Enter shell command...",
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    }
                    BasicTextField(
                        value = shellInput,
                        onValueChange = onShellInputChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onRunCommand(shellInput) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_command_input")
                    )
                }

                IconButton(
                    onClick = { onRunCommand(shellInput) },
                    enabled = shellInput.isNotBlank() && !isRunning,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("terminal_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Run command",
                        tint = if (shellInput.isNotBlank() && !isRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalQuickChip(
    label: String,
    onClick: () -> Unit
) {
    val extras = LocalCodeCanvasExtras.current
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, extras.subtleBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
