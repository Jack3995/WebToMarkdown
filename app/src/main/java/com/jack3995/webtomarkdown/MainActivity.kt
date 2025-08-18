package com.jack3995.webtomarkdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.jack3995.webtomarkdown.screens.MainScreen
import com.jack3995.webtomarkdown.screens.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException

import com.jack3995.webtomarkdown.FileNameOption

class MainActivity : ComponentActivity() {

    enum class Screen {
        Main,
        Settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf(Screen.Main) }
            var savedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
            var askEveryTime by rememberSaveable { mutableStateOf(true) }
            val urlState = remember { mutableStateOf("") }
            var fileNameOption by rememberSaveable { mutableStateOf(FileNameOption.DEFAULT_NAME) }
            var notePreview by remember { mutableStateOf("") }
            var fileNameInput by remember { mutableStateOf("") }

            fun processUrl() {
                val url = urlState.value
                if (url.isEmpty()) {
                    notePreview = ""
                    fileNameInput = ""
                    println("Ошибка: Введите URL")
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val html = downloadWebPage(url)
                            val markdown = convertHtmlToMarkdown(html)
                            notePreview = markdown
                            fileNameInput = when (fileNameOption) {
                                FileNameOption.ASK_EVERY_TIME -> ""
                                FileNameOption.DEFAULT_NAME -> "page_${System.currentTimeMillis()}.md"
                                FileNameOption.PAGE_TITLE -> {
                                    val title = extractTitleFromHtml(html)
                                    if (title.isNullOrBlank()) "page_${System.currentTimeMillis()}.md" else sanitizeFileName(title) + ".md"
                                }
                            }
                        } catch (e: Exception) {
                            notePreview = "Ошибка загрузки страницы: ${e.message}"
                            fileNameInput = ""
                        }
                    }
                }
            }

            fun saveNote() {
                val fileName = fileNameInput.ifBlank {
                    "page_${System.currentTimeMillis()}.md"
                }
                saveToFile(fileName, notePreview)
            }

            when (currentScreen) {
                Screen.Main -> MainScreen(
                    urlState = urlState.value,
                    onUrlChange = { urlState.value = it },
                    onProcessClick = { processUrl() },
                    onSaveClick = { saveNote() },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    fileNameInput = fileNameInput,
                    onFileNameInputChange = { fileNameInput = it },
                    notePreview = notePreview
                )
                Screen.Settings -> SettingsScreen(
                    initialPath = savedFolderPath,
                    initialFileNameOption = fileNameOption,
                    onSave = { askEvery, path, option ->
                        askEveryTime = askEvery
                        savedFolderPath = path
                        fileNameOption = option
                        currentScreen = Screen.Main
                    }
                )
            }
        }
    }

    private fun saveToFile(fileName: String, content: String) {
        val dir = File(getExternalFilesDir(null), "WebToMarkdown")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        println("📁 Файл сохранён: ${file.absolutePath}")
    }

    private suspend fun downloadWebPage(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Ошибка HTTP ${response.code}")
        return response.body?.string() ?: throw IOException("Пустой ответ")
    }

    private fun convertHtmlToMarkdown(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, iframe, noscript").remove()
            val sb = StringBuilder()
            val body = doc.body()
            if (body != null) {
                for (element in body.children()) {
                    sb.append(elementToMarkdown(element))
                    sb.append("\n\n")
                }
            } else {
                sb.append("Нет содержимого для отображения")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "Ошибка преобразования: ${e.message}"
        }
    }

    private fun elementToMarkdown(element: org.jsoup.nodes.Element): String {
        return when (element.tagName().lowercase()) {
            "h1" -> "# ${element.text()}"
            "h2" -> "## ${element.text()}"
            "h3" -> "### ${element.text()}"
            "h4" -> "#### ${element.text()}"
            "h5" -> "##### ${element.text()}"
            "h6" -> "###### ${element.text()}"
            "p" -> element.text()
            "ul" -> element.children().joinToString("\n") { "- ${it.text()}" }
            "ol" -> element.children().mapIndexed { i, li -> "${i + 1}. ${li.text()}" }.joinToString("\n")
            "a" -> "[${element.text()}](${element.attr("href")})"
            "b", "strong" -> "**${element.text()}**"
            "i", "em" -> "*${element.text()}*"
            else -> if (element.children().isNotEmpty())
                element.children().joinToString("\n") { elementToMarkdown(it) }
            else
                element.text()
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    }

    private fun extractTitleFromHtml(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            doc.title()
        } catch (e: Exception) {
            null
        }
    }
}
