package com.jack3995.webtomarkdown.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    urlState: String,
    onUrlChange: (String) -> Unit,
    onProcessClick: () -> Unit,
    onSaveClick: () -> Unit,
    onOpenSettings: () -> Unit,
    fileNameInput: String,
    onFileNameInputChange: (String) -> Unit,
    notePreview: String
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebToMarkdown") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Menu, contentDescription = "Открыть настройки")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onProcessClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Обработать")
                    }
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            TextField(
                value = urlState,
                onValueChange = onUrlChange,
                label = { Text("Введите URL сайта") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = fileNameInput,
                onValueChange = onFileNameInputChange,
                label = { Text("Имя файла") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Предпросмотр заметки:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)  // Занимает всё оставшееся место в колонке
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()) // Позволяет прокрутку предпросмотра
            ) {
                Text(
                    text = notePreview,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
