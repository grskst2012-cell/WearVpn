package com.grsks.wearvpn

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Разобранная ссылка vless://uuid@host:port?параметры#имя */
data class VlessLink(
    val uuid: String,
    val host: String,
    val port: Int,
    val name: String,
    val params: Map<String, String>,
) {
    fun p(key: String): String? = params[key]?.takeIf { it.isNotEmpty() }
}

object VlessConfig {

    fun parse(raw: String): VlessLink {
        val text = raw.trim()
        require(text.startsWith("vless://", ignoreCase = true)) { "Ссылка должна начинаться с vless://" }
        val uri = URI(text.replace(" ", "%20"))
        val uuid = uri.rawUserInfo?.let { dec(it) } ?: error("В ссылке нет UUID")
        val host = uri.host?.trim('[', ']') ?: error("В ссылке нет адреса сервера")
        val port = if (uri.port > 0) uri.port else 443
        val name = uri.rawFragment?.let { dec(it) }?.takeIf { it.isNotBlank() } ?: host
        val params = HashMap<String, String>()
        uri.rawQuery?.split('&')?.forEach { kv ->
            if (kv.isEmpty()) return@forEach
            val i = kv.indexOf('=')
            if (i < 0) params[dec(kv)] = "" else params[dec(kv.substring(0, i))] = dec(kv.substring(i + 1))
        }
        return VlessLink(uuid, host, port, name, params)
    }

    /** Готовый JSON для Xray: tun-inbound (fd отдаётся из VpnService) + VLESS-outbound. */
    fun build(link: VlessLink, tun: Boolean = true, proxyPort: Int = 10809): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put(
            "dns",
            JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIPv4"),
        )
        val inbound = if (tun) {
            JSONObject()
                .put("tag", "tun-in")
                .put("protocol", "tun")
                .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500))
        } else {
            JSONObject()
                .put("tag", "http-in")
                .put("protocol", "http")
                .put("listen", "127.0.0.1")
                .put("port", proxyPort)
                .put("settings", JSONObject())
        }
        inbound.put(
            "sniffing",
            JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
        )
        val inbounds = JSONArray().put(inbound)
        if (!tun) {
            // SOCKS5 для приложений со своим прокси (Telegram): 127.0.0.1:proxyPort-1
            inbounds.put(
                JSONObject()
                    .put("tag", "socks-in")
                    .put("protocol", "socks")
                    .put("listen", "127.0.0.1")
                    .put("port", proxyPort - 1)
                    .put("settings", JSONObject().put("udp", true)),
            )
        }
        root.put("inbounds", inbounds)
        root.put(
            "outbounds",
            JSONArray()
                .put(outbound(link))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom")),
        )
        return root.toString()
    }

    private fun outbound(l: VlessLink): JSONObject {
        val user = JSONObject()
            .put("id", l.uuid)
            .put("encryption", l.p("encryption") ?: "none")
        l.p("flow")?.let { user.put("flow", it) }

        val out = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", l.host)
                            .put("port", l.port)
                            .put("users", JSONArray().put(user)),
                    ),
                ),
            )

        val network = when (val t = l.p("type") ?: "tcp") {
            "h2" -> "http"
            else -> t
        }
        val security = l.p("security") ?: "none"
        val ss = JSONObject().put("network", network).put("security", security)

        when (security) {
            "tls" -> ss.put(
                "tlsSettings",
                JSONObject().apply {
                    put("serverName", l.p("sni") ?: l.p("host") ?: l.host)
                    l.p("fp")?.let { put("fingerprint", it) }
                    l.p("alpn")?.let { put("alpn", JSONArray(it.split(','))) }
                    if (l.p("allowInsecure") == "1") put("allowInsecure", true)
                },
            )
            "reality" -> ss.put(
                "realitySettings",
                JSONObject().apply {
                    put("serverName", l.p("sni") ?: l.host)
                    put("fingerprint", l.p("fp") ?: "chrome")
                    put("publicKey", l.p("pbk") ?: error("В ссылке Reality нет pbk (public key)"))
                    put("shortId", l.p("sid") ?: "")
                    put("spiderX", l.p("spx") ?: "")
                },
            )
        }

        when (network) {
            "ws" -> ss.put(
                "wsSettings",
                JSONObject().apply {
                    put("path", l.p("path") ?: "/")
                    l.p("host")?.let { put("headers", JSONObject().put("Host", it)) }
                },
            )
            "grpc" -> ss.put(
                "grpcSettings",
                JSONObject()
                    .put("serviceName", l.p("serviceName") ?: "")
                    .put("multiMode", l.p("mode") == "multi"),
            )
            "httpupgrade" -> ss.put(
                "httpupgradeSettings",
                JSONObject().apply {
                    put("path", l.p("path") ?: "/")
                    l.p("host")?.let { put("host", it) }
                },
            )
            "xhttp", "splithttp" -> ss.put(
                "xhttpSettings",
                JSONObject().apply {
                    put("path", l.p("path") ?: "/")
                    l.p("host")?.let { put("host", it) }
                    l.p("mode")?.let { put("mode", it) }
                },
            )
            "tcp" -> if (l.p("headerType") == "http") {
                ss.put(
                    "tcpSettings",
                    JSONObject().put(
                        "header",
                        JSONObject().put("type", "http").put(
                            "request",
                            JSONObject()
                                .put("path", JSONArray().put(l.p("path") ?: "/"))
                                .put(
                                    "headers",
                                    JSONObject().put("Host", JSONArray().put(l.p("host") ?: l.host)),
                                ),
                        ),
                    ),
                )
            }
        }
        out.put("streamSettings", ss)
        return out
    }

    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
