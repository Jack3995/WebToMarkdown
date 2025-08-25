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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.jack3995.webtomarkdown.screens.*
import com.jack3995.webtomarkdown.screens.FileNameOption
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import com.jack3995.webtomarkdown.util.WebContentProcessor
import com.jack3995.webtomarkdown.util.FileSaveHandler
import com.jack3995.webtomarkdown.util.SettingsManager
import kotlinx.coroutines.*
import androidx.compose.material3.ExperimentalMaterial3Api
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    private val processor = WebContentProcessor()
    private lateinit var fileSaveHandler: FileSaveHandler
    private lateinit var settingsManager: SettingsManager
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private var fileNameInput by mutableStateOf("")
    private var notePreview by mutableStateOf("")
    private var urlState = mutableStateOf("")

    private var fileNameOption by mutableStateOf(FileNameOption.DEFAULT_NAME)
    private var saveLocationOption by mutableStateOf(SaveLocationOption.ASK_EVERY_TIME)
    private var lastCustomFolderUri by mutableStateOf<String?>(null)

    private var downloadImages by mutableStateOf(true)
    private var usePatterns by mutableStateOf(true)
    private var imagesFolder by mutableStateOf<File?>(null) // Инициализировано с корректным типом
    private var isLoading by mutableStateOf(false)

    private var currentScreen by mutableStateOf(Screen.Splash)
    private var savedFolderPath by mutableStateOf<String?>(null)
    
    // Переменные для хранения текущих значений при выборе папки
    private var pendingFileName by mutableStateOf("")
    private var pendingContent by mutableStateOf("")
    private var pendingImagesFolder by mutableStateOf<File?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileSaveHandler = FileSaveHandler(this, contentResolver)
        settingsManager = SettingsManager(this)
        
        // Загружаем сохраненные настройки
        fileNameOption = settingsManager.getFileNameOption()
        saveLocationOption = settingsManager.getSaveLocationOption()
        lastCustomFolderUri = settingsManager.getCustomFolderPath()
        downloadImages = settingsManager.getDownloadImages()
        usePatterns = settingsManager.getUsePatterns()

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            lastCustomFolderUri = uri?.toString()
            // Сохраняем выбранную папку в настройки
            if (uri != null) {
                settingsManager.saveCustomFolderPath(uri.toString())
            }
            
            // Используем сохраненные значения для сохранения файла
            if (uri != null) {
                println("📁 Выбрана папка: $uri")
                println("💾 Сохраняем файл: $pendingFileName")
                pendingImagesFolder?.let { folder ->
                    println("🖼️ Папка с изображениями: ${folder.absolutePath}")
                }
                
                fileSaveHandler.onFolderPicked(uri, pendingFileName, pendingContent, { success ->
                    if (!success) println("❗ Ошибка сохранения файла через SAF")
                    else println("✅ Файл успешно сохранен через SAF")
                }, pendingImagesFolder)
            } else {
                println("❌ Папка не выбрана")
            }
        }

        // Обрабатываем интент при старте
        handleSendIntent(intent)

        setContent {
            var _currentScreen by rememberSaveable { mutableStateOf(currentScreen) }
            var _savedFolderPath by rememberSaveable { mutableStateOf(settingsManager.getCustomFolderPath()) }
            var _lastCustomFolderUri by rememberSaveable { mutableStateOf(settingsManager.getCustomFolderPath()) }

            val _urlState = remember { mutableStateOf(urlState.value) }
            var _fileNameOption by rememberSaveable { mutableStateOf(settingsManager.getFileNameOption()) }
            var _saveLocationOption by rememberSaveable { mutableStateOf(settingsManager.getSaveLocationOption()) }
            var _notePreview by remember { mutableStateOf(notePreview) }
            var _fileNameInput by remember { mutableStateOf(fileNameInput) }
            var _downloadImages by rememberSaveable { mutableStateOf(settingsManager.getDownloadImages()) }
            var _usePatterns by rememberSaveable { mutableStateOf(settingsManager.getUsePatterns()) }
            var _imagesFolder by remember { mutableStateOf<File?>(imagesFolder) }
            var _isLoading by remember { mutableStateOf(isLoading) }
            val supportedDomains by remember { mutableStateOf(processor.getSupportedPatternDomains()) }

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

                _isLoading = true
                _notePreview = ""

                CoroutineScope(Dispatchers.IO).launch {
                    val result = processor.processPage(url, _fileNameOption, _downloadImages, _usePatterns)
                    withContext(Dispatchers.Main) {
                        _isLoading = false
                        if (result.isSuccess) {
                            val data = result.getOrThrow()
                            _notePreview = data.markdown
                            _fileNameInput = data.fileName
                            _imagesFolder = data.imagesFolder
                            if (data.imagesFolder != null) {
                                println("📁 Папка с изображениями: ${data.imagesFolder.name}")
                            }
                            println("✅ Страница успешно обработана")
                        } else {
                            _notePreview = "Ошибка загрузки страницы: ${result.exceptionOrNull()?.message}"
                            _fileNameInput = ""
                            _imagesFolder = null
                            println("❌ Ошибка обработки страницы: ${result.exceptionOrNull()?.message}")
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

                println("💾 Сохраняем заметку: $fileName")
                _imagesFolder?.let { folder ->
                    println("📁 Папка с изображениями: ${folder.absolutePath}")
                    println("📁 Содержимое папки: ${folder.listFiles()?.map { it.name } ?: "пусто"}")
                } ?: run {
                    println("⚠️ Папка с изображениями не найдена")
                }

                fileSaveHandler.saveNote(
                    fileName,
                    _notePreview,
                    _saveLocationOption,
                    onFolderPickerRequest = {
                        // Сохраняем текущие значения для использования в замыкании
                        pendingFileName = fileName
                        pendingContent = _notePreview
                        pendingImagesFolder = _imagesFolder
                        
                        println("💾 Сохранены значения для выбора папки:")
                        println("   Файл: $fileName")
                        println("   Папка с изображениями: ${_imagesFolder?.absolutePath ?: "нет"}")
                        
                        folderPickerLauncher.launch(null)
                    },
                    onSaveResult = { success ->
                        if (!success) {
                            println("❗ Ошибка сохранения заметки")
                        } else {
                            println("✅ Заметка успешно сохранена")
                        }
                    },
                    imagesFolder = _imagesFolder
                )
            }

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
            downloadImages = _downloadImages
            usePatterns = _usePatterns
            imagesFolder = _imagesFolder
            isLoading = _isLoading

            AnimatedContent(
                targetState = _currentScreen,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { if (targetState > initialState) it else -it },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith slideOutHorizontally(
                        targetOffsetX = { if (targetState > initialState) -it else it },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
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
                        notePreview = _notePreview,
                        isLoading = _isLoading
                    )
                    Screen.Settings -> SettingsScreen(
                        initialPath = _savedFolderPath,
                        initialFileNameOption = _fileNameOption,
                        initialSaveLocationOption = _saveLocationOption,
                        initialDownloadImages = _downloadImages,
                        initialUsePatterns = _usePatterns,
                        supportedDomains = supportedDomains,
                        onSave = { _, path, option, locationOption, downloadImages, usePatterns ->
                            _savedFolderPath = path
                            _fileNameOption = option
                            _saveLocationOption = locationOption
                            _downloadImages = downloadImages
                            _usePatterns = usePatterns
                            
                            // Сохраняем настройки в SharedPreferences
                            settingsManager.saveAllSettings(
                                locationOption,
                                path,
                                option,
                                downloadImages,
                                usePatterns
                            )
                            
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
                // Можно тут вызвать processUrl() для автоматической обработки ссылки
            }
        }
    }
}
