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
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    private val processor = WebContentProcessor()
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

        // Инициализация лаунчера для выбора папки через SAF
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            // Сохраняем выбранный URI папки в виде строки
            lastCustomFolderUri = uri?.toString()
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

            // Очищает все поля ввода и предпросмотра
            fun clearFields() {
                _urlState.value = ""
                _fileNameInput = ""
                _notePreview = ""
            }

            /**
             * Загружает страницу по текущему url, обрабатывает её через WebContentProcessor,
             * обновляет предпросмотр markdown и устанавливает имя файла.
             * Обработка происходит асинхронно в фоновом потоке.
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
                    val result = processor.processPage(url, _fileNameOption)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val data = result.getOrThrow()
                            _notePreview = data.markdown
                            _fileNameInput = data.fileName
                        } else {
                            _notePreview = "Ошибка загрузки страницы: ${result.exceptionOrNull()?.message}"
                            _fileNameInput = ""
                        }
                    }
                }
            }

            /**
             * Обрабатывает команду сохранения заметки:
             * - Определяет имя файла (если пустое, генерирует по умолчанию)
             * - Передает данные в FileSaveHandler для сохранения
             * - Запускает выбор папки, если нужно
             */
            fun saveNote() {
                val fileName = _fileNameInput.ifBlank {
                    if (_fileNameOption == FileNameOption.DEFAULT_NAME) processor.getDefaultFileName()
                    else "page_${System.currentTimeMillis()}.md"
                }

                fileSaveHandler.lastCustomFolderUri = _lastCustomFolderUri
                fileSaveHandler.saveNote(
                    fileName,
                    _notePreview,
                    _saveLocationOption,
                    onFolderPickerRequest = {
                        folderPickerLauncher.launch(null)
                    },
                    onSaveResult = { success ->
                        if (!success) println("❗ Ошибка сохранения заметки")
                        // Здесь можно добавить UI-уведомление об успехе/ошибке
                    }
                )
            }

            // Задержка на Splash экране, затем переход к основному экрану
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000L)
                _currentScreen = Screen.Main
            }

            // Синхронизация локальных состояний с внешними состояниями
            currentScreen = _currentScreen
            savedFolderPath = _savedFolderPath
            lastCustomFolderUri = _lastCustomFolderUri

            urlState.value = _urlState.value
            fileNameOption = _fileNameOption
            saveLocationOption = _saveLocationOption
            notePreview = _notePreview
            fileNameInput = _fileNameInput

            // Отображение соответствующего экрана в зависимости от состояния
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
