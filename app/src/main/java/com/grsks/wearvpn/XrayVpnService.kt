package com.grsks.wearvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import kotlin.concurrent.thread

class XrayVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (VpnState.status.value == VpnStatus.ON || VpnState.status.value == VpnStatus.CONNECTING) return
        VpnState.set(VpnStatus.CONNECTING)
        startForegroundCompat()
        thread(name = "xray-start") {
            try {
                val link = VlessConfig.parse(LinkStore.get(this) ?: error("Сначала задай ссылку vless://"))
                val config = VlessConfig.build(link)

                val pfd = Builder()
                    .setSession(link.name)
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    // Сам Xray живёт в этом процессе: исключаем приложение из туннеля,
                    // иначе его собственные соединения к серверу зациклятся.
                    .addDisallowedApplication(packageName)
                    .establish() ?: error("Система не дала создать VPN (нет разрешения?)")
                tun = pfd

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
                controller.startLoop(config, pfd.fd)
                VpnState.set(VpnStatus.ON, link.name)
                updateNotification("Подключено: ${link.name}")
            } catch (e: Throwable) {
                Log.e(TAG, "start failed", e)
                stopVpn()
                VpnState.set(VpnStatus.ERROR, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun stopVpn() {
        thread(name = "xray-stop") {
            try {
                core?.stopLoop()
            } catch (e: Throwable) {
                Log.w(TAG, "stopLoop: ${e.message}")
            }
            core = null
            try {
                tun?.close()
            } catch (_: Exception) {
            }
            tun = null
            if (VpnState.status.value != VpnStatus.ERROR) VpnState.set(VpnStatus.OFF)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        try {
            core?.stopLoop()
        } catch (_: Throwable) {
        }
        tun?.close()
        if (VpnState.status.value != VpnStatus.ERROR) VpnState.set(VpnStatus.OFF)
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Wear VPN")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat() = startForeground(NOTIF_ID, notification("Подключение…"))

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    companion object {
        private const val TAG = "WearVpn"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.grsks.wearvpn.STOP"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, XrayVpnService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, XrayVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
