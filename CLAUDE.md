# Wear VPN — контекст проекта

Wear OS-приложение (Kotlin, Compose for Wear OS, minSdk 30, targetSdk 33) — клиент VLESS на ядре Xray (`libv2ray.aar`, не в git, скачивается: см. README). Пакет `com.grsks.wearvpn`. Пользователь говорит по-русски.

## Архитектура
- `VlessConfig` — разбор `vless://` и сборка JSON для Xray (`tun=true` — TUN-inbound; `tun=false` — HTTP :10809 + SOCKS5 :10808).
- `XrayVpnService` — системный VpnService, fd туннеля отдаётся в `startLoop(config, fd)`; приложение исключено из туннеля (`addDisallowedApplication`).
- `XrayProxyService` — режим прокси для часов без VPN: Xray + запись `Settings.Global.http_proxy` (нужно `pm grant … WRITE_SECURE_SETTINGS`, право должно быть объявлено в манифесте).
- `Subscription` + `LinkReceiver` — ссылка/подписка приходит по `adb shell am broadcast` (`--es link`, `--ei index`).
- `MainActivity.toggle`: пробует `VpnService.prepare`; при исключении (на Pixel Watch 4 нет IVpnManager) идёт в `XrayProxyService`.

## Статус
- v0.1.0. Pixel Watch 4 (Wear OS 6, armeabi-v7a): режим прокси проверен (сайты работают, выход через сервер NL). Режим VPN на реальных часах не проверялся. Telegram на часах (2.0.1) не открывает `tg://socks`, прокси вводится вручную; работа через него не проверена.
- Импорт подписки на часах после последней сборки не проверялся (часы ушли в offline).

## Сборка и отладка
- `JAVA_HOME=C:/Users/grsks/.jdks/jbr-21.0.11 ./gradlew assembleDebug testDebugUnitTest`, предварительно положить `app/libs/libv2ray.aar`.
- Часы подключаются по Wi-Fi adb, порт меняется; экран должен быть включён (`input keyevent KEYCODE_WAKEUP`), тап по кнопке: `input tap 213 195` (иногда нужен второй).
- Файлы Gradle Kotlin DSL писать инструментом Write, а не через heredoc. В XML-комментариях нельзя `--`.
- Часть провайдеров отдаёт заглушку «App not supported» (серверы только своему приложению), другие отдают обычные vless. Ключи, UUID и ссылки подписок в репозиторий не класть.
