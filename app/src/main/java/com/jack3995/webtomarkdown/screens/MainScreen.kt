package com.jack3995.webtomarkdown.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    urlState: String,
    onUrlChange: (String) -> Unit,
    onProcessClick: () -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
    onOpenSettings: () -> Unit,
    fileNameInput: String,
    onFileNameInputChange: (String) -> Unit,
    notePreview: String,
    isLoading: Boolean = false
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Функция для вставки из буфера обмена
    fun pasteFromClipboard() {
        coroutineScope.launch {
            try {
                val text = clipboardManager.getText()
                if (text != null) {
                    onUrlChange(text.text)
                    snackbarHostState.showSnackbar("URL вставлен из буфера обмена")
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Ошибка при вставке из буфера обмена")
            }
        }
    }

    // Функция для показа уведомлений
    fun showNotification(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebToMarkdown") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedButton(
                        onClick = {
                            onProcessClick()
                            showNotification("Начинаем обработку страницы...")
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = urlState.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Обработать")
                    }
                    
                    AnimatedButton(
                        onClick = {
                            onClearClick()
                            showNotification("Поля очищены")
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить")
                    }
                    
                    AnimatedButton(
                        onClick = {
                            onSaveClick()
                            showNotification("Файл сохранен")
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = notePreview.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сохранить")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Поле URL с кнопкой вставки
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = urlState,
                    onValueChange = onUrlChange,
                    label = { Text("Введите URL сайта") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedButton(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Вставить из буфера")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Анимированная индикация загрузки
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Обрабатываем страницу...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Анимированный контент результата
            AnimatedVisibility(
                visible = notePreview.isNotBlank() && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
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
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = notePreview,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// Анимированная кнопка с эффектом нажатия
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "button_scale"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content
    )
}
