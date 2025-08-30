package com.jack3995.webtomarkdown.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageDownloader {

    private val client = OkHttpClient()

    data class ImageInfo(
        val originalUrl: String,
        val localPath: String,
        val fileName: String
    )

    /**
     * Скачивает изображение по URL и сохраняет в указанную папку
     */
    suspend fun downloadImage(
        imageUrl: String,
        targetFolder: File,
        fileName: String,
        folderName: String? = null
    ): Result<ImageInfo> = withContext(Dispatchers.IO) {
        try {
            println("🔄 Начинаем скачивание: $imageUrl")
            
            // Создаем папку, если она не существует
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
                println("📁 Создана папка: ${targetFolder.absolutePath}")
            }

            // Получаем расширение файла из URL
            val extension = getImageExtension(imageUrl)
            val fullFileName = if (fileName.contains(".")) fileName else "$fileName.$extension"
            val imageFile = File(targetFolder, fullFileName)

            println("📄 Сохраняем в файл: ${imageFile.absolutePath}")

            // Скачиваем изображение
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("❌ HTTP ошибка ${response.code} для $imageUrl")
                    return@withContext Result.failure(IOException("Ошибка HTTP ${response.code} при скачивании $imageUrl"))
                }

                val body = response.body
                if (body == null) {
                    println("❌ Пустой ответ для $imageUrl")
                    return@withContext Result.failure(IOException("Пустой ответ при скачивании $imageUrl"))
                }
                
                // Сохраняем файл
                val bytes = body.bytes()
                imageFile.writeBytes(bytes)
                println("💾 Сохранено ${bytes.size} байт в ${imageFile.name}")
            }

            val imageInfo = ImageInfo(
                originalUrl = imageUrl,
                localPath = if (folderName != null) "./$folderName/$fullFileName" else fullFileName, // Относительный путь для Markdown
                fileName = fullFileName
            )

            println("✅ Успешно скачано: ${imageInfo.fileName}")
            Result.success(imageInfo)
        } catch (e: Exception) {
            println("❌ Ошибка скачивания $imageUrl: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Извлекает расширение файла из URL изображения
     */
    private fun getImageExtension(url: String): String {
        return try {
            val urlObj = URL(url)
            val path = urlObj.path
            val lastDotIndex = path.lastIndexOf('.')
            if (lastDotIndex > 0) {
                path.substring(lastDotIndex + 1).lowercase()
            } else {
                "jpg" // По умолчанию
            }
        } catch (_: Exception) {
            "jpg" // По умолчанию
        }
    }

    /**
     * Проверяет, является ли URL изображением
     */
    fun isImageUrl(url: String): Boolean {
        val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
        val extension = getImageExtension(url).lowercase()
        return imageExtensions.contains(extension)
    }

    /**
     * Преобразует относительный URL в абсолютный
     */
    fun resolveImageUrl(baseUrl: String, imageUrl: String): String {
        return try {
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                imageUrl
            } else if (imageUrl.startsWith("//")) {
                "https:$imageUrl"
            } else if (imageUrl.startsWith("/")) {
                val base = URL(baseUrl)
                "${base.protocol}://${base.host}$imageUrl"
            } else {
                val base = URL(baseUrl)
                val basePath = base.path.substringBeforeLast("/")
                "${base.protocol}://${base.host}$basePath/$imageUrl"
            }
        } catch (_: Exception) {
            imageUrl
        }
    }
}
