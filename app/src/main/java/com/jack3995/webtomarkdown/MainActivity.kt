package com.jack3995.webtomarkdown

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.*

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileSaveHandler = FileSaveHandler(this, contentResolver)

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            lastCustomFolderUri = uri?.toString()
            fileSaveHandler.onFolderPicked(uri, fileNameInput, notePreview) { success ->
                if (!success) println("❗ Ошибка сохранения файла через SAF")
            }
        }

        // Обрабатываем интент при старте
        handleSendIntent(intent)

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

            fun getDefaultFileName(): String {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH.mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                return "Заметка_$currentDate"
            }

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
                        // Можно здесь добавить уведомления для пользователя
                    }
                )
            }

            LaunchedEffect(Unit) {
                delay(2000L)
                _currentScreen = Screen.Main
            }

            // Синхронизация состояний
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                Log.d("ShareIntent", "Получена ссылка: $sharedText")
                urlState.value = sharedText.trim()
                // Если хотите, запустить сразу обработку:
                // processUrl() - нужно адаптировать доступ в этом контексте
            }
        }
    }
}
