package com.jack3995.webtomarkdown.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebContentProcessor {

    data class ProcessResult(
        val markdown: String, 
        val fileName: String,
        val baseUrl: String,
        val tempImagesFolder: File? = null
    )

    private val patternProcessor = PatternProcessor()

    fun getSupportedPatternDomains(): List<String> = patternProcessor.getSupportedDomains()

    // Конвертирует HTML в markdown с поддержкой изображений
    suspend fun convertHtmlToMarkdownWithImages(
        html: String, 
        baseUrl: String, 
        imagesFolder: File,
        usePatterns: Boolean,
        noteName: String? = null
    ): String {
        val doc = Jsoup.parse(html)
        val root = if (usePatterns) (patternProcessor.tryExtract(baseUrl, doc) ?: doc.body()) else doc.body()
        root.select("script, style, iframe, noscript, .share-button, .like-button, button").remove()
        
        // Обрабатываем изображения
        val imageDownloader = ImageDownloader()
        val imageElements = root.select("img")
        
        println("🖼️ Найдено изображений на странице: ${imageElements.size}")
        
        var imageCounter = 1
        for (imgElement in imageElements) {
            val src = imgElement.attr("src")
            if (src.isNotBlank()) {
                val absoluteUrl = imageDownloader.resolveImageUrl(baseUrl, src)
                println("🔗 Обрабатываем изображение: $absoluteUrl")
                
                if (imageDownloader.isImageUrl(absoluteUrl)) {
                    val altText = imgElement.attr("alt").ifBlank { "image" }
                    val fileName = generateImageFileName(absoluteUrl, altText, noteName, imageCounter)
                    
                    println("📥 Скачиваем изображение: $fileName")
                    
                    // Скачиваем изображение
                    val downloadResult = imageDownloader.downloadImage(absoluteUrl, imagesFolder, fileName, imagesFolder.name)
                    downloadResult.onSuccess { imageInfo ->
                        // Заменяем src на локальный путь
                        imgElement.attr("src", imageInfo.localPath)
                        println("✅ Изображение скачано: ${imageInfo.fileName}")
                        imageCounter++
                    }.onFailure { error ->
                        println("❌ Ошибка скачивания изображения $absoluteUrl: ${error.message}")
                        // Оставляем оригинальный URL
                    }
                } else {
                    println("⚠️ URL не является изображением: $absoluteUrl")
                }
            }
        }
        
        val sb = StringBuilder()

        for (element in root.children()) {
            sb.append(elementToMarkdown(element))
            sb.append("\n\n")
        }

        return sb.toString().trim()
    }

    // Конвертирует HTML в markdown, фильтруя лишние элементы (старая версия без изображений)
    fun convertHtmlToMarkdown(html: String, baseUrl: String, usePatterns: Boolean): String {
        val doc = Jsoup.parse(html)
        val root = if (usePatterns) (patternProcessor.tryExtract(baseUrl, doc) ?: doc.body()) else doc.body()
        root.select("script, style, iframe, noscript, .share-button, .like-button, button").remove()
        val sb = StringBuilder()

        for (element in root.children()) {
            sb.append(elementToMarkdown(element))
            sb.append("\n\n")
        }

        return sb.toString().trim()
    }

    // Рекурсивно преобразует HTML элемент в markdown
    // ВАЖНО: не терять собственный текст узла при наличии дочерних элементов
    private fun elementToMarkdown(element: Element): String = when (element.tagName().lowercase()) {
        "h1" -> "# ${element.text()}"
        "h2" -> "## ${element.text()}"
        "h3" -> "### ${element.text()}"
        "h4" -> "#### ${element.text()}"
        "h5" -> "##### ${element.text()}"
        "h6" -> "###### ${element.text()}"
        "p" -> element.text()
        // inline code
        "code" -> {
            val parentTag = element.parent()?.tagName()?.lowercase()
            if (parentTag == "pre") {
                // handled in pre
                ""
            } else {
                "`${element.text()}`"
            }
        }
        // preformatted block code
        "pre" -> buildString {
            val insideCode = element.selectFirst("code")
            val raw = insideCode?.wholeText()?.ifBlank { insideCode.text() }
                ?: element.wholeText().ifBlank { element.text() }
            
            // Извлекаем язык программирования
            val language = insideCode?.let { extractProgrammingLanguage(it) } 
                ?: extractProgrammingLanguage(element)
            
            append("```")
            if (!language.isNullOrBlank()) {
                append(language)
                println("🎯 Обнаружен язык программирования: $language")
            }
            append("\n")
            append(raw.trimEnd())
            append("\n```")
        }
        // div и span могут содержать и собственный текст, и вложенные элементы — собираем всё
        "div", "span", "section", "article" -> buildString {
            val own = element.ownText().trim()
            if (own.isNotEmpty()) append(own)
            for (child in element.children()) {
                val childMd = elementToMarkdown(child)
                if (childMd.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(childMd)
                }
            }
        }
        "ul" -> element.children().joinToString("\n") { child -> "- ${child.text()}" }
        "ol" -> element.children().mapIndexed { index, li -> "${index + 1}. ${li.text()}" }.joinToString("\n")
        "a" -> "[${element.text()}](${element.attr("href")})"
        "b", "strong" -> "**${element.text()}**"
        "i", "em" -> "*${element.text()}*"
        "img" -> {
            val src = element.attr("src")
            "![[$src]]"
        }
        "blockquote" -> "> [!NOTE]\n" + element.text().lines().joinToString("\n") { "> $it" }
        "br" -> ""
        else -> buildString {
            val own = element.ownText().trim()
            if (own.isNotEmpty()) append(own)
            for (child in element.children()) {
                val childMd = elementToMarkdown(child)
                if (childMd.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(childMd)
                }
            }
            if (isEmpty()) append(element.text())
        }
    }

    // Мягкая очистка для имен заметок - только запрещенные символы Windows/Android
    fun sanitizeNoteName(name: String): String {
        return name
            .replace("[\\\\/:*?\"<>|]".toRegex(), "-") // Удаляем только запрещенные символы Windows/Android
            .trim()
    }
    
    // Строгая очистка для имен изображений - с заменой скобок и пробелов
    fun sanitizeImageName(name: String): String {
        return name
            .replace("\\s+".toRegex(), "_") // Заменяем пробелы на подчеркивания
            .replace("\\[\\[".toRegex(), "((") // Заменяем [[ на ((
            .replace("\\]\\]".toRegex(), "))") // Заменяем ]] на ))
            .replace("\\[".toRegex(), "(") // Заменяем одиночные [ на (
            .replace("\\]".toRegex(), ")") // Заменяем одиночные ] на )
            .replace("[\\\\/:*?\"<>|]".toRegex(), "-") // Удаляем запрещенные символы Windows/Android
            .trim()
    }

    // Извлекает заголовок из HTML
    fun extractTitle(html: String): String? = try {
        Jsoup.parse(html).title()
    } catch (_: Exception) {
        null
    }

    // Генерация имени файла по умолчанию с датой и временем
    fun getDefaultFileName(): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH.mm", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        return "Заметка_$currentDate"
    }
    
    // Генерирует имя файла для изображения
    private fun generateImageFileName(imageUrl: String, altText: String, noteName: String? = null, imageCounter: Int = 1): String {
        val sanitizedAlt = sanitizeImageName(altText)
        val knownExt = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif", "heic")
        val fromUrl = imageUrl.substringAfterLast('/', imageUrl)
        val ext = fromUrl.substringAfterLast('.', "").lowercase().take(5)
        val safeExt = if (ext in knownExt) ext else "png"
        
        // Если altText после очистки пустой или слишком короткий, используем fallback
        val finalName = if (sanitizedAlt.isBlank() || sanitizedAlt.length < 3) {
            if (noteName != null) {
                val cleanNoteName = sanitizeImageName(noteName).take(20)
                "${cleanNoteName}_${imageCounter}"
            } else {
                "image_${imageCounter}"
            }
        } else {
            sanitizedAlt.take(30)
        }
        
        return "$finalName.$safeExt"
    }

    // Основная функция обработки страницы по url и опции имени файла
    // Возвращает Result с ProcessResult или ошибкой
    suspend fun processPage(
        url: String,
        fileNameOption: com.jack3995.webtomarkdown.screens.FileNameOption,
        downloadImages: Boolean = true,
        usePatterns: Boolean = true,
        customFileName: String? = null
    ): Result<ProcessResult> = withContext(Dispatchers.IO) {
        try {
            val html = WebDownloader().downloadWebPage(url)
            val fileName = customFileName?.takeIf { it.isNotBlank() } ?: when (fileNameOption) {
                com.jack3995.webtomarkdown.screens.FileNameOption.DEFAULT_NAME -> getDefaultFileName()
                com.jack3995.webtomarkdown.screens.FileNameOption.PAGE_TITLE -> {
                    val title = extractTitle(html)
                    if (title.isNullOrBlank()) getDefaultFileName() else sanitizeNoteName(title)
                }
            }
            
            val (markdownBody, tempImagesFolder) = if (downloadImages && fileName.isNotBlank()) {
                println("🖼️ WebContentProcessor: Скачивание изображений включено")
                
                // Создаем временную папку "temp-folder-for-images" в системной временной директории
                val tempFolder = File.createTempFile("temp-folder-for-images", "").parentFile?.let { parent ->
                    File(parent, "temp-folder-for-images")
                } ?: File("temp-folder-for-images")
                
                if (!tempFolder.exists()) {
                    tempFolder.mkdirs()
                    println("📁 WebContentProcessor: Создана временная папка для изображений: ${tempFolder.absolutePath}")
                }
                
                // Скачиваем изображения во временную папку
                val markdown = convertHtmlToMarkdownWithImages(html, url, tempFolder, usePatterns, fileName)
                
                // Проверяем, есть ли изображения в папке
                val imageFiles = tempFolder.listFiles()?.filter { it.isFile } ?: emptyList()
                val finalTempFolder = if (imageFiles.isNotEmpty()) {
                    println("📁 WebContentProcessor: Временная папка с изображениями создана: ${tempFolder.name}")
                    println("🖼️ WebContentProcessor: Найдено изображений: ${imageFiles.size}")
                    tempFolder
                } else {
                    println("📁 WebContentProcessor: Временная папка пуста, изображения не загрузились")
                    println("🗑️ WebContentProcessor: Удаляем пустую временную папку")
                    tempFolder.deleteRecursively()
                    null
                }
                
                Pair(markdown, finalTempFolder)
            } else {
                println("⚠️ WebContentProcessor: Скачивание изображений отключено")
                val markdown = convertHtmlToMarkdown(html, url, usePatterns)
                Pair(markdown, null)
            }

            val markdown = buildString {
                append(markdownBody)
                append("\n\n—\nИсточник: ")
                append("[")
                append(url)
                append("](")
                append(url)
                append(")")
            }
            
            Result.success(ProcessResult(markdown, fileName, url, tempImagesFolder))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Извлекает язык программирования из HTML элемента
    private fun extractProgrammingLanguage(element: Element): String? {
        // Проверяем class атрибут элемента code
        val codeClass = element.attr("class").lowercase()
        if (codeClass.isNotBlank()) {
            // Ищем язык в class (например, "java", "language-java", "highlight-java")
            val knownLanguages = setOf(
                "java", "kotlin", "javascript", "js", "python", "py", "cpp", "c++", "c", 
                "csharp", "c#", "php", "ruby", "go", "rust", "swift", "typescript", "ts",
                "html", "css", "xml", "json", "yaml", "yml", "sql", "bash", "shell", "sh"
            )
            
            for (lang in knownLanguages) {
                if (codeClass.contains(lang)) {
                    return when (lang) {
                        "js" -> "javascript"
                        "py" -> "python"
                        "c++" -> "cpp"
                        "c#" -> "csharp"
                        "ts" -> "typescript"
                        "yml" -> "yaml"
                        "sh" -> "bash"
                        else -> lang
                    }
                }
            }
            
            // Если точного совпадения нет, ищем паттерны
            when {
                codeClass.contains("language-") -> {
                    val lang = codeClass.substringAfter("language-").split(" ")[0]
                    if (lang.isNotBlank()) return lang
                }
                codeClass.contains("highlight-") -> {
                    val lang = codeClass.substringAfter("highlight-").split(" ")[0]
                    if (lang.isNotBlank()) return lang
                }
            }
        }
        
        // Проверяем data-lang или data-language атрибуты
        element.attr("data-lang").takeIf { it.isNotBlank() }?.let { return it }
        element.attr("data-language").takeIf { it.isNotBlank() }?.let { return it }
        
        return null
    }

    /**
     * Создаёт папку с изображениями с актуальным именем и копирует изображения из временной папки
     */
    suspend fun createFinalImagesFolderAndCopyImages(
        tempImagesFolder: File,
        actualFileName: String,
        targetDirectory: File
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Создаём папку с правильным именем в целевой директории
            val finalImagesFolder = File(targetDirectory, "${actualFileName}_images")
            if (!finalImagesFolder.exists()) {
                finalImagesFolder.mkdirs()
                println("📁 Создана финальная папка для изображений: ${finalImagesFolder.absolutePath}")
            }
            
            // Копируем все изображения из временной папки
            val imageFiles = tempImagesFolder.listFiles()
            if (imageFiles.isNullOrEmpty()) {
                println("⚠️ Временная папка с изображениями пуста")
                return@withContext finalImagesFolder
            }
            
            println("🔄 Копируем ${imageFiles.size} изображений в финальную папку")
            
            imageFiles.forEach { imageFile ->
                if (imageFile.isFile) {
                    val targetFile = File(finalImagesFolder, imageFile.name)
                    imageFile.copyTo(targetFile, overwrite = true)
                    println("✅ Скопировано изображение: ${imageFile.name}")
                }
            }
            
            // Удаляем временную папку
            tempImagesFolder.deleteRecursively()
            println("🗑️ Временная папка удалена")
            
            finalImagesFolder
        } catch (e: Exception) {
            println("❌ Ошибка при создании финальной папки: ${e.message}")
            null
        }
    }
}