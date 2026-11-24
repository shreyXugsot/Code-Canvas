package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentPreset
import com.example.model.AppSettings
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "codecanvas_prefs")

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_on_leave")
        val SEEDED_INITIAL = booleanPreferencesKey("seeded_initial_workspace")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.DARK

        val accent = prefs[Keys.ACCENT_PRESET]?.let { raw ->
            AccentPreset.entries.firstOrNull { it.name == raw }
        } ?: AccentPreset.ELECTRIC_BLUE

        val fontSize = (prefs[Keys.EDITOR_FONT_SIZE] ?: 14).coerceIn(11, 22)
        val wordWrap = prefs[Keys.WORD_WRAP] ?: false
        val autoSave = prefs[Keys.AUTO_SAVE] ?: true

        AppSettings(
            themeMode = theme,
            editorFontSizeSp = fontSize,
            accentPreset = accent,
            wordWrap = wordWrap,
            autoSaveOnLeave = autoSave
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setEditorFontSize(sizeSp: Int) {
        context.settingsDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(11, 22) }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.settingsDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun hasSeededInitialData(): Boolean {
        return context.settingsDataStore.data.first()[Keys.SEEDED_INITIAL] ?: false
    }

    suspend fun markInitialDataSeeded() {
        context.settingsDataStore.edit { it[Keys.SEEDED_INITIAL] = true }
    }
}
