package com.example.data.repository

import com.example.data.local.LibraryDao
import com.example.data.local.LibraryItemEntity
import com.example.editor.ValidationResult
import com.example.editor.ValidationUtils
import com.example.model.CodeLanguage
import com.example.model.LibraryItemType
import kotlinx.coroutines.flow.Flow

class LibraryRepository(private val libraryDao: LibraryDao) {
    val allItems: Flow<List<LibraryItemEntity>> = libraryDao.observeAllItems()

    suspend fun saveItem(
        existingId: Long?,
        rawTitle: String,
        type: LibraryItemType,
        language: CodeLanguage,
        content: String,
        rawTags: String
    ): RepoResult<Long> {
        return try {
            when (val check = ValidationUtils.validateLibraryTitle(rawTitle)) {
                is ValidationResult.Invalid -> return RepoResult.Error(check.message)
                ValidationResult.Valid -> Unit
            }
            if (content.isBlank()) {
                val label = if (type == LibraryItemType.SNIPPET) "Snippet code" else "Note body"
                return RepoResult.Error("$label cannot be empty.")
            }
            val normalizedTags = ValidationUtils.parseTags(rawTags).joinToString(", ")
            val now = System.currentTimeMillis()
            val entity = LibraryItemEntity(
                id = existingId ?: 0L,
                title = rawTitle.trim(),
                type = type.name,
                language = if (type == LibraryItemType.SNIPPET) language.name else CodeLanguage.MARKDOWN.name,
                content = content,
                tags = normalizedTags,
                updatedAt = now
            )
            val savedId = if (existingId == null || existingId == 0L) {
                libraryDao.insertItem(entity)
            } else {
                libraryDao.updateItem(entity)
                existingId
            }
            RepoResult.Success(savedId)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to save item.")
        }
    }

    suspend fun deleteItem(id: Long): RepoResult<Unit> {
        return try {
            libraryDao.deleteItemById(id)
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Error(e.message ?: "Failed to delete item.")
        }
    }
}
