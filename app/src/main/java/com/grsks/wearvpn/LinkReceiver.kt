package com.grsks.wearvpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * Принимает через adb либо одиночную vless://-ссылку, либо ссылку на подписку (https://...).
 * Для подписки можно указать номер сервера: --ei index N (по умолчанию 0).
 * Ответ (виден в выводе adb) содержит нумерованный список серверов.
 */
class LinkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val link = intent.getStringExtra("link")?.trim() ?: return
        val index = intent.getIntExtra("index", 0)
        val pending = goAsync()
        thread(name = "link-import") {
            val result = try {
                if (link.startsWith("vless://", ignoreCase = true)) {
                    "OK " + LinkStore.set(context, link).name
                } else if (link.startsWith("http://") || link.startsWith("https://")) {
                    importSubscription(context, link, index)
                } else {
                    error("Нужна ссылка vless:// или https:// (подписка)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Импорт не удался: ${e.message}")
                "ERROR ${e.message}"
            }
            Log.i(TAG, result.lines().first())
            pending.resultData = result
            pending.finish()
        }
    }

    private fun importSubscription(context: Context, url: String, index: Int): String {
        val links = Subscription.fetch(url)
        val names = links.map { runCatching { VlessConfig.parse(it).name }.getOrDefault("?") }
        if (index !in links.indices) error("Нет сервера с номером $index. Всего серверов: ${links.size}")
        LinkStore.set(context, links[index])
        return buildString {
            append("OK выбран [$index] ${names[index]}\n")
            names.forEachIndexed { i, n -> append("  [$i] $n\n") }
        }
    }

    private companion object {
        const val TAG = "WearVpn"
    }
}
