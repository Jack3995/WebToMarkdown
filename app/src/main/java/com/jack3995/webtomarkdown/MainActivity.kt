package com.jack3995.webtomarkdown

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.jack3995.webtomarkdown.screens.ThemeOption
import com.jack3995.webtomarkdown.util.WebContentProcessor
import com.jack3995.webtomarkdown.util.FileSaveHandler
import com.jack3995.webtomarkdown.util.SettingsManager
import com.jack3995.webtomarkdown.util.ThemeManager
import kotlinx.coroutines.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Главная Activity приложения: инициализирует настройки и сохранение,
 * управляет Compose-экраном и делегирует обработку/сохранение в утилиты.
 */
class MainActivity : ComponentActivity() {

    enum class Screen {
        Splash, Main, Settings
    }

    // Обработчик контента (HTML → Markdown, загрузка изображений)
    private val processor = WebContentProcessor()

    // Сервис сохранения заметок (через SAF) и копирования изображений
    private lateinit var fileSaveHandler: FileSaveHandler

    // Менеджер пользовательских настроек
    private lateinit var settingsManager: SettingsManager

    // Менеджер тем приложения
    private lateinit var themeManager: ThemeManager

    // Лаунчер системного диалога выбора папки (SAF)
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    // Локальные поля Activity; синхронизируются с состоянием внутри setContent
    private var fileNameInput by mutableStateOf("")          // Текущее имя файла
    private var notePreview by mutableStateOf("")            // Markdown превью
    private var urlState = mutableStateOf("")                // Введённый URL

    // Настройки приложения
    private var fileNameOption by mutableStateOf(FileNameOption.DEFAULT_NAME)            // Правило имени файла
    private var saveLocationOption by mutableStateOf(SaveLocationOption.ASK_EVERY_TIME)  // Место сохранения
    private var lastCustomFolderUri by mutableStateOf<String?>(null)                     // Запомненный SAF URI

    private var downloadImages by mutableStateOf(true)       // Загружать ли изображения
    private var usePatterns by mutableStateOf(true)          // Использовать паттерны сайтов
    private var imagesFolder by mutableStateOf<File?>(null)  // Текущая папка изображений
    private var isLoading by mutableStateOf(false)           // Индикатор фоновой загрузки
    private var themeOption by mutableStateOf(ThemeOption.SYSTEM) // Выбранная тема

    // Навигация и сохранённый путь папки
    private var currentScreen by mutableStateOf(Screen.Splash)
    private var savedFolderPath by mutableStateOf<String?>(null)

    // Буфер значений, которые используются после выбора папки в SAF
    private var pendingFileName by mutableStateOf("")
    private var pendingContent by mutableStateOf("")
    private var pendingImagesFolder by mutableStateOf<File?>(null)

    // Флаг автозапуска обработки после получения ссылки извне
    private var pendingAutoProcess: Boolean = false


    private fun handleSendIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                Log.d("ShareIntent", "Получена ссылка: $sharedText")
                urlState.value = sharedText.trim()
                // Отметим, что нужно автообработать ссылку после инициализации UI
                pendingAutoProcess = true
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация менеджера тем
        themeManager = ThemeManager(this)

        // Настройка системных баров
        themeManager.setupSystemBars()

        fileSaveHandler = FileSaveHandler(this, contentResolver)
        settingsManager = SettingsManager(this)

        // Загружаем сохраненные настройки
        fileNameOption = settingsManager.getFileNameOption()
        saveLocationOption = settingsManager.getSaveLocationOption()
        lastCustomFolderUri = settingsManager.getCustomFolderPath()
        downloadImages = settingsManager.getDownloadImages()
        usePatterns = settingsManager.getUsePatterns()
        themeOption = settingsManager.getThemeOption()
        
        // Устанавливаем цвета системных баров при запуске (по умолчанию светлая тема)
        themeManager.updateSystemBarsColors(themeOption, false)

        // Регистрируем диалог выбора папки. Возвращает Uri выбранной директории (или null).
        folderPickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                lastCustomFolderUri = uri?.toString()
                // Сохраняем выбранную папку в настройки
                if (uri != null) {
                    settingsManager.saveCustomFolderPath(uri.toString())
                }

                // Используем сохраненные значения для сохранения файла
                if (uri != null) {
                    println("📁 MainActivity: Выбрана папка: $uri")
                    println("💾 MainActivity: Передаем управление FileSaveHandler для сохранения")
                    // Используем отложенные значения, подготовленные в FileSaveHandler
                    fileSaveHandler.onFolderPickedUsePending(uri) { success ->
                        if (!success) {
                            println("❗ MainActivity получил результат: Ошибка сохранения файла через SAF")
                        } else {
                            println("✅ MainActivity получил результат: Файл успешно сохранен через SAF")
                            Toast.makeText(this@MainActivity, "Заметка сохранена", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    println("❌ MainActivity: Папка не выбрана")
                }
            }

        // Обрабатываем интент при старте
        handleSendIntent(intent)

        // Запускаем Compose UI и связываем его со стабильным состоянием
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
            var _imagesFolder by remember { mutableStateOf<File?>(imagesFolder) }   // Папка изображений для UI
            var _originalUrl by remember { mutableStateOf("") }                    // Исходный URL
            var _tempImagesFolder by remember { mutableStateOf<File?>(null) }       // Временная папка изображений
            var _isLoading by remember { mutableStateOf(isLoading) }                 // Индикатор загрузки для UI
            var _themeOption by rememberSaveable { mutableStateOf(settingsManager.getThemeOption()) }
            val supportedDomains by remember { mutableStateOf(processor.getSupportedPatternDomains()) }
            val isSystemInDarkTheme = isSystemInDarkTheme()

            // Очистка полей ввода и превью
            fun clearFields() {
                _urlState.value = ""
                _fileNameInput = ""
                _notePreview = ""
            }

            // Генерация имени по умолчанию (на случай локального использования)
            fun getDefaultFileName(): String {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH.mm", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                return "Заметка_$currentDate"
            }

            // Обрабатывает URL: запускает процесс скачивания и конвертации через WebContentProcessor
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

                // Выполнение тяжёлой работы в IO, обновление UI — на Main
                CoroutineScope(Dispatchers.IO).launch {
                    val result = processor.processPage(
                        url,
                        _fileNameOption,
                        _downloadImages,
                        _usePatterns,
                        _fileNameInput.takeIf { it.isNotBlank() })
                    withContext(Dispatchers.Main) {
                        _isLoading = false
                        if (result.isSuccess) {
                            val data = result.getOrThrow()
                            _notePreview = data.markdown
                            _fileNameInput = data.fileName
                            _originalUrl = url
                            _tempImagesFolder = data.tempImagesFolder

                            println("✅ MainActivity: Страница успешно обработана")
                            if (data.tempImagesFolder != null) {
                                println("📁 MainActivity: Получена временная папка с изображениями: ${data.tempImagesFolder.name}")
                            } else {
                                println("ℹ️ MainActivity: Временная папка не создана (изображения отключены или не найдены)")
                            }
                        } else {
                            _notePreview =
                                "Ошибка загрузки страницы: ${result.exceptionOrNull()?.message}"
                            _fileNameInput = ""
                            _originalUrl = ""
                            _tempImagesFolder = null
                            println("❌ MainActivity: Ошибка обработки страницы: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            }

            // Делегирует FileSaveHandler полный алгоритм сохранения
            fun saveNote() {
                println("💾 Пользователь нажал сохранить! Передаем управление FileSaveHandler")

                fileSaveHandler.lastCustomFolderUri = _lastCustomFolderUri

                // Передаем всю логику сохранения в FileSaveHandler
                fileSaveHandler.saveNoteWithFullLogic(
                    fileName = _fileNameInput,
                    content = _notePreview,
                    saveLocationOption = _saveLocationOption,
                    fileNameOption = _fileNameOption,
                    downloadImages = _downloadImages,
                    usePatterns = _usePatterns,
                    originalUrl = _originalUrl,
                    tempImagesFolder = _tempImagesFolder,
                    onFolderPickerRequest = {
                        // Сохраняем значения, чтобы использовать их после выбора папки в SAF
                        pendingFileName = _fileNameInput
                        pendingContent = _notePreview
                        pendingImagesFolder = _tempImagesFolder

                        println("📁 Запрашиваем выбор папки у пользователя")
                        folderPickerLauncher.launch(null)
                    },
                    onSaveResult = { success ->
                        if (!success) {
                            println("❗ MainActivity получил результат: Ошибка сохранения")
                        } else {
                            println("✅ MainActivity получил результат: Успешное сохранение")
                            Toast.makeText(this@MainActivity, "Заметка сохранена", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            LaunchedEffect(Unit) {
                delay(2000L)
                _currentScreen = Screen.Main
            }

            // Автозапуск обработки, если ссылка пришла через Share-интент
            LaunchedEffect(_urlState.value) {
                if (pendingAutoProcess && _urlState.value.isNotBlank()) {
                    pendingAutoProcess = false
                    processUrl()
                }
            }
            
            // Обновляем цвета системных баров при изменении темы
            LaunchedEffect(_themeOption, isSystemInDarkTheme) {
                themeManager.updateSystemBarsColors(_themeOption, isSystemInDarkTheme)
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
            themeOption = _themeOption

            MaterialTheme(
                colorScheme = themeManager.getColorScheme(_themeOption, isSystemInDarkTheme)
            ) {
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
                            initialThemeOption = _themeOption,
                            supportedDomains = supportedDomains,
                            onSave = { _, path, option, locationOption, downloadImages, usePatterns, themeOption ->
                                _savedFolderPath = path
                                _fileNameOption = option
                                _saveLocationOption = locationOption
                                _downloadImages = downloadImages
                                _usePatterns = usePatterns
                                _themeOption = themeOption

                                // Сохраняем настройки в SharedPreferences
                                settingsManager.saveAllSettings(
                                    locationOption,
                                    path,
                                    option,
                                    downloadImages,
                                    usePatterns,
                                    themeOption
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }
}