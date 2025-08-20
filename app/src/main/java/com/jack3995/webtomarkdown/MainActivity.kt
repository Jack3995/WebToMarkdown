package com.jack3995.webtomarkdown

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.documentfile.provider.DocumentFile
import com.jack3995.webtomarkdown.screens.*
import com.jack3995.webtomarkdown.screens.FileNameOption
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import com.jack3995.webtomarkdown.util.WebContentProcessor
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api


class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    private var pendingSaveCallback: ((Uri?) -> Unit)? = null

    // ✅ используем общий помощник для работы с HTML
    private val processor = WebContentProcessor()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val folderPickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                pendingSaveCallback?.invoke(uri)
            }

        setContent {
            var currentScreen by rememberSaveable { mutableStateOf(Screen.Splash) }
            var savedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
            var lastCustomFolderUri by rememberSaveable { mutableStateOf<String?>(null) }

            val urlState = remember { mutableStateOf("") }
            var fileNameOption by rememberSaveable { mutableStateOf(FileNameOption.DEFAULT_NAME) }
            var saveLocationOption by rememberSaveable { mutableStateOf(SaveLocationOption.ASK_EVERY_TIME) }
            var notePreview by remember { mutableStateOf("") }
            var fileNameInput by remember { mutableStateOf("") }

            val context = this

            // Очистка полей
            fun clearFields() {
                urlState.value = ""
                fileNameInput = ""
                notePreview = ""
            }

            // Имя файла по умолчанию
            fun getDefaultFileName(): String {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH.mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                return "Заметка_$currentDate"
            }

            // Обработка URL
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

                        // ✅ теперь используем processor для работы
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

            // Сохранение через SAF
            fun saveNoteToSAF(folderUri: Uri, fileName: String, content: String) {
                val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                val safeFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
                val newFile = pickedDir?.createFile("text/markdown", safeFileName)
                if (newFile != null) {
                    context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    println("📁 Файл сохранён через SAF в: ${newFile.uri}")
                } else {
                    println("❗ Ошибка создания файла в выбранной директории")
                }
            }

            // Сохранение локально
            fun saveToFileCustomDir(dir: File, fileName: String, content: String) {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(content)
                println("📁 Файл сохранён в: ${file.absolutePath}")
            }

            // Главная функция сохранения
            fun saveNote() {
                val fileName = fileNameInput.ifBlank {
                    if (fileNameOption == FileNameOption.DEFAULT_NAME) getDefaultFileName()
                    else "page_${System.currentTimeMillis()}.md"
                }

                when (saveLocationOption) {
                    SaveLocationOption.ASK_EVERY_TIME -> {
                        pendingSaveCallback = { folderUri ->
                            if (folderUri != null) {
                                saveNoteToSAF(folderUri, fileName, notePreview)
                            } else println("Папка не выбрана, сохранения не будет.")
                        }
                        folderPickerLauncher.launch(null)
                    }
                    SaveLocationOption.DOWNLOADS -> {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        saveToFileCustomDir(downloadsDir, fileName, notePreview)
                    }
                    SaveLocationOption.CUSTOM_FOLDER -> {
                        if (!lastCustomFolderUri.isNullOrBlank()) {
                            val uri = Uri.parse(lastCustomFolderUri)
                            saveNoteToSAF(uri, fileName, notePreview)
                        } else {
                            pendingSaveCallback = { folderUri ->
                                if (folderUri != null) {
                                    lastCustomFolderUri = folderUri.toString()
                                    saveNoteToSAF(folderUri, fileName, notePreview)
                                } else println("Папка не выбрана, сохранения не будет.")
                            }
                            folderPickerLauncher.launch(null)
                        }
                    }
                }
            }

            // Splash → Main
            LaunchedEffect(Unit) {
                delay(2000L)
                currentScreen = Screen.Main
            }

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

    // Оставляем только сетевой метод тут
    private suspend fun downloadWebPage(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Ошибка HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Пустой ответ")
        }
    }
}
