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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.zIndex
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
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    

    // Функция для вставки из буфера обмена
    fun pasteFromClipboard() {
        scope.launch {
            try {
                val clipboardText = clipboard.getText()?.text
                if (clipboardText != null) {
                    onUrlChange(clipboardText)
                    // Автозапуск обработки после вставки ссылки
                    onProcessClick()
                }
            } catch (_: Exception) {
                // Ошибку вставки игнорируем без всплывающих уведомлений
            }
        }
    }

    // Всплывающие уведомления удалены

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
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.zIndex(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Первый ряд: Вставить | Очистить
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedButton(
                            onClick = { pasteFromClipboard() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            enabled = !isLoading
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste, 
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Вставить")
                            }
                        }
                        
                        AnimatedButton(
                            onClick = { onClearClick() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            enabled = !isLoading
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Clear, 
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Очистить")
                            }
                        }
                    }
                    
                    // Второй ряд: Обработать | Сохранить
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedButton(
                            onClick = { onProcessClick() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            enabled = urlState.isNotBlank() && !isLoading
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Refresh, 
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Обработать")
                            }
                        }
                        
                        AnimatedButton(
                            onClick = { onSaveClick() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            enabled = notePreview.isNotBlank() && !isLoading
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Save, 
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить")
                            }
                        }
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
            // Поле URL
            OutlinedTextField(
                value = urlState,
                onValueChange = onUrlChange,
                label = { Text("Введите URL сайта") },
                placeholder = { Text("https://example.com/article") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = onFileNameInputChange,
                        label = { Text("Имя файла") },
                        placeholder = { Text("Заметка_01.01.2025_12.00") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        singleLine = false,
                        maxLines = 4,
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
        shape = RoundedCornerShape(8.dp),
        content = content
    )
}
