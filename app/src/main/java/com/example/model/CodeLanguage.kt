package com.example.model

import androidx.compose.ui.graphics.Color

enum class CodeLanguage(
    val displayName: String,
    val shortLabel: String,
    val markerColor: Color,
    val defaultExtension: String,
    val extensions: Set<String>
) {
    KOTLIN(
        displayName = "Kotlin",
        shortLabel = "KT",
        markerColor = Color(0xFF8B5CF6),
        defaultExtension = "kt",
        extensions = setOf("kt", "kts")
    ),
    TYPESCRIPT(
        displayName = "TypeScript",
        shortLabel = "TS",
        markerColor = Color(0xFF3B82F6),
        defaultExtension = "ts",
        extensions = setOf("ts", "tsx")
    ),
    JAVASCRIPT(
        displayName = "JavaScript",
        shortLabel = "JS",
        markerColor = Color(0xFFF59E0B),
        defaultExtension = "js",
        extensions = setOf("js", "jsx", "mjs")
    ),
    PYTHON(
        displayName = "Python",
        shortLabel = "PY",
        markerColor = Color(0xFF10B981),
        defaultExtension = "py",
        extensions = setOf("py")
    ),
    SHELL(
        displayName = "Shell",
        shortLabel = "SH",
        markerColor = Color(0xFF22C55E),
        defaultExtension = "sh",
        extensions = setOf("sh", "bash", "zsh")
    ),
    JSON(
        displayName = "JSON",
        shortLabel = "JSON",
        markerColor = Color(0xFF06B6D4),
        defaultExtension = "json",
        extensions = setOf("json")
    ),
    MARKDOWN(
        displayName = "Markdown",
        shortLabel = "MD",
        markerColor = Color(0xFFEC4899),
        defaultExtension = "md",
        extensions = setOf("md", "markdown")
    ),
    HTML(
        displayName = "HTML",
        shortLabel = "HTML",
        markerColor = Color(0xFFF97316),
        defaultExtension = "html",
        extensions = setOf("html", "htm", "xml")
    ),
    CSS(
        displayName = "CSS",
        shortLabel = "CSS",
        markerColor = Color(0xFF38BDF8),
        defaultExtension = "css",
        extensions = setOf("css", "scss")
    ),
    RUST(
        displayName = "Rust",
        shortLabel = "RS",
        markerColor = Color(0xFFFB7185),
        defaultExtension = "rs",
        extensions = setOf("rs")
    ),
    PLAIN_TEXT(
        displayName = "Plain Text",
        shortLabel = "TXT",
        markerColor = Color(0xFF94A3B8),
        defaultExtension = "txt",
        extensions = setOf("txt", "log", "env", "gitignore", "properties")
    );

    fun starterTemplate(projectName: String): String = when (this) {
        KOTLIN -> """
            package dev.codecanvas.${projectName.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "app" }}

            data class CanvasItem(
                val id: Int,
                val title: String,
                val active: Boolean = true
            )

            fun main() {
                val items = listOf(
                    CanvasItem(1, "Design tokens"),
                    CanvasItem(2, "Offline sync")
                )
                items.filter { it.active }.forEach { println(it.title) }
            }
        """.trimIndent()

        TYPESCRIPT -> """
            export interface TaskCard {
              id: string;
              title: string;
              completed: boolean;
            }

            export function summarizeTasks(tasks: TaskCard[]): string {
              const done = tasks.filter((t) => t.completed).length;
              return `${'$'}{done}/${'$'}{tasks.length} tasks complete`;
            }
        """.trimIndent()

        JAVASCRIPT -> """
            const formatMetric = (label, value) => {
              return `${'$'}{label}: ${'$'}{Number(value).toFixed(1)}`;
            };

            console.log(formatMetric("Latency", 12.48));
        """.trimIndent()

        PYTHON -> """
            from dataclasses import dataclass

            @dataclass
            class NoteEntry:
                title: str
                tags: list[str]

            def filter_by_tag(entries: list[NoteEntry], tag: str) -> list[NoteEntry]:
                return [entry for entry in entries if tag in entry.tags]

            if __name__ == "__main__":
                print("Ready for $projectName")
        """.trimIndent()

        SHELL -> """
            #!/bin/sh
            # Quick workspace check for $projectName
            echo "Running $projectName in $(pwd)"
            ls -la
        """.trimIndent()

        JSON -> """
            {
              "name": "$projectName",
              "version": "1.0.0",
              "private": true,
              "settings": {
                "tabSize": 2,
                "formatOnSave": true
              }
            }
        """.trimIndent()

        MARKDOWN -> """
            # $projectName

            Quick scratchpad and architecture notes while away from the desk.

            - [ ] Outline core module boundaries
            - [ ] Review edge cases on mobile layout
        """.trimIndent()

        HTML -> """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <title>$projectName</title>
            </head>
            <body>
              <main class="canvas-shell">
                <h1>$projectName</h1>
              </main>
            </body>
            </html>
        """.trimIndent()

        CSS -> """
            :root {
              --bg-canvas: #0b0e14;
              --accent-blue: #3b82f6;
            }

            .canvas-shell {
              background: var(--bg-canvas);
              border: 1px solid rgba(255, 255, 255, 0.08);
              border-radius: 12px;
            }
        """.trimIndent()

        RUST -> """
            #[derive(Debug)]
            struct Project {
                name: String,
                stars: u32,
            }

            fn main() {
                let p = Project {
                    name: String::from("$projectName"),
                    stars: 42,
                };
                println!("{:?}", p);
            }
        """.trimIndent()

        PLAIN_TEXT -> """
            $projectName
            Created in CodeCanvas
        """.trimIndent()
    }

    companion object {
        fun fromNameOrDefault(name: String): CodeLanguage {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) }
                ?: PLAIN_TEXT
        }

        fun inferFromFileName(fileName: String, fallback: CodeLanguage = PLAIN_TEXT): CodeLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return fallback
            return entries.firstOrNull { ext in it.extensions } ?: fallback
        }
    }
}
