package com.jack3995.webtomarkdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jack3995.webtomarkdown.screens.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    enum class Screen {
        Main, Settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf(Screen.Main) }
            var savedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
            var askEveryTime by rememberSaveable { mutableStateOf(true) }
            val urlState = remember { mutableStateOf("") }

            when (currentScreen) {
                Screen.Main -> MainScreen(
                    urlState = urlState.value,
                    onUrlChange = { urlState.value = it },
                    onSaveClick = {
                        if (urlState.value.isEmpty()) {
                            println("Ошибка: Введите URL")
                        } else {
                            startDownloadAndSave(urlState.value)
                        }
                    },
                    onOpenSettings = { currentScreen = Screen.Settings }
                )
                Screen.Settings -> SettingsScreen(
                    initialPath = savedFolderPath,
                    onSave = { askEvery, path ->
                        askEveryTime = askEvery
                        savedFolderPath = path
                        currentScreen = Screen.Main
                    }
                )
            }
        }
    }

    private fun startDownloadAndSave(url: String) {
        println("**DEBUG**: startDownloadAndSave запущена")
        CoroutineScope(Dispatchers.IO).launch {
            println("**DEBUG**: Корутин стартует")
            try {
                println("Будем сохранять: [$url]($url)")
                val html = downloadWebPage(url)
                val markdown = convertHtmlToMarkdown(html)
                println("**DEBUG**: Markdown сформирован:\n$markdown")
                saveToFile("page_${System.currentTimeMillis()}.md", markdown)
                println("✅ Успешно сохранено!")
            } catch (e: Exception) {
                println("**ERROR**: Исключение: ${e::class.java.name} ${e.message}")
                e.printStackTrace()
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
}

@Composable
fun MainScreen(
    urlState: String,
    onUrlChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = urlState,
            onValueChange = onUrlChange,
            label = { Text("Введите URL сайта") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSaveClick) {
            Text("Сохранить в Markdown")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenSettings) {
            Text("Настройки")
        }
    }
}
