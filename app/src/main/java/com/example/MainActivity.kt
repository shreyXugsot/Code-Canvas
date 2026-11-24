package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppSettings
import com.example.model.ThemeMode
import com.example.ui.navigation.CodeCanvasAppShell
import com.example.ui.theme.CodeCanvasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CodeCanvasApplication).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            SideEffect {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            CodeCanvasTheme(
                themeMode = settings.themeMode,
                accentPreset = settings.accentPreset
            ) {
                CodeCanvasAppShell(container = container)
            }
        }
    }
}
