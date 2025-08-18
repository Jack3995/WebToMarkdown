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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jack3995.webtomarkdown.FileNameOption

@Composable
fun SettingsScreen(
    initialPath: String?,
    initialFileNameOption: FileNameOption,
    onSave: (
        askEveryTime: Boolean,
        savedPath: String?,
        fileNameOption: FileNameOption
    ) -> Unit
) {
    var selectedOption by rememberSaveable { mutableStateOf(if (initialPath.isNullOrEmpty()) 0 else 1) }
    var folderPath by rememberSaveable { mutableStateOf(initialPath ?: "") }
    var selectedFileNameOption by rememberSaveable { mutableStateOf(initialFileNameOption) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                folderPath = it.toString()
                selectedOption = 1
            }
        }
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Выберите способ сохранения файлов:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                .padding(8.dp)
        ) {
            RadioButton(selected = selectedOption == 0, onClick = { selectedOption = 0 })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Всегда спрашивать")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                .padding(8.dp)
        ) {
            RadioButton(selected = selectedOption == 1, onClick = { selectedOption = 1 })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Сохранять в указанную папку:")
        }
        if (selectedOption == 1) {
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
        Text("Выберите вариант наименования файла:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            FileNameOption.values().forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
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
                        text = when(option) {
                            FileNameOption.ASK_EVERY_TIME -> "Спрашивать каждый раз"
                            FileNameOption.DEFAULT_NAME -> "По умолчанию (текущее имя)"
                            FileNameOption.PAGE_TITLE -> "Имя страницы (заголовок сайта)"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            onSave(selectedOption == 0, if (selectedOption == 1) folderPath else null, selectedFileNameOption)
        }) {
            Text("Сохранить настройки")
        }
    }
}
