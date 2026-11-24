package com.example.editor

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

object ValidationUtils {
    private val forbiddenChars = setOf('/', '\\', '\u0000', '\n', '\r', '\t')

    fun validateProjectName(rawName: String, existingNames: Collection<String>): ValidationResult {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Project name cannot be empty.")
        }
        if (trimmed.length > 48) {
            return ValidationResult.Invalid("Project name must be 48 characters or fewer.")
        }
        if (trimmed.any { it in forbiddenChars }) {
            return ValidationResult.Invalid("Project name cannot contain slashes or line breaks.")
        }
        if (trimmed == "." || trimmed == "..") {
            return ValidationResult.Invalid("Choose a descriptive project name.")
        }
        if (existingNames.any { it.equals(trimmed, ignoreCase = true) }) {
            return ValidationResult.Invalid("A project named \"$trimmed\" already exists.")
        }
        return ValidationResult.Valid
    }

    fun validateNodeName(
        rawName: String,
        isFolder: Boolean,
        siblingNames: Collection<String>
    ): ValidationResult {
        val trimmed = rawName.trim()
        val label = if (isFolder) "Folder" else "File"
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("$label name cannot be empty.")
        }
        if (trimmed.length > 64) {
            return ValidationResult.Invalid("$label name must be 64 characters or fewer.")
        }
        if (trimmed.any { it in forbiddenChars || it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' }) {
            return ValidationResult.Invalid("$label name contains invalid path characters.")
        }
        if (trimmed == "." || trimmed == "..") {
            return ValidationResult.Invalid("Name cannot be \".\" or \"..\".")
        }
        if (siblingNames.any { it.equals(trimmed, ignoreCase = true) }) {
            return ValidationResult.Invalid("An item named \"$trimmed\" already exists in this folder.")
        }
        return ValidationResult.Valid
    }

    fun validateLibraryTitle(rawTitle: String): ValidationResult {
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Title cannot be empty.")
        }
        if (trimmed.length > 80) {
            return ValidationResult.Invalid("Title must be 80 characters or fewer.")
        }
        return ValidationResult.Valid
    }

    fun parseTags(rawTags: String): List<String> {
        return rawTags
            .split(',', ' ')
            .map { it.trim().trimStart('#').lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
    }

    fun findMatchRanges(
        text: String,
        query: String,
        ignoreCase: Boolean = true
    ): List<IntRange> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val results = mutableListOf<IntRange>()
        var startIndex = 0
        while (startIndex <= text.length - query.length) {
            val found = text.indexOf(query, startIndex = startIndex, ignoreCase = ignoreCase)
            if (found < 0) break
            results.add(found until (found + query.length))
            startIndex = found + maxOf(1, query.length)
        }
        return results
    }
}
