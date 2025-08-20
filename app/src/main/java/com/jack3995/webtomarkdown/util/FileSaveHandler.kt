package com.jack3995.webtomarkdown.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.jack3995.webtomarkdown.screens.SaveLocationOption
import java.io.File

class FileSaveHandler(private val context: Context, private val contentResolver: ContentResolver) {

    var lastCustomFolderUri: String? = null

    /**
     * Сохраняет заметку в зависимости от режима сохранения.
     * Если нужно, вызывает onFolderPickerRequest для выбора папки.
     * Сообщает результат через onSaveResult.
     */
    fun saveNote(
        fileName: String,
        content: String,
        saveLocationOption: SaveLocationOption,
        onFolderPickerRequest: () -> Unit,
        onSaveResult: (Boolean) -> Unit
    ) {
        when (saveLocationOption) {
            SaveLocationOption.ASK_EVERY_TIME -> {
                // Запросить у UI выбрать папку
                onFolderPickerRequest()
            }
            SaveLocationOption.DOWNLOADS -> {
                val dir = getDownloadsDirectory()
                val result = saveToFileCustomDir(dir, fileName, content)
                onSaveResult(result)
            }
            SaveLocationOption.CUSTOM_FOLDER -> {
                val uriString = lastCustomFolderUri
                if (!uriString.isNullOrBlank()) {
                    val uri = uriString.toUri()
                    val result = saveNoteToSAF(uri, fileName, content)
                    onSaveResult(result)
                } else {
                    // Папка не задана - запрос выбора папки
                    onFolderPickerRequest()
                }
            }
        }
    }

    /**
     * Метод для вызова из UI-обработчика выбора папки.
     * Сохраняет файл и обновляет lastCustomFolderUri.
     * Сообщает результат через onSaveResult.
     */
    fun onFolderPicked(folderUri: Uri?, fileName: String, content: String, onSaveResult: (Boolean) -> Unit) {
        if (folderUri == null) {
            onSaveResult(false)
            return
        }
        lastCustomFolderUri = folderUri.toString()
        val result = saveNoteToSAF(folderUri, fileName, content)
        onSaveResult(result)
    }

    fun saveNoteToSAF(folderUri: Uri, fileName: String, content: String): Boolean {
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        val safeFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
        val newFile = pickedDir.createFile("text/markdown", safeFileName) ?: return false

        contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(content.toByteArray())
        } ?: return false

        println("📁 Файл сохранён через SAF в: ${newFile.uri}")
        return true
    }

    fun saveToFileCustomDir(dir: File, fileName: String, content: String): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            println("📁 Файл сохранён локально: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}
