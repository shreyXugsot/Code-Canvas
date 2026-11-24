package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibraryItemEntity
import com.example.data.repository.LibraryRepository
import com.example.data.repository.RepoResult
import com.example.editor.ValidationUtils
import com.example.model.CodeLanguage
import com.example.model.LibraryItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTabFilter(val label: String) {
    ALL("All"),
    SNIPPETS("Snippets"),
    NOTES("Notes")
}

data class LibraryUiState(
    val items: List<LibraryItemEntity> = emptyList(),
    val totalCount: Int = 0,
    val availableTags: List<String> = emptyList(),
    val selectedTab: LibraryTabFilter = LibraryTabFilter.ALL,
    val selectedTag: String? = null,
    val searchQuery: String = ""
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTabFilter.ALL)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.allItems,
        selectedTab,
        selectedTag,
        searchQuery
    ) { allItems, tab, tagFilter, query ->
        val allTags = allItems
            .flatMap { ValidationUtils.parseTags(it.tags) }
            .distinct()
            .sorted()

        val activeTag = if (tagFilter != null && tagFilter in allTags) tagFilter else null
        if (activeTag != tagFilter) selectedTag.value = activeTag

        val trimmed = query.trim()
        val filtered = allItems.filter { item ->
            val matchesTab = when (tab) {
                LibraryTabFilter.ALL -> true
                LibraryTabFilter.SNIPPETS -> item.type == LibraryItemType.SNIPPET.name
                LibraryTabFilter.NOTES -> item.type == LibraryItemType.NOTE.name
            }
            val itemTags = ValidationUtils.parseTags(item.tags)
            val matchesTag = activeTag == null || activeTag in itemTags
            val matchesQuery = trimmed.isEmpty() ||
                item.title.contains(trimmed, ignoreCase = true) ||
                item.content.contains(trimmed, ignoreCase = true) ||
                itemTags.any { it.contains(trimmed, ignoreCase = true) }

            matchesTab && matchesTag && matchesQuery
        }

        LibraryUiState(
            items = filtered,
            totalCount = allItems.size,
            availableTags = allTags,
            selectedTab = tab,
            selectedTag = activeTag,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun selectTab(tab: LibraryTabFilter) {
        selectedTab.value = tab
    }

    fun toggleTag(tag: String) {
        selectedTag.value = if (selectedTag.value == tag) null else tag
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun saveItem(
        existingId: Long?,
        title: String,
        type: LibraryItemType,
        language: CodeLanguage,
        content: String,
        tags: String,
        onResult: (RepoResult<Long>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(libraryRepository.saveItem(existingId, title, type, language, content, tags))
        }
    }

    fun deleteItem(id: Long, onResult: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(libraryRepository.deleteItem(id))
        }
    }
}
