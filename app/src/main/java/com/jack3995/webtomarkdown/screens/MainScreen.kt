package com.jack3995.webtomarkdown.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Главный экран приложения с полем ввода и кнопками,
// выровненный по центру, с TopAppBar и иконкой меню настроек справа
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    urlState: String,
    onUrlChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onOpenSettings: () -> Unit
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                TextField(
                    value = urlState,
                    onValueChange = onUrlChange,
                    label = { Text("Введите URL сайта") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Сохранить в Markdown")
                }
            }
        }
    }
}
