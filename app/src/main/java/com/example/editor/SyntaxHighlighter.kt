package com.example.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.model.CodeLanguage

data class CodeSyntaxPalette(
    val plain: Color,
    val keyword: Color,
    val typeOrTag: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotationOrKey: Color,
    val functionName: Color,
    val searchMatchBg: Color,
    val activeSearchMatchBg: Color
) {
    companion object {
        val Dark = CodeSyntaxPalette(
            plain = Color(0xFFE6EDF3),
            keyword = Color(0xFFC084FC),         // Vivid Cyber Violet
            typeOrTag = Color(0xFF38BDF8),       // Electric Sky/Cyan
            string = Color(0xFF4ADE80),          // Mint Emerald
            number = Color(0xFFFBBF24),          // Warm Amber
            comment = Color(0xFF6E7681),         // Muted Charcoal Slate
            annotationOrKey = Color(0xFF60A5FA), // Electric Blue
            functionName = Color(0xFF93C5FD),    // Soft Ice Blue
            searchMatchBg = Color(0x553B82F6),
            activeSearchMatchBg = Color(0xAA8B5CF6)
        )

        val Light = CodeSyntaxPalette(
            plain = Color(0xFF1E293B),
            keyword = Color(0xFF7C3AED),
            typeOrTag = Color(0xFF0284C7),
            string = Color(0xFF059669),
            number = Color(0xFFD97706),
            comment = Color(0xFF64748B),
            annotationOrKey = Color(0xFF2563EB),
            functionName = Color(0xFF1D4ED8),
            searchMatchBg = Color(0x443B82F6),
            activeSearchMatchBg = Color(0x777C3AED)
        )
    }
}

object SyntaxHighlighter {
    private val cStyleCommentRegex = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
    private val hashCommentRegex = Regex("#[^\\n]*")
    private val htmlCommentRegex = Regex("<!--[\\s\\S]*?-->")
    private val stringRegex = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`")
    private val jsonKeyRegex = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:)")
    private val numberRegex = Regex("\\b(?:0x[0-9a-fA-F_]+|0b[01_]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlLuU]*)\\b")
    private val annotationRegex = Regex("@[A-Za-z_][A-Za-z0-9_]*")
    private val functionCallRegex = Regex("\\b([a-z_][A-Za-z0-9_]*)\\s*(?=\\()")
    private val pascalTypeRegex = Regex("\\b[A-Z][A-Za-z0-9_]*\\b")
    private val markdownHeadingRegex = Regex("(?m)^#{1,6}\\s+.*$")
    private val markdownBulletRegex = Regex("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+(?:\\[[ xX]\\]\\s+)?")
    private val markdownBoldRegex = Regex("\\*\\*[^\\n*]+\\*\\*|__[^\\n_]+__")
    private val markdownInlineCodeRegex = Regex("`[^`\\n]+`")
    private val htmlTagRegex = Regex("</?[A-Za-z][A-Za-z0-9-]*|/?\\s*>")
    private val cssPropRegex = Regex("(?m)^\\s*[a-z-]+(?=\\s*:)")
    private val cssColorRegex = Regex("#[0-9a-fA-F]{3,8}\\b")

    private val keywordSets: Map<CodeLanguage, Set<String>> = mapOf(
        CodeLanguage.KOTLIN to setOf(
            "package", "import", "class", "interface", "object", "data", "sealed", "enum",
            "fun", "val", "var", "const", "lateinit", "private", "public", "internal", "protected",
            "override", "open", "abstract", "suspend", "inline", "return", "if", "else", "when",
            "for", "while", "do", "try", "catch", "finally", "throw", "true", "false", "null",
            "is", "as", "in", "by", "this", "super", "companion", "init"
        ),
        CodeLanguage.TYPESCRIPT to setOf(
            "import", "export", "from", "default", "interface", "type", "enum", "class",
            "implements", "extends", "function", "const", "let", "var", "return", "async", "await",
            "if", "else", "switch", "case", "for", "while", "try", "catch", "throw", "new",
            "true", "false", "null", "undefined", "void", "readonly", "public", "private", "as"
        ),
        CodeLanguage.JAVASCRIPT to setOf(
            "import", "export", "from", "default", "class", "extends", "function", "const", "let",
            "var", "return", "async", "await", "if", "else", "switch", "case", "for", "while",
            "try", "catch", "throw", "new", "true", "false", "null", "undefined", "typeof", "this"
        ),
        CodeLanguage.PYTHON to setOf(
            "from", "import", "as", "def", "class", "return", "yield", "if", "elif", "else",
            "for", "while", "break", "continue", "try", "except", "finally", "raise", "with",
            "lambda", "pass", "async", "await", "True", "False", "None", "and", "or", "not", "in", "is"
        ),
        CodeLanguage.RUST to setOf(
            "fn", "let", "mut", "const", "pub", "struct", "enum", "impl", "trait", "mod", "use",
            "crate", "self", "super", "match", "if", "else", "for", "while", "loop", "return",
            "async", "await", "move", "true", "false", "Some", "None", "Ok", "Err", "where", "type"
        ),
        CodeLanguage.JSON to setOf("true", "false", "null"),
        CodeLanguage.CSS to setOf("important", "from", "to", "var", "calc", "media", "root"),
        CodeLanguage.SHELL to setOf(
            "if", "then", "elif", "else", "fi", "for", "while", "until", "do", "done",
            "case", "esac", "in", "function", "local", "export", "unset", "return", "exit",
            "echo", "printf", "read", "cd", "pwd", "ls", "cat", "grep", "sed", "awk",
            "mkdir", "rm", "cp", "mv", "touch", "chmod", "wc", "head", "tail", "true", "false"
        )
    )

    private val shellVarRegex = Regex("\\$\\{?[A-Za-z_][A-Za-z0-9_]*\\}?|\\$[0-9@#?*!]")

    fun highlight(
        text: String,
        language: CodeLanguage,
        palette: CodeSyntaxPalette,
        searchMatches: List<IntRange> = emptyList(),
        activeMatchIndex: Int = -1
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val builder = AnnotatedString.Builder(text)
        builder.addStyle(SpanStyle(color = palette.plain), 0, text.length)

        // Track masked character ranges so comments/strings take precedence over inner tokens
        val masked = BooleanArray(text.length)

        fun applySpan(start: Int, end: Int, style: SpanStyle, mask: Boolean = false) {
            if (start < 0 || end <= start || end > text.length) return
            builder.addStyle(style, start, end)
            if (mask) {
                for (i in start until end) masked[i] = true
            }
        }

        fun isUnmasked(start: Int, end: Int): Boolean {
            if (start < 0 || end > text.length) return false
            for (i in start until end) {
                if (masked[i]) return false
            }
            return true
        }

        when (language) {
            CodeLanguage.MARKDOWN -> {
                markdownHeadingRegex.findAll(text).forEach {
                    applySpan(
                        it.range.first,
                        it.range.last + 1,
                        SpanStyle(color = palette.typeOrTag, fontWeight = FontWeight.Bold),
                        mask = true
                    )
                }
                markdownBulletRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold))
                    }
                }
                markdownInlineCodeRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                markdownBoldRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number, fontWeight = FontWeight.Bold))
                    }
                }
            }

            CodeLanguage.JSON -> {
                jsonKeyRegex.findAll(text).forEach {
                    applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.annotationOrKey), mask = true)
                }
                stringRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                numberRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number))
                    }
                }
                highlightKeywords(text, keywordSets[language].orEmpty(), palette.keyword, ::isUnmasked, ::applySpan)
            }

            CodeLanguage.HTML -> {
                htmlCommentRegex.findAll(text).forEach {
                    applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), mask = true)
                }
                stringRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                htmlTagRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.typeOrTag, fontWeight = FontWeight.Medium))
                    }
                }
            }

            CodeLanguage.CSS -> {
                cStyleCommentRegex.findAll(text).forEach {
                    applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), mask = true)
                }
                stringRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                cssColorRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number))
                    }
                }
                cssPropRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.annotationOrKey))
                    }
                }
                numberRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number))
                    }
                }
            }

            CodeLanguage.PLAIN_TEXT -> {
                // Keep plain text clean with search highlights applied below
            }

            CodeLanguage.SHELL -> {
                hashCommentRegex.findAll(text).forEach {
                    applySpan(
                        it.range.first,
                        it.range.last + 1,
                        SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                        mask = true
                    )
                }
                stringRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                shellVarRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.typeOrTag))
                    }
                }
                numberRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number))
                    }
                }
                highlightKeywords(text, keywordSets[CodeLanguage.SHELL].orEmpty(), palette.keyword, ::isUnmasked, ::applySpan)
            }

            else -> {
                // C-style or Python comments first
                val commentRegex = if (language == CodeLanguage.PYTHON) hashCommentRegex else cStyleCommentRegex
                commentRegex.findAll(text).forEach {
                    applySpan(
                        it.range.first,
                        it.range.last + 1,
                        SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                        mask = true
                    )
                }
                stringRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.string), mask = true)
                    }
                }
                annotationRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.annotationOrKey))
                    }
                }
                numberRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.number))
                    }
                }
                pascalTypeRegex.findAll(text).forEach {
                    if (isUnmasked(it.range.first, it.range.last + 1)) {
                        applySpan(it.range.first, it.range.last + 1, SpanStyle(color = palette.typeOrTag))
                    }
                }
                functionCallRegex.findAll(text).forEach { match ->
                    val grp = match.groups[1]
                    if (grp != null && isUnmasked(grp.range.first, grp.range.last + 1)) {
                        applySpan(grp.range.first, grp.range.last + 1, SpanStyle(color = palette.functionName))
                    }
                }
                highlightKeywords(text, keywordSets[language].orEmpty(), palette.keyword, ::isUnmasked, ::applySpan)
            }
        }

        // Overlay search matches cleanly without shifting offsets
        searchMatches.forEachIndexed { idx, range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end > start) {
                val bg = if (idx == activeMatchIndex) palette.activeSearchMatchBg else palette.searchMatchBg
                builder.addStyle(
                    SpanStyle(background = bg, fontWeight = if (idx == activeMatchIndex) FontWeight.Bold else null),
                    start,
                    end
                )
            }
        }

        return builder.toAnnotatedString()
    }

    private val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")

    private inline fun highlightKeywords(
        text: String,
        keywords: Set<String>,
        keywordColor: Color,
        isUnmasked: (Int, Int) -> Boolean,
        applySpan: (Int, Int, SpanStyle, Boolean) -> Unit
    ) {
        if (keywords.isEmpty()) return
        wordRegex.findAll(text).forEach { match ->
            if (match.value in keywords && isUnmasked(match.range.first, match.range.last + 1)) {
                applySpan(
                    match.range.first,
                    match.range.last + 1,
                    SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium),
                    false
                )
            }
        }
    }
}

class SyntaxVisualTransformation(
    private val language: CodeLanguage,
    private val palette: CodeSyntaxPalette,
    private val searchMatches: List<IntRange>,
    private val activeMatchIndex: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(
            text = text.text,
            language = language,
            palette = palette,
            searchMatches = searchMatches,
            activeMatchIndex = activeMatchIndex
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyntaxVisualTransformation) return false
        return language == other.language &&
            palette == other.palette &&
            searchMatches == other.searchMatches &&
            activeMatchIndex == other.activeMatchIndex
    }

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + palette.hashCode()
        result = 31 * result + searchMatches.hashCode()
        result = 31 * result + activeMatchIndex
        return result
    }
}
