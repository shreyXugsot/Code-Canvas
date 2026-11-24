package com.example.editor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.data.local.NodeEntity
import com.example.model.CodeLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.concurrent.atomic.AtomicReference

enum class TerminalLineKind {
    COMMAND,
    STDOUT,
    STDERR,
    INFO
}

data class MaterializedWorkspace(
    val rootDir: File,
    val activeFile: File?,
    val activeRelativePath: String
)

data class JsEvalResult(
    val exitCode: Int,
    val lines: List<Pair<String, TerminalLineKind>>
)

class ShellExecutionEngine(private val context: Context) {

    private val activeProcess = AtomicReference<Process?>(null)

    fun materializeWorkspace(
        projectId: Long,
        nodes: List<NodeEntity>,
        activeFileId: Long
    ): MaterializedWorkspace {
        val workspacesBase = File(context.filesDir, "workspaces")
        val rootDir = File(workspacesBase, "project_$projectId")
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
        rootDir.mkdirs()

        val nodeById = nodes.associateBy { it.id }

        fun buildRelativePath(node: NodeEntity): String {
            val parts = ArrayDeque<String>()
            var current: NodeEntity? = node
            var guard = 0
            while (current != null && guard < 32) {
                parts.addFirst(current.name)
                current = current.parentId?.let { nodeById[it] }
                guard++
            }
            return parts.joinToString("/")
        }

        // Create folders first, then files
        nodes.filter { it.isFolder }.forEach { folder ->
            val rel = buildRelativePath(folder)
            File(rootDir, rel).mkdirs()
        }

        var activeFile: File? = null
        var activeRelativePath = ""

        nodes.filter { !it.isFolder }.forEach { fileNode ->
            val rel = buildRelativePath(fileNode)
            val target = File(rootDir, rel)
            target.parentFile?.mkdirs()
            target.writeText(fileNode.content, Charsets.UTF_8)
            if (fileNode.id == activeFileId) {
                activeFile = target
                activeRelativePath = rel
            }
        }

        return MaterializedWorkspace(
            rootDir = rootDir,
            activeFile = activeFile,
            activeRelativePath = activeRelativePath
        )
    }

    suspend fun runShellCommand(
        command: String,
        workingDir: File,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onLine: (String, TerminalLineKind) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (!workingDir.exists()) workingDir.mkdirs()
        val shellBin = resolveShellBinary()
        val process = ProcessBuilder(shellBin, "-c", command)
            .directory(workingDir)
            .apply {
                environment()["HOME"] = workingDir.absolutePath
                environment()["PWD"] = workingDir.absolutePath
            }
            .start()

        activeProcess.set(process)
        try {
            val exitCode = withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    val stdoutJob = async {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                onLine(line, TerminalLineKind.STDOUT)
                            }
                        }
                    }
                    val stderrJob = async {
                        process.errorStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                onLine(line, TerminalLineKind.STDERR)
                            }
                        }
                    }
                    stdoutJob.await()
                    stderrJob.await()
                    process.waitFor()
                }
            }
            if (exitCode == null) {
                process.destroyForcibly()
                onLine("Process timed out after ${timeoutMs / 1000}s and was stopped.", TerminalLineKind.STDERR)
                124
            } else {
                exitCode
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            onLine(e.message ?: "Shell execution interrupted.", TerminalLineKind.STDERR)
            1
        } finally {
            activeProcess.compareAndSet(process, null)
        }
    }

    suspend fun runShellScript(
        scriptFile: File,
        workingDir: File,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onLine: (String, TerminalLineKind) -> Unit
    ): Int {
        val escapedPath = "'" + scriptFile.absolutePath.replace("'", "'\\''") + "'"
        val shellBin = resolveShellBinary()
        return runShellCommand("$shellBin $escapedPath", workingDir, timeoutMs, onLine)
    }

    private fun resolveShellBinary(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() } ?: "sh"
    }

    fun stopCurrentProcess() {
        activeProcess.getAndSet(null)?.destroyForcibly()
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun evaluateJavaScript(code: String): JsEvalResult = withContext(Dispatchers.Main) {
        val logs = mutableListOf<Pair<String, TerminalLineKind>>()
        val completion = CompletableDeferred<String?>()
        var webView: WebView? = null
        try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null) {
                            val kind = when (consoleMessage.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> TerminalLineKind.STDERR
                                ConsoleMessage.MessageLevel.WARNING -> TerminalLineKind.INFO
                                else -> TerminalLineKind.STDOUT
                            }
                            logs.add(consoleMessage.message() to kind)
                        }
                        return true
                    }
                }
            }

            val wrappedScript = """
                (function() {
                    try {
                        var __res = (0, eval)(${JSONObject.quote(code)});
                        if (__res === undefined) return JSON.stringify({ ok: true, hasValue: false });
                        var __str = typeof __res === 'object' ? JSON.stringify(__res, null, 2) : String(__res);
                        return JSON.stringify({ ok: true, hasValue: true, value: __str });
                    } catch (err) {
                        return JSON.stringify({ ok: false, error: err && err.message ? err.message : String(err) });
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(wrappedScript) { raw ->
                completion.complete(raw)
            }

            val rawJsonString = withTimeoutOrNull(5_000L) { completion.await() }
            if (rawJsonString == null) {
                logs.add("JavaScript execution timed out after 5s." to TerminalLineKind.STDERR)
                return@withContext JsEvalResult(exitCode = 1, lines = logs)
            }

            // evaluateJavascript returns a JSON-encoded string literal when JS returns a string
            val decoded = try {
                JSONTokener(rawJsonString).nextValue() as? String ?: rawJsonString
            } catch (_: Exception) {
                rawJsonString
            }

            val resultObj = try {
                JSONObject(decoded)
            } catch (_: Exception) {
                if (decoded.isNotBlank() && decoded != "null") {
                    logs.add(decoded to TerminalLineKind.STDOUT)
                }
                return@withContext JsEvalResult(exitCode = 0, lines = logs)
            }

            val ok = resultObj.optBoolean("ok", true)
            if (ok) {
                if (resultObj.optBoolean("hasValue", false)) {
                    val value = resultObj.optString("value", "")
                    if (value.isNotEmpty()) {
                        value.lineSequence().forEach { logs.add("← $it" to TerminalLineKind.INFO) }
                    }
                }
                JsEvalResult(exitCode = 0, lines = logs)
            } else {
                val err = resultObj.optString("error", "JavaScript error")
                logs.add(err to TerminalLineKind.STDERR)
                JsEvalResult(exitCode = 1, lines = logs)
            }
        } catch (e: Throwable) {
            logs.add("JS sandbox unavailable: ${e.message ?: "WebView error"}" to TerminalLineKind.STDERR)
            JsEvalResult(exitCode = 1, lines = logs)
        } finally {
            try {
                webView?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun validateJson(content: String): JsEvalResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return JsEvalResult(
                exitCode = 1,
                lines = listOf("Empty JSON document." to TerminalLineKind.STDERR)
            )
        }
        return try {
            val parsed = JSONTokener(trimmed).nextValue()
            val lines = mutableListOf<Pair<String, TerminalLineKind>>()
            when (parsed) {
                is JSONObject -> {
                    lines.add("Valid JSON object (${parsed.length()} top-level keys)" to TerminalLineKind.INFO)
                    parsed.toString(2).lineSequence().take(40).forEach {
                        lines.add(it to TerminalLineKind.STDOUT)
                    }
                }
                is JSONArray -> {
                    lines.add("Valid JSON array (${parsed.length()} items)" to TerminalLineKind.INFO)
                    parsed.toString(2).lineSequence().take(40).forEach {
                        lines.add(it to TerminalLineKind.STDOUT)
                    }
                }
                else -> {
                    lines.add("Valid JSON primitive: $parsed" to TerminalLineKind.STDOUT)
                }
            }
            JsEvalResult(exitCode = 0, lines = lines)
        } catch (e: JSONException) {
            JsEvalResult(
                exitCode = 1,
                lines = listOf((e.message ?: "Invalid JSON syntax") to TerminalLineKind.STDERR)
            )
        }
    }

    fun buildUnsupportedLanguageGuidance(
        language: CodeLanguage,
        relativePath: String
    ): List<Pair<String, TerminalLineKind>> {
        val desktopCmd = when (language) {
            CodeLanguage.KOTLIN -> "kotlinc $relativePath -include-runtime -d app.jar && java -jar app.jar"
            CodeLanguage.PYTHON -> "python3 $relativePath"
            CodeLanguage.TYPESCRIPT -> "npx tsx $relativePath"
            CodeLanguage.RUST -> "rustc $relativePath && ./${relativePath.substringBeforeLast('.').substringAfterLast('/')}"
            CodeLanguage.HTML -> "Open $relativePath in a browser or serve with: python3 -m http.server"
            CodeLanguage.CSS -> "Link $relativePath in an HTML document to preview styles"
            CodeLanguage.MARKDOWN -> "Preview $relativePath in a Markdown viewer"
            CodeLanguage.PLAIN_TEXT -> "cat $relativePath"
            else -> relativePath
        }
        return listOf(
            "${language.displayName} compiler/runtime is not bundled in Android's offline sandbox." to TerminalLineKind.INFO,
            "Desktop command: $desktopCmd" to TerminalLineKind.INFO,
            "Tip: Use the shell prompt below (e.g. `ls`, `cat $relativePath`, `wc -l $relativePath`) or run a .sh / .js / .json file." to TerminalLineKind.STDOUT
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
