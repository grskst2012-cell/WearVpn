package com.grsks.wearvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import kotlin.concurrent.thread

/**
 * Режим для часов без системного VPN (Pixel Watch 4): Xray слушает HTTP-прокси
 * на 127.0.0.1:PORT, а приложение прописывает его как глобальный прокси системы.
 * Нужно право WRITE_SECURE_SETTINGS (выдаётся один раз через adb).
 */
class XrayProxyService : Service() {

    private var core: CoreController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProxy()
            else -> startProxy()
        }
        return START_NOT_STICKY
    }

    private fun startProxy() {
        if (VpnState.status.value == VpnStatus.ON || VpnState.status.value == VpnStatus.CONNECTING) return
        VpnState.set(VpnStatus.CONNECTING)
        startForeground(NOTIF_ID, notification("Подключение…"))
        thread(name = "xray-proxy-start") {
            try {
                val link = VlessConfig.parse(LinkStore.get(this) ?: error("Сначала задай ссылку vless://"))
                val config = VlessConfig.build(link, tun = false, proxyPort = PORT)

                Libv2ray.initCoreEnv(filesDir.absolutePath, "")
                val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
                    override fun startup(): Long = 0
                    override fun shutdown(): Long = 0
                    override fun onEmitStatus(code: Long, msg: String?): Long {
                        Log.i(TAG, "xray[$code]: $msg")
                        return 0
                    }
                })
                core = controller
                controller.startLoop(config, 0)

                try {
                    Settings.Global.putString(contentResolver, "http_proxy", "127.0.0.1:$PORT")
                } catch (e: SecurityException) {
                    error(
                        "Нет права WRITE_SECURE_SETTINGS. Выполни на ПК: adb shell pm grant " +
                            "$packageName android.permission.WRITE_SECURE_SETTINGS",
                    )
                }
                VpnState.set(VpnStatus.ON, link.name)
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification("Прокси: ${link.name}"))
            } catch (e: Throwable) {
                Log.e(TAG, "proxy start failed", e)
                shutdown()
                VpnState.set(VpnStatus.ERROR, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun shutdown() {
        try {
            Settings.Global.putString(contentResolver, "http_proxy", ":0")
        } catch (_: Exception) {
        }
        try {
            core?.stopLoop()
        } catch (e: Throwable) {
            Log.w(TAG, "stopLoop: ${e.message}")
        }
        core = null
    }

    private fun stopProxy() {
        thread(name = "xray-proxy-stop") {
            shutdown()
            if (VpnState.status.value != VpnStatus.ERROR) VpnState.set(VpnStatus.OFF)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        shutdown()
        if (VpnState.status.value == VpnStatus.ON) VpnState.set(VpnStatus.OFF)
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Прокси", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Wear VPN")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WearVpn"
        private const val CHANNEL = "proxy"
        private const val NOTIF_ID = 2
        const val PORT = 10809
        const val ACTION_STOP = "com.grsks.wearvpn.STOP_PROXY"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, XrayProxyService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, XrayProxyService::class.java).setAction(ACTION_STOP))
        }
    }
}
