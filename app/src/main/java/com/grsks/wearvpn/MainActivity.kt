package com.grsks.wearvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {

    private var linkName by mutableStateOf<String?>(null)
    private var importError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        refreshName()
        setContent {
            MaterialTheme {
                Screen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        refreshName()
    }

    /** adb shell am start -a android.intent.action.VIEW -d "vless://..." */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "vless") return
        importError = try {
            LinkStore.set(this, data.toString())
            null
        } catch (e: Exception) {
            e.message
        }
    }

    private fun refreshName() {
        linkName = LinkStore.get(this)?.let { runCatching { VlessConfig.parse(it).name }.getOrNull() }
    }

    @Composable
    private fun Screen() {
        val status by VpnState.status.collectAsState()
        val message by VpnState.message.collectAsState()

        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) XrayVpnService.start(this)
        }

        fun toggle() {
            when (status) {
                VpnStatus.ON, VpnStatus.CONNECTING -> {
                    XrayVpnService.stop(this)
                    XrayProxyService.stop(this)
                }
                else -> {
                    try {
                        val prepare = VpnService.prepare(this)
                        if (prepare != null) consent.launch(prepare) else XrayVpnService.start(this)
                    } catch (e: Exception) {
                        // На некоторых часах (Pixel Watch 4) в системе вообще нет сервиса VPN:
                        // работаем как локальный прокси + глобальный прокси системы.
                        XrayProxyService.start(this)
                    }
                }
            }
        }

        val color = when (status) {
            VpnStatus.ON -> Color(0xFF2E7D32)
            VpnStatus.CONNECTING -> Color(0xFFF9A825)
            VpnStatus.ERROR -> Color(0xFFC62828)
            VpnStatus.OFF -> Color(0xFF455A64)
        }
        val label = when (status) {
            VpnStatus.ON -> "ВКЛ"
            VpnStatus.CONNECTING -> "…"
            VpnStatus.ERROR -> "ОШИБКА"
            VpnStatus.OFF -> "ВЫКЛ"
        }
        val hasLink = linkName != null

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { toggle() },
                enabled = hasLink,
                modifier = Modifier.size(88.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = color),
            ) {
                Text(label, fontSize = 15.sp)
            }
            val info = when {
                importError != null -> "Ссылка неверна: $importError"
                !hasLink -> "Задай ссылку через adb (см. README)"
                status == VpnStatus.ERROR -> message
                else -> linkName ?: ""
            }
            Text(
                info,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    }
}
