package com.jack3995.webtomarkdown.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import java.io.File

class FileSaveHandler(private val context: Context, private val contentResolver: ContentResolver) {

    var lastCustomFolderUri: String? = null

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
        imagesFolder: File? = null
    ) {
        when (saveLocationOption) {
            SaveLocationOption.ASK_EVERY_TIME -> onFolderPickerRequest()
            SaveLocationOption.CUSTOM_FOLDER -> {
                val uriStr = lastCustomFolderUri
                if (!uriStr.isNullOrBlank()) {
                    val uri = uriStr.toUri()
                    val success = saveNoteToSAF(uri, fileName, content, imagesFolder)
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
    fun onFolderPicked(folderUri: Uri?, fileName: String, content: String, onSaveResult: (Boolean) -> Unit, imagesFolder: File? = null) {
        if (folderUri == null) {
            onSaveResult(false)
            return
        }
        lastCustomFolderUri = folderUri.toString()
        val success = saveNoteToSAF(folderUri, fileName, content, imagesFolder)
        onSaveResult(success)
    }

    private fun saveNoteToSAF(folderUri: Uri, fileName: String, content: String, imagesFolder: File? = null): Boolean {
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        val safeFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
        val newFile = pickedDir.createFile("text/markdown", safeFileName) ?: return false

        contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(content.toByteArray())
        } ?: return false

        println("📁 Файл сохранён через SAF в: ${newFile.uri}")
        
        // Сохраняем папку с изображениями, если она есть
        if (imagesFolder != null && imagesFolder.exists()) {
            saveImagesFolderToSAF(pickedDir, imagesFolder)
        }
        
        return true
    }




    
    /**
     * Копирует папку с изображениями через SAF
     */
    private fun saveImagesFolderToSAF(pickedDir: DocumentFile, imagesFolder: File) {
        try {
            println("🖼️ Начинаем сохранение папки с изображениями: ${imagesFolder.absolutePath}")
            
            // Создаем папку для изображений
            val imagesDir = pickedDir.createDirectory(imagesFolder.name) ?: run {
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
