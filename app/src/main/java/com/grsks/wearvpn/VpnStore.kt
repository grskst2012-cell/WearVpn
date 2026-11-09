package com.grsks.wearvpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnStatus { OFF, CONNECTING, ON, ERROR }

/** Состояние VPN, общее для сервиса и UI (оба живут в одном процессе). */
object VpnState {
    private val _status = MutableStateFlow(VpnStatus.OFF)
    val status: StateFlow<VpnStatus> = _status

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    fun set(status: VpnStatus, message: String = "") {
        _status.value = status
        _message.value = message
    }
}

/** Хранит vless-ссылку. */
object LinkStore {
    private const val PREFS = "wearvpn"
    private const val KEY = "link"

    fun get(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** Проверяет ссылку и сохраняет. Бросает исключение, если она неверная. */
    fun set(ctx: Context, link: String): VlessLink {
        val parsed = VlessConfig.parse(link)
        VlessConfig.build(parsed) // заодно проверим, что конфиг собирается
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, link.trim()).apply()
        return parsed
    }
}
