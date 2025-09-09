package com.jack3995.webtomarkdown.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import com.jack3995.webtomarkdown.screens.FileNameOption
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileSaveHandler(private val context: Context, private val contentResolver: ContentResolver) {

    var lastCustomFolderUri: String? = null
    private val processor = WebContentProcessor()

    // Pending state for ASK_EVERY_TIME flow
    private var pendingFileName: String? = null
    private var pendingContent: String? = null
    private var pendingImagesFolder: File? = null
    private var pendingImagesDirName: String? = null

    /**
     * Главный метод для сохранения заметки.
     * Делегирует вызов сохранения локально, через SAF или вызывает запрос выбора папки.
     * Результат сообщает через onSaveResult.
     */
    fun saveNote(
        fileName: String,
        content: String,
        saveLocationOption: SaveLocationOption,
        onFolderPickerRequest: () -> Unit,
        onSaveResult: (Boolean) -> Unit,
        imagesFolder: File? = null,
        imagesDirName: String? = null
    ) {
        when (saveLocationOption) {
            SaveLocationOption.ASK_EVERY_TIME -> onFolderPickerRequest()
            SaveLocationOption.CUSTOM_FOLDER -> {
                val uriStr = lastCustomFolderUri
                if (!uriStr.isNullOrBlank()) {
                    val uri = uriStr.toUri()
                    val success = saveNoteToSAF(uri, fileName, content, imagesFolder, imagesDirName)
                    onSaveResult(success)
                } else {
                    // Папка не задана - запрос выбора папки
                    onFolderPickerRequest()
                }
            }
        }
    }

    /**
     * Вызывается после выбора папки системой.
     * Выполняет сохранение и сообщает результат.
     */
    fun onFolderPicked(folderUri: Uri?, fileName: String, content: String, onSaveResult: (Boolean) -> Unit, imagesFolder: File? = null, imagesDirName: String? = null) {
        if (folderUri == null) {
            onSaveResult(false)
            return
        }
        lastCustomFolderUri = folderUri.toString()
        val success = saveNoteToSAF(folderUri, fileName, content, imagesFolder, imagesDirName)
        onSaveResult(success)
    }

    /**
     * Completes save using previously stored pending values (ASK_EVERY_TIME flow).
     */
    fun onFolderPickedUsePending(folderUri: Uri?, onSaveResult: (Boolean) -> Unit) {
        if (folderUri == null) {
            onSaveResult(false)
            return
        }
        val fileName = pendingFileName
        val content = pendingContent
        val images = pendingImagesFolder
        val imagesDir = pendingImagesDirName
        if (fileName.isNullOrBlank() || content == null) {
            println("❌ Нет отложенных данных для сохранения")
            onSaveResult(false)
            return
        }
        println("🔍 onFolderPickedUsePending: используем отложенные данные")
        println("   Файл: $fileName")
        println("   Контент: ${content.length} символов")
        println("   Папка изображений: ${images?.absolutePath}")
        println("   Имя папки изображений: $imagesDir")
        lastCustomFolderUri = folderUri.toString()
        val success = saveNoteToSAF(folderUri, fileName, content, images, imagesDir)
        // clear pending
        pendingFileName = null
        pendingContent = null
        pendingImagesFolder = null
        pendingImagesDirName = null
        onSaveResult(success)
    }

    private fun saveNoteToSAF(folderUri: Uri, fileName: String, content: String, imagesFolder: File? = null, imagesDirName: String? = null): Boolean {
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        val safeFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
        val newFile = pickedDir.createFile("text/markdown", safeFileName) ?: return false

        contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(content.toByteArray())
        } ?: return false

        println("📁 Файл сохранён через SAF в: ${newFile.uri}")
        
        // Сохраняем папку с изображениями, если она есть
        if (imagesFolder != null) {
            if (imagesFolder.exists() && imagesFolder.isDirectory) {
                println("🖼️ Найдена папка с изображениями: ${imagesFolder.absolutePath}")
                // Если имя целевой папки не задано, формируем его из имени файла
                val targetDirName = imagesDirName ?: safeFileName.removeSuffix(".md") + "_images"
                saveImagesFolderToSAF(pickedDir, imagesFolder, targetDirName)
                // Чистим временную папку после успешного копирования
                try {
                    imagesFolder.deleteRecursively()
                    println("🗑️ Временная папка с изображениями удалена")
                } catch (e: Exception) {
                    println("⚠️ Не удалось удалить временную папку: ${e.message}")
                }
            } else {
                println("⚠️ Папка с изображениями не найдена или недоступна: ${imagesFolder.absolutePath}")
            }
        } else {
            println("ℹ️ Папка с изображениями не указана")
        }
        
        return true
    }




    
    /**
     * Копирует папку с изображениями через SAF
     */
    private fun saveImagesFolderToSAF(pickedDir: DocumentFile, imagesFolder: File, imagesDirName: String? = null) {
        try {
            println("🖼️ Начинаем сохранение папки с изображениями: ${imagesFolder.absolutePath}")
            
            // Создаем папку для изображений
            val targetDirName = imagesDirName ?: imagesFolder.name
            val imagesDir = pickedDir.createDirectory(targetDirName) ?: run {
                println("❌ Не удалось создать папку для изображений")
                return
            }
            
            println("📁 Создана папка для изображений: ${imagesDir.name}")
            
            // Копируем все файлы из папки изображений
            val imageFiles = imagesFolder.listFiles()
            if (imageFiles.isNullOrEmpty()) {
                println("⚠️ Папка с изображениями пуста")
                return
            }
            
            println("🖼️ Найдено изображений: ${imageFiles.size}")
            
            imageFiles.forEach { imageFile ->
                try {
                    val mimeType = getMimeType(imageFile.name)
                    println("📸 Сохраняем изображение: ${imageFile.name} (тип: $mimeType)")
                    
                    val newFile = imagesDir.createFile(mimeType, imageFile.name) ?: run {
                        println("❌ Не удалось создать файл: ${imageFile.name}")
                        return@forEach
                    }
                    
                    contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        imageFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    println("✅ Изображение сохранено: ${imageFile.name}")
                } catch (e: Exception) {
                    println("❌ Ошибка сохранения изображения ${imageFile.name}: ${e.message}")
                }
            }
            
            println("🎉 Папка с изображениями успешно сохранена")
        } catch (e: Exception) {
            println("❌ Ошибка сохранения папки с изображениями: ${e.message}")
            e.printStackTrace()
        }
    }
    

    
    /**
     * Полная логика сохранения заметки с обработкой изображений и всеми проверками.
     * Вызывается из MainActivity для выполнения всего алгоритма сохранения.
     */
    fun saveNoteWithFullLogic(
        fileName: String,
        content: String,
        saveLocationOption: SaveLocationOption,
        fileNameOption: FileNameOption,
        downloadImages: Boolean,
        usePatterns: Boolean,
        originalUrl: String,
        tempImagesFolder: File?,
        onFolderPickerRequest: () -> Unit,
        onSaveResult: (Boolean) -> Unit
    ) {
        println("💾 Начинаем полный процесс сохранения заметки")
        println("   Файл: $fileName")
        println("   Опция сохранения: $saveLocationOption")
        println("   Загружать изображения: $downloadImages")
        println("   Использовать паттерны: $usePatterns")
        println("   Оригинальный URL: $originalUrl")
        println("   Временная папка с изображениями: ${tempImagesFolder?.absolutePath ?: "нет"}")

        val finalFileName = fileName.ifBlank {
            if (fileNameOption == FileNameOption.DEFAULT_NAME) processor.getDefaultFileName()
            else "page_${System.currentTimeMillis()}.md"
        }

        println("💾 Финальное имя файла: $finalFileName")

        // Проверяем, есть ли временная папка с изображениями
        if (tempImagesFolder != null && tempImagesFolder.exists()) {
            println("📁 Временная папка с изображениями найдена: ${tempImagesFolder.name}")
            
            // Обновляем ссылки в markdown на актуальные пути
            println("🔄 Вызываем updateMarkdownImageLinks...")
            val updatedMarkdown = processor.updateMarkdownImageLinks(content, finalFileName)
            println("📝 Markdown обновлен с актуальными путями к изображениям")

            if (saveLocationOption == SaveLocationOption.ASK_EVERY_TIME) {
                // Store pending and trigger folder picker
                println("💾 Сохраняем отложенные данные для ASK_EVERY_TIME")
                pendingFileName = finalFileName
                pendingContent = updatedMarkdown
                pendingImagesFolder = tempImagesFolder
                pendingImagesDirName = "${finalFileName}_images"
                println("   Отложенное имя файла: $pendingFileName")
                println("   Отложенный контент: ${pendingContent?.length} символов")
                println("   Отложенная папка: ${pendingImagesFolder?.absolutePath}")
                println("   Отложенное имя папки изображений: $pendingImagesDirName")
                onFolderPickerRequest()
            } else {
                // CUSTOM_FOLDER: сохранить сразу
                saveNote(
                    finalFileName,
                    updatedMarkdown,
                    saveLocationOption,
                    onFolderPickerRequest = onFolderPickerRequest,
                    onSaveResult = { success ->
                        if (!success) {
                            println("❗ Ошибка сохранения заметки")
                        } else {
                            println("✅ Заметка успешно сохранена")
                        }
                        onSaveResult(success)
                    },
                    imagesFolder = tempImagesFolder,
                    imagesDirName = "${finalFileName}_images"
                )
            }
        } else {
            println("ℹ️ Изображения не загружались, сохраняем заметку без них")
            
            // Сохраняем без изображений
            saveNote(
                finalFileName,
                content,
                saveLocationOption,
                onFolderPickerRequest = {
                    println("💾 Сохранены значения для выбора папки:")
                    println("   Файл: $finalFileName")
                    println("   Папка с изображениями: нет")
                    onFolderPickerRequest()
                },
                onSaveResult = { success ->
                    if (!success) {
                        println("❗ Ошибка сохранения заметки")
                    } else {
                        println("✅ Заметка успешно сохранена")
                    }
                    onSaveResult(success)
                },
                imagesFolder = null
            )
        }
    }

    /**
     * Определяет MIME-тип файла по расширению
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}