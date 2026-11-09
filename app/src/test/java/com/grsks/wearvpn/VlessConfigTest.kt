package com.grsks.wearvpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessConfigTest {

    @Test
    fun reality() {
        val l = VlessConfig.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
                "?type=tcp&security=reality&pbk=PUBKEY&sid=ab12&sni=www.microsoft.com&fp=chrome" +
                "&flow=xtls-rprx-vision&spx=%2F#My%20Server",
        )
        assertEquals("My Server", l.name)
        assertEquals(443, l.port)
        val out = JSONObject(VlessConfig.build(l)).getJSONArray("outbounds").getJSONObject(0)
        val ss = out.getJSONObject("streamSettings")
        assertEquals("reality", ss.getString("security"))
        assertEquals("PUBKEY", ss.getJSONObject("realitySettings").getString("publicKey"))
        assertEquals("/", ss.getJSONObject("realitySettings").getString("spiderX"))
        val user = out.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("xtls-rprx-vision", user.getString("flow"))
    }

    @Test
    fun wsTls() {
        val l = VlessConfig.parse(
            "vless://aaaa@host.example:8443?type=ws&security=tls&sni=cdn.example&path=%2Fws&host=cdn.example#ws",
        )
        val ss = JSONObject(VlessConfig.build(l)).getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings")
        assertEquals("ws", ss.getString("network"))
        assertEquals("/ws", ss.getJSONObject("wsSettings").getString("path"))
        assertEquals("cdn.example", ss.getJSONObject("tlsSettings").getString("serverName"))
    }

    @Test
    fun hasTunInbound() {
        val cfg = JSONObject(VlessConfig.build(VlessConfig.parse("vless://u@h:1?security=none")))
        assertEquals("tun", cfg.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
    }

    @Test
    fun subscriptionPlainAndBase64() {
        val plain = "vless://a@h1:1?security=none#one\nvless://b@h2:2?security=none#two\nvmess://x\n"
        assertEquals(2, Subscription.extractLinks(plain).size)
        val b64 = java.util.Base64.getEncoder().encodeToString(plain.toByteArray())
        assertEquals(2, Subscription.extractLinks(b64).size)
        val noPad = b64.trimEnd('=')
        assertEquals(2, Subscription.extractLinks(noPad).size)
        assertEquals(0, Subscription.extractLinks("<html>nope</html>").size)
    }

    @Test
    fun rejectsGarbage() {
        val r = runCatching { VlessConfig.parse("vmess://abc") }
        assertTrue(r.isFailure)
    }
}
