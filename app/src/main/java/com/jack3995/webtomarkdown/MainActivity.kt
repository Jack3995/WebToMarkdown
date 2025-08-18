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

class MainActivity : ComponentActivity() {

    // Перечисление экранов приложения для навигации
    enum class Screen {
        Main,
        Settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Хранение текущего экрана
            var currentScreen by remember { mutableStateOf(Screen.Main) }
            // Путь к выбранной папке для сохранения, nullable
            var savedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
            // Флаг "спрашивать ли каждый раз"
            var askEveryTime by rememberSaveable { mutableStateOf(true) }
            // Текущее значение URL, введённого пользователем
            val urlState = remember { mutableStateOf("") }

            // Навигация между экранами
            when (currentScreen) {
                Screen.Main -> MainScreen(
                    urlState = urlState.value,
                    onUrlChange = { urlState.value = it },
                    onSaveClick = {
                        if (urlState.value.isEmpty()) {
                            // Отладочная печать ошибки пустого URL
                            println("Ошибка: Введите URL")
                        } else {
                            // Начать скачивание и сохранение markdown
                            startDownloadAndSave(urlState.value)
                        }
                    },
                    // Переход к экрану настроек
                    onOpenSettings = { currentScreen = Screen.Settings }
                )
                Screen.Settings -> SettingsScreen(
                    initialPath = savedFolderPath,
                    // Сохранение настроек и возврат к главному экрану
                    onSave = { askEvery, path ->
                        askEveryTime = askEvery
                        savedFolderPath = path
                        currentScreen = Screen.Main
                    }
                )
            }
        }
    }

    // Функция для скачивания страницы и сохранения как markdown - запускается в фоновом потоке
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

    // Сохраняет строку content в файл с именем fileName во внутреннюю папку приложения
    private fun saveToFile(fileName: String, content: String) {
        val dir = File(getExternalFilesDir(null), "WebToMarkdown")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        file.writeText(content)
        println("📁 Файл сохранён: ${file.absolutePath}")
    }

    // Скачивает HTML-страницу по URL (в корутине)
    private suspend fun downloadWebPage(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) throw IOException("Ошибка HTTP ${response.code}")

        return response.body?.string() ?: throw IOException("Пустой ответ")
    }

    // Конвертирует HTML в markdown используя Jsoup
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

    // Рекурсивная конвертация элементов HTML в markdown
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
