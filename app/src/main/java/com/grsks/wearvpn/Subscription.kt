package com.grsks.wearvpn

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Подписка: https-ссылка, по которой отдаётся список серверов (обычно base64 из строк vless://). */
object Subscription {

    /** Достаёт vless://-ссылки из ответа подписки: открытым текстом или в base64. */
    fun extractLinks(body: String): List<String> {
        val text = body.trim()
        val plain = findLinks(text)
        if (plain.isNotEmpty()) return plain
        val decoded = runCatching { String(decodeBase64(text), Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return findLinks(decoded)
    }

    private fun findLinks(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("vless://", ignoreCase = true) }
            .toList()

    private fun decodeBase64(s: String): ByteArray {
        val clean = s.filterNot { it.isWhitespace() }
        val decoder = if (clean.contains('-') || clean.contains('_')) Base64.getUrlDecoder() else Base64.getDecoder()
        // Base64 без "=" в конце тоже встречается
        return decoder.decode(clean.trimEnd('=') .let { it + "=".repeat((4 - it.length % 4) % 4) })
    }

    /** Скачивает подписку. Вызывать не из главного потока. */
    fun fetch(url: String): List<String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "WearVpn/0.1")
        try {
            if (conn.responseCode !in 200..299) error("Сервер подписки ответил ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val links = extractLinks(body)
            if (links.isEmpty()) error("В подписке нет серверов vless:// (возможно, провайдер отдаёт их только своему приложению)")
            return links
        } finally {
            conn.disconnect()
        }
    }
}
