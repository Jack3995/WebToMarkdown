package com.jack3995.webtomarkdown.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

/**
 * Основной экран настроек приложения.
 *
 * Позволяет выбрать место сохранения файла и формат имени файла.
 * Предоставляет возможность выбрать пользовательскую папку через SAF,
 * а также сохраняет изменения по кнопке с уведомлением в Snackbar.
 *
 * @param initialPath начальный путь пользовательской папки (если выбран `CUSTOM_FOLDER`)
 * @param initialFileNameOption начальный вариант формирования имени файла
 * @param initialSaveLocationOption начальное место сохранения файла
 * @param onSave колбэк, вызываемый при сохранении настроек, передаёт:
 * - askEveryTime - сохранять ли каждый раз с выбором папки,
 * - savedPath - путь к папке (если указан),
 * - fileNameOption - выбранный вариант имени файла,
 * - saveLocationOption - выбранное место сохранения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialPath: String?,
    initialFileNameOption: FileNameOption,
    initialSaveLocationOption: SaveLocationOption,
    initialDownloadImages: Boolean = true,
    initialUsePatterns: Boolean = true,
    supportedDomains: List<String> = emptyList(),
    onSave: (
        Boolean,
        String?,
        FileNameOption,
        SaveLocationOption,
        Boolean,
        Boolean
    ) -> Unit
) {
    // Состояние для выбора варианта места сохранения (0 — ASK_EVERY_TIME, 1 — CUSTOM_FOLDER)
    var selectedOption by rememberSaveable {
        mutableIntStateOf(
            when (initialSaveLocationOption) {
                SaveLocationOption.ASK_EVERY_TIME -> 0
                SaveLocationOption.CUSTOM_FOLDER -> 1
            }
        )
    }

    // Состояние для пути к выбранной папке (если CUSTOM_FOLDER)
    var folderPath by rememberSaveable { mutableStateOf(initialPath ?: "") }

    // Состояние для варианта формирования имени файла
    var selectedFileNameOption by rememberSaveable { mutableStateOf(initialFileNameOption) }
    
    // Состояние для скачивания изображений
    var downloadImages by rememberSaveable { mutableStateOf(initialDownloadImages) }

    // Состояние для использования паттернов
    var usePatterns by rememberSaveable { mutableStateOf(initialUsePatterns) }

    // Запуск SAF для выбора папки
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                folderPath = it.toString()
                selectedOption = 1
            }
        }
    )

    // Snackbar для уведомления о сохранении
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Функция для преобразования URI в читаемый путь
    fun getReadablePath(uriString: String): String {
        return try {
            val uri = uriString.toUri()
            // Пытаемся получить читаемое имя папки
            val lastSegment = uri.lastPathSegment
            if (lastSegment != null && lastSegment != "tree") {
                lastSegment
            } else {
                // Если это корневая папка, показываем "Корневая папка"
                "Корневая папка"
            }
        } catch (_: Exception) {
            uriString
        }
    }

    // Функция сохранения настроек
    fun saveSettings() {
        val saveLoc = when (selectedOption) {
            0 -> SaveLocationOption.ASK_EVERY_TIME
            1 -> SaveLocationOption.CUSTOM_FOLDER
            else -> SaveLocationOption.ASK_EVERY_TIME
        }

        onSave(
            selectedOption == 0,
            if (selectedOption == 1) folderPath else null,
            selectedFileNameOption,
            saveLoc,
            downloadImages,
            usePatterns
        )

        coroutineScope.launch {
            snackbarHostState.showSnackbar("Настройки сохранены")
        }
    }

    // Обработчик кнопки "назад" - сохраняет настройки и возвращается на главный экран
    BackHandler {
        saveSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                actions = {
                    IconButton(onClick = { saveSettings() }) {
                        Icon(Icons.Filled.Save, contentDescription = "Сохранить настройки")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Блок 1: Место сохранения заметки
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Место сохранения заметки:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Вариант 1: Всегда спрашивать
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Всегда спрашивать")
                        }

                        // Вариант 2: Пользовательская папка
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("В указанную папку")
                        }

                        // Если выбран пользовательский путь, показываем его и кнопку выбора папки
                        if (selectedOption == 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = getReadablePath(folderPath),
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Выбранный путь") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = "Выбрать папку")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Блок 2: Наименование заметки
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Наименование заметки:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Список вариантов имен файлов с радио-кнопками
                        Column {
                            FileNameOption.entries.forEach { option ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedFileNameOption == option,
                                            onClick = { selectedFileNameOption = option }
                                        )
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedFileNameOption == option,
                                        onClick = { selectedFileNameOption = option }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        when (option) {
                                            FileNameOption.DEFAULT_NAME -> "По умолчанию (Заметка_ДД.ММ.ГГГГ_ЧЧ.ММ)"
                                            FileNameOption.PAGE_TITLE -> "Имя страницы (заголовок сайта)"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Блок 3: Обработка контента
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Обработка контента:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Переключатель для использования паттернов сайтов + инфо-иконка
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Использовать паттерны обработки сайтов",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            var showInfo by rememberSaveable { mutableStateOf(false) }
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Filled.Info, contentDescription = "Список поддерживаемых сайтов")
                            }
                            Switch(
                                checked = usePatterns,
                                onCheckedChange = { usePatterns = it }
                            )

                            if (showInfo) {
                                AlertDialog(
                                    onDismissRequest = { showInfo = false },
                                    confirmButton = {
                                        TextButton(onClick = { showInfo = false }) {
                                            Text("OK")
                                        }
                                    },
                                    title = { Text("Поддерживаемые сайты") },
                                    text = {
                                        val listText = if (supportedDomains.isNotEmpty()) supportedDomains.joinToString(", ") else "Пока нет"
                                        Text(listText)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Переключатель для скачивания изображений
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Скачивать изображения с сайта",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = downloadImages,
                                onCheckedChange = { downloadImages = it }
                            )
                        }

                        // Убрали сворачиваемую подпись, используем инфо-иконку и диалог
                    }
                }
            }
        }
    }
}

/**
 * Перечисление вариантов формирования имени файла.
 *
 * - DEFAULT_NAME: Использовать имя по умолчанию, например "Заметка_ДД.ММ.ГГГГ_ЧЧ.ММ".
 * - PAGE_TITLE: Использовать заголовок веб-страницы в качестве имени файла.
 */
enum class FileNameOption {
    DEFAULT_NAME,
    PAGE_TITLE
}

/**
 * Перечисление вариантов места сохранения файла.
 *
 * - ASK_EVERY_TIME: Каждый раз спрашивать папку для сохранения.
 * - CUSTOM_FOLDER: Пользователь указывает конкретную папку.
 */
enum class SaveLocationOption {
    ASK_EVERY_TIME,
    CUSTOM_FOLDER
}
