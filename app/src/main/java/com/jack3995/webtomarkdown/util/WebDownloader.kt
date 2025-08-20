package com.jack3995.webtomarkdown.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class WebDownloader {

    private val client = OkHttpClient()

    /**
     * Скачивает HTML содержимое по указанному URL.
     * Внимание: метод выполняется синхронно, вызывает сеть в текущем потоке.
     * Вызывайте из корутины с подходящим диспетчером (например, Dispatchers.IO).
     *
     * @param url адрес веб-страницы
     * @return HTML-код страницы как строка
     * @throws IOException при ошибках сети или пустом ответе
     */
    @Throws(IOException::class)
    fun downloadWebPage(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Ошибка HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Пустой ответ")
        }
    }
}
