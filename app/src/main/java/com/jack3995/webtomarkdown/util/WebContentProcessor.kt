package com.jack3995.webtomarkdown.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class WebContentProcessor {

    // Конвертирует HTML в markdown, фильтруя лишние элементы
    fun convertHtmlToMarkdown(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, iframe, noscript, .share-button, .like-button, button").remove()

        val body = doc.body()
        val sb = StringBuilder()
        if (body != null) {
            for (element in body.children()) {
                sb.append(elementToMarkdown(element))
                sb.append("\n\n")
            }
        } else {
            sb.append("Нет содержимого")
        }
        return sb.toString().trim()
    }

    // Рекурсивно преобразует HTML элемент в markdown
    private fun elementToMarkdown(element: Element): String = when (element.tagName().lowercase()) {
        "h1" -> "# ${element.text()}"
        "h2" -> "## ${element.text()}"
        "h3" -> "### ${element.text()}"
        "h4" -> "#### ${element.text()}"
        "h5" -> "##### ${element.text()}"
        "h6" -> "###### ${element.text()}"
        "p" -> element.text()
        "ul" -> element.children().joinToString("\n") { "- ${it.text()}" }
        "ol" -> element.children().mapIndexed { i, li -> "${i + 1}. ${li.text()}" }.joinToString("\n")
        "a" -> "[${element.text()}](${element.attr("href")})"
        "b", "strong" -> "**${element.text()}**"
        "i", "em" -> "*${element.text()}*"
        else -> if (element.children().isNotEmpty()) element.children().joinToString("\n") { elementToMarkdown(it) } else element.text()
    }

    // Безопасно формирует имя файла
    fun sanitizeFilename(name: String): String =
        name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()

    // Извлекает заголовок из HTML
    fun extractTitle(html: String): String? = try {
        Jsoup.parse(html).title()
    } catch (_: Exception) {
        null
    }
}
