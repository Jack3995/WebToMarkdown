package com.jack3995.webtomarkdown.util

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import java.net.URI

/**
 * Processor that holds site-specific patterns and extracts the root content element.
 */
class PatternProcessor {

    interface SitePattern {
        fun canHandle(url: String): Boolean
        fun extract(doc: Document, url: String): Element?
        val name: String
        val domains: List<String>
    }

    private val patterns: List<SitePattern> = listOf(
        QnaHabrPattern(),
        HabrPattern(),
        PikabuPattern(),
        IxbtPattern()
    )

    fun tryExtract(url: String, doc: Document): Element? {
        val pattern = patterns.firstOrNull { it.canHandle(url) }
        if (pattern != null) {
            println("🎯 Применяем паттерн для сайта: ${pattern.name}")
            return try {
                pattern.extract(doc, url)
            } catch (e: Exception) {
                println("⚠️ Ошибка работы паттерна ${pattern.name}: ${e.message}")
                null
            }
        }
        println("🌐 Паттерн не найден, используем базовый алгоритм")
        return null
    }

    fun getSupportedDomains(): List<String> = patterns.flatMap { it.domains }.distinct()

    private class HabrPattern : SitePattern {
        override val name: String = "Habr"
        override val domains: List<String> = listOf("habr.com")
        override fun canHandle(url: String): Boolean = try { URI(url).host?.lowercase() == "habr.com" } catch (_: Exception) { false }
        override fun extract(doc: Document, url: String): Element? = doc.selectFirst("#post-content-body")
    }

    private class PikabuPattern : SitePattern {
        override val name: String = "Pikabu"
        override val domains: List<String> = listOf("pikabu.ru")
        override fun canHandle(url: String): Boolean = try { URI(url).host?.lowercase() == "pikabu.ru" } catch (_: Exception) { false }
        override fun extract(doc: Document, url: String): Element? = doc.selectFirst(".story__content-inner")
    }

    private class IxbtPattern : SitePattern {
        override val name: String = "iXBT"
        override val domains: List<String> = listOf("ixbt.com", "www.ixbt.com")
        override fun canHandle(url: String): Boolean = try {
            val host = URI(url).host?.lowercase()
            host == "ixbt.com" || host == "www.ixbt.com"
        } catch (_: Exception) { false }
        override fun extract(doc: Document, url: String): Element? =
            doc.selectFirst("div.b-article__content[itemprop=articleBody]#main-pagecontent__div")
    }

    // qna.habr.com pattern: собираем HTML согласно шагам
    private class QnaHabrPattern : SitePattern {
        override val name: String = "Habr Q&A"
        override val domains: List<String> = listOf("qna.habr.com")
        override fun canHandle(url: String): Boolean = try { URI(url).host?.lowercase() == "qna.habr.com" } catch (_: Exception) { false }
        override fun extract(doc: Document, url: String): Element? {
            return try {
                val container = Element(Tag.valueOf("div"), "").attr("data-source", name)

                doc.selectFirst("div.question__text.js-question-text[itemprop='text description']")?.let { q ->
                    container.appendChild(q.clone())
                }
                container.appendElement("br") 

                val answers = doc.select("div.answer__text.js-answer-text[itemprop='text']")
                
                for (answer in answers) {
                    
                    // Find author/user summary near the answer
                    val userSummary = answer.parents().select("span.user-summary__nickname").first()
                    if (userSummary != null) {
                        println("👤 Найден автор: ${userSummary.text().trim()}")
                        val strong = Element(Tag.valueOf("strong"), "").text(userSummary.text().trim())
                        container.appendChild(strong)
                    }

                    // Clone the answer element to preserve HTML structure (including blockquote tags)
                    val clonedAnswer = answer.clone()
                    container.appendChild(clonedAnswer)
                    container.appendElement("br")
                }

                container
            } catch (_: Exception) {
                null
            }
        }
    }
}


