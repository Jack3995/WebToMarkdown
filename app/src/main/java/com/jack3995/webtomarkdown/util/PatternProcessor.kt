package com.jack3995.webtomarkdown.util

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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
        override fun canHandle(url: String): Boolean = url.contains("habr.com")
        override fun extract(doc: Document, url: String): Element? = doc.selectFirst("#post-content-body")
    }

    private class PikabuPattern : SitePattern {
        override val name: String = "Pikabu"
        override val domains: List<String> = listOf("pikabu.ru")
        override fun canHandle(url: String): Boolean = url.contains("pikabu.ru")
        override fun extract(doc: Document, url: String): Element? = doc.selectFirst(".story__content-inner")
    }

    private class IxbtPattern : SitePattern {
        override val name: String = "iXBT"
        override val domains: List<String> = listOf("ixbt.com", "www.ixbt.com")
        override fun canHandle(url: String): Boolean = url.contains("ixbt.com") || url.contains("www.ixbt.com")
        override fun extract(doc: Document, url: String): Element? =
            doc.selectFirst("div.b-article__content[itemprop=articleBody]#main-pagecontent__div")
    }
}


