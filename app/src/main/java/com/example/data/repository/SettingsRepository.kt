package com.example.data.repository

import com.example.data.local.SettingsDataStore
import com.example.model.AccentPreset
import com.example.model.AppSettings
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val dataStore: SettingsDataStore) {
    val settings: Flow<AppSettings> = dataStore.settingsFlow

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    suspend fun setEditorFontSize(sizeSp: Int) = dataStore.setEditorFontSize(sizeSp)
    suspend fun setAccentPreset(preset: AccentPreset) = dataStore.setAccentPreset(preset)
    suspend fun setWordWrap(enabled: Boolean) = dataStore.setWordWrap(enabled)
    suspend fun setAutoSave(enabled: Boolean) = dataStore.setAutoSave(enabled)
}
