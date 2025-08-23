package com.jack3995.webtomarkdown.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
@Composable
fun SettingsScreen(
    initialPath: String?,
    initialFileNameOption: FileNameOption,
    initialSaveLocationOption: SaveLocationOption,
    initialDownloadImages: Boolean = true,
    onSave: (
        Boolean,
        String?,
        FileNameOption,
        SaveLocationOption,
        Boolean
    ) -> Unit
) {
    // Состояние для выбора варианта места сохранения (0 — ASK_EVERY_TIME, 1 — DOWNLOADS, 2 — CUSTOM_FOLDER)
    var selectedOption by rememberSaveable {
        mutableIntStateOf(
            when (initialSaveLocationOption) {
                SaveLocationOption.ASK_EVERY_TIME -> 0
                SaveLocationOption.DOWNLOADS -> 1
                SaveLocationOption.CUSTOM_FOLDER -> 2
            }
        )
    }

    // Состояние для пути к выбранной папке (если CUSTOM_FOLDER)
    var folderPath by rememberSaveable { mutableStateOf(initialPath ?: "") }

    // Состояние для варианта формирования имени файла
    var selectedFileNameOption by rememberSaveable { mutableStateOf(initialFileNameOption) }
    
    // Состояние для скачивания изображений
    var downloadImages by rememberSaveable { mutableStateOf(initialDownloadImages) }

    // Запуск SAF для выбора папки
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                folderPath = it.toString()
                selectedOption = 2
            }
        }
    )

    // Snackbar для уведомления о сохранении
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(paddingValues)
        ) {

            // Заголовок блока выбора места сохранения
            Text("Место сохранения заметки:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Вариант 1: Всегда спрашивать
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                    .padding(8.dp)
            ) {
                RadioButton(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Всегда спрашивать")
            }

            // Вариант 2: Папка «Загрузки»
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                    .padding(8.dp)
            ) {
                RadioButton(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Папка «Загрузки»")
            }

            // Вариант 3: Пользовательская папка
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selectedOption == 2, onClick = { selectedOption = 2 })
                    .padding(8.dp)
            ) {
                RadioButton(selected = selectedOption == 2, onClick = { selectedOption = 2 })
                Spacer(modifier = Modifier.width(8.dp))
                Text("В указанную папку")
            }

            // Если выбран пользовательский путь, показываем его и кнопку выбора папки
            if (selectedOption == 2) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Заголовок блока выбора имени файла
            Text("Вариант наименования файла:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

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
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedFileNameOption == option,
                            onClick = { selectedFileNameOption = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (option) {
                                FileNameOption.ASK_EVERY_TIME -> "Спрашивать каждый раз"
                                FileNameOption.DEFAULT_NAME -> "По умолчанию (текущее имя)"
                                FileNameOption.PAGE_TITLE -> "Имя страницы (заголовок сайта)"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Заголовок блока настроек изображений
            Text("Настройки изображений:", style = MaterialTheme.typography.titleMedium)
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

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопка сохранения настроек — вызывает onSave и показывает Snackbar
            Button(onClick = {
                val saveLoc = when (selectedOption) {
                    0 -> SaveLocationOption.ASK_EVERY_TIME
                    1 -> SaveLocationOption.DOWNLOADS
                    2 -> SaveLocationOption.CUSTOM_FOLDER
                    else -> SaveLocationOption.ASK_EVERY_TIME
                }

                // Вызов колбэка без именованных аргументов
                onSave(
                    selectedOption == 0,
                    if (selectedOption == 2) folderPath else null,
                    selectedFileNameOption,
                    saveLoc,
                    downloadImages
                )

                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Настройки сохранены")
                }
            }) {
                Text("Сохранить настройки")
            }
        }
    }
}

/**
 * Перечисление вариантов формирования имени файла.
 *
 * - ASK_EVERY_TIME: Спрашивать имя файла вручную каждый раз.
 * - DEFAULT_NAME: Использовать имя по умолчанию, например "Заметка_дата".
 * - PAGE_TITLE: Использовать заголовок веб-страницы в качестве имени файла.
 */
enum class FileNameOption {
    ASK_EVERY_TIME,
    DEFAULT_NAME,
    PAGE_TITLE
}

/**
 * Перечисление вариантов места сохранения файла.
 *
 * - ASK_EVERY_TIME: Каждый раз спрашивать папку для сохранения.
 * - DOWNLOADS: Сохранять в системную папку «Загрузки».
 * - CUSTOM_FOLDER: Пользователь указывает конкретную папку.
 */
enum class SaveLocationOption {
    ASK_EVERY_TIME,
    DOWNLOADS,
    CUSTOM_FOLDER
}
