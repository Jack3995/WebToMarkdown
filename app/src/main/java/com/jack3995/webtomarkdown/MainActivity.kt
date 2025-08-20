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
import com.jack3995.webtomarkdown.util.WebDownloader
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    private val processor = WebContentProcessor()
    private val webDownloader = WebDownloader()
    private lateinit var fileSaveHandler: FileSaveHandler

    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private var fileNameInput by mutableStateOf("")
    private var notePreview by mutableStateOf("")
    private var urlState = mutableStateOf("")

    private var fileNameOption by mutableStateOf(FileNameOption.DEFAULT_NAME)
    private var saveLocationOption by mutableStateOf(SaveLocationOption.ASK_EVERY_TIME)
    private var lastCustomFolderUri by mutableStateOf<String?>(null)

    private var currentScreen by mutableStateOf(Screen.Splash)
    private var savedFolderPath by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileSaveHandler = FileSaveHandler(this, contentResolver)

        // Регистрируем launcher в onCreate
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            fileSaveHandler.onFolderPicked(uri, fileNameInput, notePreview) { success ->
                if (!success) println("❗ Ошибка сохранения файла через SAF")
            }
        }

        setContent {
            var _currentScreen by rememberSaveable { mutableStateOf(currentScreen) }
            var _savedFolderPath by rememberSaveable { mutableStateOf(savedFolderPath) }
            var _lastCustomFolderUri by rememberSaveable { mutableStateOf(lastCustomFolderUri) }

            val _urlState = remember { mutableStateOf(urlState.value) }
            var _fileNameOption by rememberSaveable { mutableStateOf(fileNameOption) }
            var _saveLocationOption by rememberSaveable { mutableStateOf(saveLocationOption) }
            var _notePreview by remember { mutableStateOf(notePreview) }
            var _fileNameInput by remember { mutableStateOf(fileNameInput) }

            fun clearFields() {
                _urlState.value = ""
                _fileNameInput = ""
                _notePreview = ""
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
                val url = _urlState.value.trim()
                if (url.isEmpty()) {
                    _notePreview = ""
                    _fileNameInput = ""
                    println("Ошибка: Введите URL")
                    return
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val html = webDownloader.downloadWebPage(url)
                        val markdown = processor.convertHtmlToMarkdown(html)
                        val newFileName = when (_fileNameOption) {
                            FileNameOption.ASK_EVERY_TIME -> ""
                            FileNameOption.DEFAULT_NAME -> getDefaultFileName()
                            FileNameOption.PAGE_TITLE -> {
                                val title = processor.extractTitle(html)
                                if (title.isNullOrBlank()) getDefaultFileName()
                                else processor.sanitizeFilename(title)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            _notePreview = markdown
                            _fileNameInput = newFileName
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _notePreview = "Ошибка загрузки страницы: ${e.message}"
                            _fileNameInput = ""
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
                val fileName = _fileNameInput.ifBlank {
                    if (_fileNameOption == FileNameOption.DEFAULT_NAME) getDefaultFileName()
                    else "page_${System.currentTimeMillis()}.md"
                }
                // Синхронизируем URI с FileSaveHandler
                fileSaveHandler.lastCustomFolderUri = _lastCustomFolderUri

                // Даем команду сохранить без лишних деталей
                fileSaveHandler.saveNote(
                    fileName,
                    _notePreview,
                    _saveLocationOption,
                    onFolderPickerRequest = {
                        folderPickerLauncher.launch(null)
                    },
                    onSaveResult = { success ->
                        if (!success) println("❗ Ошибка сохранения заметки")
                        // Тут можно показать UI-уведомление
                    }
                )
            }

            /**
             * Задержка на Splash экране, после которой переходим к основному
             */
            LaunchedEffect(Unit) {
                delay(2000L)
                _currentScreen = Screen.Main
            }

            currentScreen = _currentScreen
            savedFolderPath = _savedFolderPath
            lastCustomFolderUri = _lastCustomFolderUri
            urlState.value = _urlState.value
            fileNameOption = _fileNameOption
            saveLocationOption = _saveLocationOption
            notePreview = _notePreview
            fileNameInput = _fileNameInput

            when (_currentScreen) {
                Screen.Splash -> SplashScreen()
                Screen.Main -> MainScreen(
                    urlState = _urlState.value,
                    onUrlChange = { _urlState.value = it },
                    onProcessClick = { processUrl() },
                    onSaveClick = { saveNote() },
                    onClearClick = { clearFields() },
                    onOpenSettings = { _currentScreen = Screen.Settings },
                    fileNameInput = _fileNameInput,
                    onFileNameInputChange = { _fileNameInput = it },
                    notePreview = _notePreview
                )
                Screen.Settings -> SettingsScreen(
                    initialPath = _savedFolderPath,
                    initialFileNameOption = _fileNameOption,
                    initialSaveLocationOption = _saveLocationOption,
                    onSave = { _, path, option, locationOption ->
                        _savedFolderPath = path
                        _fileNameOption = option
                        _saveLocationOption = locationOption
                        _currentScreen = Screen.Main
                        if (locationOption == SaveLocationOption.CUSTOM_FOLDER && !path.isNullOrBlank()) {
                            _lastCustomFolderUri = path
                        }
                    }
                )
            }
        }
    }
}
