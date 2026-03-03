# ATVxray (Minimal Android TV VLESS Client)

Ultra-minimal Android TV VPN client based on `Xray-core` via `libXray`.

## Features
- Import VLESS config from TXT (`vless://...` first valid line).
- One switch to connect/disconnect VPN.
- Full TUN mode (`VpnService` + `tun2socks`) for global traffic proxy.
- Default bypass for LAN/private networks:
  - `10.0.0.0/8`
  - `172.16.0.0/12`
  - `192.168.0.0/16`
  - `geoip:private`

## Required binaries
1. Build `libXray.aar` from official repo:
   - `python3 build/main.py android`
2. Copy `libXray.aar` to:
   - `app/libs/libXray.aar`
3. `tun2socks` is embedded via JNI (prebuilt static library), no external binary is required.

## Build.
```powershell
.\gradlew.bat assembleDebug
```

## Runtime notes
- Target scenario: Android TV 11 low-RAM devices (`armeabi-v7a`, ~1 GB RAM).
- App excludes itself from VPN route (`addDisallowedApplication`) to avoid proxy loop.
- `libXray` API is called via reflection because API stability is not guaranteed by upstream.
