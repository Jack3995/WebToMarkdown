// MainActivity.kt
package com.jack3995.webtomarkdown

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.jack3995.webtomarkdown.screens.*
import com.jack3995.webtomarkdown.screens.FileNameOption
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import com.jack3995.webtomarkdown.util.WebContentProcessor
import com.jack3995.webtomarkdown.util.FileSaveHandler
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    private val processor = WebContentProcessor()
    private lateinit var fileSaveHandler: FileSaveHandler

    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileSaveHandler = FileSaveHandler(this, contentResolver)

        setContent {
            var currentScreen by rememberSaveable { mutableStateOf(Screen.Splash) }
            var savedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
            var lastCustomFolderUri by rememberSaveable { mutableStateOf<String?>(null) }

            val urlState = remember { mutableStateOf("") }
            var fileNameOption by rememberSaveable { mutableStateOf(FileNameOption.DEFAULT_NAME) }
            var saveLocationOption by rememberSaveable { mutableStateOf(SaveLocationOption.ASK_EVERY_TIME) }
            var notePreview by remember { mutableStateOf("") }
            var fileNameInput by remember { mutableStateOf("") }

            // Инициализация launcher для выбора папки с системой SAF
            folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                // Передаём выбранный URI в FileSaveHandler для сохранения файла
                fileSaveHandler.onFolderPicked(uri, fileNameInput, notePreview) { success ->
                    if (!success) println("❗ Ошибка сохранения файла через SAF")
                    // При необходимости здесь можно уведомить пользователя
                }
            }

            /**
             * Очистка полей ввода и предпросмотра на экране
             */
            fun clearFields() {
                urlState.value = ""
                fileNameInput = ""
                notePreview = ""
            }

            /**
             * Генерация имени файла по умолчанию с датой и временем
             * @return строка с именем файла, например, "Заметка_20.08.2025_22.35"
             */
            fun getDefaultFileName(): String {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH.mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                return "Заметка_$currentDate"
            }

            /**
             * Обработка введенного URL:
             * Загружает HTML страницы, конвертирует в markdown,
             * формирует имя файла в зависимости от выбранной опции.
             * Все операции выполняются в фоне.
             */
            fun processUrl() {
                val url = urlState.value.trim()
                if (url.isEmpty()) {
                    notePreview = ""
                    fileNameInput = ""
                    println("Ошибка: Введите URL")
                    return
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val html = downloadWebPage(url)
                        val markdown = processor.convertHtmlToMarkdown(html)
                        val newFileName = when (fileNameOption) {
                            FileNameOption.ASK_EVERY_TIME -> ""
                            FileNameOption.DEFAULT_NAME -> getDefaultFileName()
                            FileNameOption.PAGE_TITLE -> {
                                val title = processor.extractTitle(html)
                                if (title.isNullOrBlank()) getDefaultFileName()
                                else processor.sanitizeFilename(title)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            notePreview = markdown
                            fileNameInput = newFileName
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            notePreview = "Ошибка загрузки страницы: ${e.message}"
                            fileNameInput = ""
                        }
                    }
                }
            }

            /**
             * Логика сохранения заметки
             * Определяет имя файла (если пустое, генерирует по умолчанию)
             * Делегирует сохранение FileSaveHandler с передачей текущих настроек
             * Виджет выбора папки будет вызван автоматически при необходимости
             */
            fun saveNote() {
                val fileName = fileNameInput.ifBlank {
                    if (fileNameOption == FileNameOption.DEFAULT_NAME) getDefaultFileName()
                    else "page_${System.currentTimeMillis()}.md"
                }

                // Передача текущего URI кастомной папки в FileSaveHandler
                fileSaveHandler.lastCustomFolderUri = lastCustomFolderUri

                fileSaveHandler.saveNote(
                    fileName,
                    notePreview,
                    saveLocationOption,
                    onFolderPickerRequest = {
                        // Запускаем стандартный диалог выбора папки
                        folderPickerLauncher.launch(null)
                    },
                    onSaveResult = { success ->
                        if (!success) println("❗ Ошибка сохранения заметки")
                        // При необходимости можно уведомить пользователя об успехе/неудаче
                    }
                )
            }

            /**
             * Задержка на Splash экране, после которой переходим к основному
             */
            LaunchedEffect(Unit) {
                delay(2000L)
                currentScreen = Screen.Main
            }

            // Основные экраны приложения - переход в зависимости от текущего состояния
            when (currentScreen) {
                Screen.Splash -> SplashScreen()
                Screen.Main -> MainScreen(
                    urlState = urlState.value,
                    onUrlChange = { urlState.value = it },
                    onProcessClick = { processUrl() },
                    onSaveClick = { saveNote() },
                    onClearClick = { clearFields() },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    fileNameInput = fileNameInput,
                    onFileNameInputChange = { fileNameInput = it },
                    notePreview = notePreview
                )
                Screen.Settings -> SettingsScreen(
                    initialPath = savedFolderPath,
                    initialFileNameOption = fileNameOption,
                    initialSaveLocationOption = saveLocationOption,
                    onSave = { _, path, option, locationOption ->
                        savedFolderPath = path
                        fileNameOption = option
                        saveLocationOption = locationOption
                        currentScreen = Screen.Main
                        if (locationOption == SaveLocationOption.CUSTOM_FOLDER && !path.isNullOrBlank()) {
                            lastCustomFolderUri = path
                        }
                    }
                )
            }
        }
    }

    /**
     * Скачивает HTML содержимое по указанному URL с помощью OkHttp.
     * @param url адрес веб-страницы
     * @return HTML-код страницы как строка
     * @throws IOException при ошибках сети или пустом ответе
     */
    private suspend fun downloadWebPage(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Ошибка HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Пустой ответ")
        }
    }
}
