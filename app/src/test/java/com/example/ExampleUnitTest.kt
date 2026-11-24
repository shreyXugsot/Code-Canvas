package com.example

import com.example.editor.CodeSyntaxPalette
import com.example.editor.SyntaxHighlighter
import com.example.editor.ValidationResult
import com.example.editor.ValidationUtils
import com.example.model.CodeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun validateProjectName_rejectsInvalidAndDuplicates() {
        assertTrue(ValidationUtils.validateProjectName("   ", emptyList()) is ValidationResult.Invalid)
        assertTrue(ValidationUtils.validateProjectName("bad/name", emptyList()) is ValidationResult.Invalid)
        assertTrue(ValidationUtils.validateProjectName("Demo", listOf("demo")) is ValidationResult.Invalid)
        assertTrue(ValidationUtils.validateProjectName("CodeCanvas", listOf("Other")) is ValidationResult.Valid)
    }

    @Test
    fun findMatchRanges_findsExpectedOccurrences() {
        val matches = ValidationUtils.findMatchRanges("val foo = foo + Foo", "foo", ignoreCase = true)
        assertEquals(3, matches.size)
        val caseSensitive = ValidationUtils.findMatchRanges("val foo = foo + Foo", "foo", ignoreCase = false)
        assertEquals(2, caseSensitive.size)
    }

    @Test
    fun syntaxHighlighter_preservesTextLengthExactly() {
        val sample = "fun main() { val x = 42 // comment\n val s = \"hi\" }"
        val annotated = SyntaxHighlighter.highlight(
            text = sample,
            language = CodeLanguage.KOTLIN,
            palette = CodeSyntaxPalette.Dark,
            searchMatches = listOf(4..7),
            activeMatchIndex = 0
        )
        assertEquals(sample, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun inferLanguageFromFilename_mapsExtensions() {
        assertEquals(CodeLanguage.KOTLIN, CodeLanguage.inferFromFileName("Editor.kt"))
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.inferFromFileName("script.py"))
        assertEquals(CodeLanguage.TYPESCRIPT, CodeLanguage.inferFromFileName("App.tsx"))
        assertEquals(CodeLanguage.JSON, CodeLanguage.inferFromFileName("config.json"))
        assertEquals(CodeLanguage.SHELL, CodeLanguage.inferFromFileName("deploy.sh"))
        assertEquals(CodeLanguage.SHELL, CodeLanguage.inferFromFileName("env.bash"))
    }

    @Test
    fun syntaxHighlighter_highlightsShellScriptsWithoutChangingText() {
        val sample = "#!/system/bin/sh\nNAME=\"CodeCanvas\"\nif [ -n \"\$NAME\" ]; then\n  echo \"Hello \$NAME\"\nfi"
        val annotated = SyntaxHighlighter.highlight(
            text = sample,
            language = CodeLanguage.SHELL,
            palette = CodeSyntaxPalette.Dark
        )
        assertEquals(sample, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
