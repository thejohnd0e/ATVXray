# ATVxray (Minimal Android TV VLESS Client)

Ultra-minimal Android TV VPN client based on `Xray-core` via `libXray`.

## Status
- Verified working on Amazon Fire TV Stick 2nd Gen (2016):
  - Fire OS `5.2.9.5`
  - Android `5.1` (API 22)
  - 32-bit (`armeabi-v7a`)

## Features
- Import VLESS config from TXT (`vless://...` first valid line).
- One switch to connect/disconnect VPN.
- Full TUN mode (`VpnService` + embedded `tun2socks`) for global traffic proxy.
- Default LAN/private bypass routes:
  - `10.0.0.0/8`
  - `172.16.0.0/12`
  - `192.168.0.0/16`
  - `127.0.0.0/8`
  - `169.254.0.0/16`
  - `100.64.0.0/10`

## Requirements
1. JDK 17 (Temurin/OpenJDK).
2. Android SDK (`platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`).
3. `libXray.aar` at `app/libs/libXray.aar`.

If Gradle cannot find SDK, create `local.properties`:
```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Build
Debug:
```powershell
.\gradlew.bat :app:assembleDebug
```

Release (unsigned):
```powershell
.\gradlew.bat :app:assembleRelease
```

## Sign release APK
This project currently produces `app-release-unsigned.apk` by default.

```powershell
$bt="$env:LOCALAPPDATA/Android/Sdk/build-tools/34.0.0"
& "$bt/apksigner.bat" sign --ks d:/MyProjects/ATVxray/release-keystore.jks --ks-key-alias atvxray --ks-pass pass:atvxray123 --key-pass pass:atvxray123 --out d:/MyProjects/ATVxray/app/build/outputs/apk/release/app-release.apk d:/MyProjects/ATVxray/app/build/outputs/apk/release/app-release-unsigned.apk
Copy-Item d:/MyProjects/ATVxray/app/build/outputs/apk/release/app-release.apk d:/MyProjects/ATVxray/ATVxray-release.apk -Force
```

Final artifact:
- `ATVxray-release.apk` (project root)

## Runtime notes
- App excludes itself from VPN route (`addDisallowedApplication`) to avoid proxy loops.
- `libXray` API is called via reflection due to upstream API instability.
- API 22 compatibility fix applied in `VpnTunnelService` notification manager access.
