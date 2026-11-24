package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import com.example.model.AccentPreset
import com.example.model.AppSettings
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setEditorFontSize(sizeSp: Int) {
        viewModelScope.launch { settingsRepository.setEditorFontSize(sizeSp) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { settingsRepository.setAccentPreset(preset) }
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWordWrap(enabled) }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSave(enabled) }
    }
}
