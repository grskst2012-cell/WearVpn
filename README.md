# Wear VPN

![Platform](https://img.shields.io/badge/platform-Wear%20OS%203%2B-4285F4?logo=wearos&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Build](https://github.com/grskst2012-cell/WearVpn/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-green)

[English](README.md) · [Русский](README.ru.md)

A small VPN / proxy client for **Wear OS 3+** watches. It speaks **VLESS** (Xray): tcp, ws, grpc, httpupgrade, xhttp over tls or reality, and can import a provider's **subscription URL** with one command.

> A personal, experimental project. Verified on a Pixel Watch 4 (Wear OS 6) with a subscription that serves plain `vless://` links.

## Read this first

Some watches, notably the **Pixel Watch 4**, have no system VPN service at all, so a real VPN is impossible there. The app detects this and switches to **proxy mode**:

| Mode | When | Covers |
|---|---|---|
| **VPN** | The watch supports system VPN (e.g. Galaxy Watch) | All traffic. *Not tested on real hardware.* |
| **Proxy** | No system VPN (Pixel Watch 4) | Browser and apps that respect the system proxy; others ignore it. |

In proxy mode **Telegram** must be configured separately (see below). All traffic through the app **counts against your subscription's data limit**.

## What you need

- A Wear OS 3+ watch on the same Wi-Fi as your computer.
- A computer with **adb** ([Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)).
- `WearVpn-0.1.0-debug.apk` (Releases, or build it yourself, see below).
- A subscription URL or a single `vless://` link.

## Install

1. **Watch:** Settings → System → About → Software information, tap *Build number* 7 times. Then Developer options → enable *ADB debugging* and *Wireless debugging*.
2. In *Wireless debugging* note the address (`192.168.1.50:34567`); tap *Pair new device* to get a second address and a 6-digit code.
3. **Computer:**
   ```
   adb pair 192.168.1.50:41234        # enter the 6-digit code
   adb connect 192.168.1.50:34567
   adb install -r WearVpn-0.1.0-debug.apk
   adb shell pm grant com.grsks.wearvpn android.permission.WRITE_SECURE_SETTINGS
   ```
   The wireless debugging port **changes** after a reboot or long sleep; reconnect with the new port if adb says `offline`.

## Add a subscription or a server

There is no practical keyboard on a watch, so the link is sent from the computer.

Subscription (a `https://…` URL from your provider):

```
adb shell am broadcast -a com.grsks.wearvpn.SET_LINK -n com.grsks.wearvpn/.LinkReceiver --es link "'https://YOUR-SUBSCRIPTION-URL'"
```

The reply lists the servers (`[0] NL …`, `[1] FR …`). Pick another one with `--ei index 2`. A single link works the same way: `--es link "'vless://...'"`. Keep both quote layers: links contain `&` and `#`.

If you get `ERROR … no vless:// servers`, the provider serves real servers only to its own app (you get an "App not supported" stub). Ask them for links for other clients. Only VLESS is supported.

## Use

Open **Wear VPN** and tap the round button (green = on). Tap again to turn it off, which also clears the system proxy. Check by opening an IP-lookup site in the watch browser.

**Telegram** ignores the system proxy. Add a SOCKS5 proxy in its settings (Data and Storage → Proxy, if your version has it): server `127.0.0.1`, port `10808`, no login or password. The Wear OS Telegram 2.0.1 does not open `tg://socks` links, so enter it by hand.

## FAQ

- **Does it use my subscription data?** Yes, exactly like any other device on the same key; the limit is shared.
- **Watch rebooted?** Open the app and tap the button again; the link is saved.
- **Internet broke after uninstalling?** `adb shell settings put global http_proxy :0`
- **IPv6?** Not supported.

Uninstall: `adb shell settings put global http_proxy :0` then `adb uninstall com.grsks.wearvpn`.

## Build

JDK 17–21 and the Android SDK.

```
mkdir app/libs
curl -L -o app/libs/libv2ray.aar https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.31/libv2ray.aar
./gradlew assembleDebug testDebugUnitTest
```

The ~60 MB Xray core is not stored in the repository. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

`VlessConfig` turns a `vless://` link into Xray JSON; `Subscription` downloads and decodes a subscription; `XrayVpnService` feeds the system TUN to Xray (VPN mode); `XrayProxyService` runs Xray with HTTP `127.0.0.1:10809` and SOCKS5 `127.0.0.1:10808` and sets `Settings.Global.http_proxy` (proxy mode); `LinkReceiver` takes links from adb.

## Credits and license

Core: [Xray-core](https://github.com/XTLS/Xray-core) via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) (MPL-2.0). This project: MIT, see `LICENSE`.
