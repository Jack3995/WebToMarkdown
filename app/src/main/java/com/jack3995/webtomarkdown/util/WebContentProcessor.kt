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
        val imagesFolder: File? = null
    )

    private val patternProcessor = PatternProcessor()

    fun getSupportedPatternDomains(): List<String> = patternProcessor.getSupportedDomains()

    // Конвертирует HTML в markdown с поддержкой изображений
    suspend fun convertHtmlToMarkdownWithImages(
        html: String, 
        baseUrl: String, 
        imagesFolder: File,
        usePatterns: Boolean
    ): String {
        val doc = Jsoup.parse(html)
        val root = if (usePatterns) (patternProcessor.tryExtract(baseUrl, doc) ?: doc.body()) else doc.body()
        root.select("script, style, iframe, noscript, .share-button, .like-button, button").remove()
        
        // Обрабатываем изображения
        val imageDownloader = ImageDownloader()
        val imageElements = root.select("img")
        
        println("🖼️ Найдено изображений на странице: ${imageElements.size}")
        
        for (imgElement in imageElements) {
            val src = imgElement.attr("src")
            if (src.isNotBlank()) {
                val absoluteUrl = imageDownloader.resolveImageUrl(baseUrl, src)
                println("🔗 Обрабатываем изображение: $absoluteUrl")
                
                if (imageDownloader.isImageUrl(absoluteUrl)) {
                    val altText = imgElement.attr("alt").ifBlank { "image" }
                    val fileName = generateImageFileName(absoluteUrl, altText)
                    
                    println("📥 Скачиваем изображение: $fileName")
                    
                    // Скачиваем изображение
                    val downloadResult = imageDownloader.downloadImage(absoluteUrl, imagesFolder, fileName, imagesFolder.name)
                    downloadResult.onSuccess { imageInfo ->
                        // Заменяем src на локальный путь
                        imgElement.attr("src", imageInfo.localPath)
                        println("✅ Изображение скачано: ${imageInfo.fileName}")
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
            val alt = element.attr("alt").ifBlank { "image" }
            "![$alt]($src)"
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

    // Безопасно формирует имя файла
    fun sanitizeFilename(name: String): String =
        name.replace("[\\\\/:*?\"<>|]".toRegex(), "-").trim()

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
    private fun generateImageFileName(imageUrl: String, altText: String): String {
        val sanitizedAlt = sanitizeFilename(altText).take(30)
        val timestamp = System.currentTimeMillis()
        val knownExt = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif", "heic")
        val fromUrl = imageUrl.substringAfterLast('/', imageUrl)
        val ext = fromUrl.substringAfterLast('.', "").lowercase().take(5)
        val safeExt = if (ext in knownExt) ext else "png"
        return "${sanitizedAlt}_$timestamp.$safeExt"
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
                    if (title.isNullOrBlank()) getDefaultFileName() else sanitizeFilename(title)
                }
            }
            
            val markdownBody = if (downloadImages && fileName.isNotBlank()) {
                println("🖼️ Скачивание изображений включено")
                
                // Создаем папку для изображений во временной директории
                val imagesFolder = File.createTempFile("${fileName}_images", "").parentFile?.let { parent ->
                    File(parent, "${fileName}_images")
                } ?: File("${fileName}_images")
                
                if (!imagesFolder.exists()) {
                    imagesFolder.mkdirs()
                    println("📁 Создана папка для изображений: ${imagesFolder.absolutePath}")
                } else {
                    println("📁 Папка для изображений уже существует: ${imagesFolder.absolutePath}")
                }
                
                convertHtmlToMarkdownWithImages(html, url, imagesFolder, usePatterns)
            } else {
                println("⚠️ Скачивание изображений отключено")
                convertHtmlToMarkdown(html, url, usePatterns)
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
            
            val imagesFolder = if (downloadImages && fileName.isNotBlank()) {
                File.createTempFile("${fileName}_images", "").parentFile?.let { parent ->
                    File(parent, "${fileName}_images")
                } ?: File("${fileName}_images")
            } else null
            
            Result.success(ProcessResult(markdown, fileName, imagesFolder))
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
}
