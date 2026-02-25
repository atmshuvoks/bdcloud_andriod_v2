# BDCLOUD Android VPN Client v2

A secure Android VPN client that routes device traffic through SOCKS5 proxy servers using **mihomo** (Clash Meta) and **tun2socks**.

## Architecture

```
App Traffic → VPN TUN → tun2socks (JNI fork) → mihomo SOCKS5 → Proxy Servers → Internet
DNS Traffic → 1.1.1.1 / 8.8.8.8 (excluded from VPN routes, goes direct)
```

### Components

| Component | Role |
|-----------|------|
| **mihomo** (`libmihomo.so`) | Local SOCKS5/HTTP proxy engine — routes traffic through remote proxy servers |
| **tun2socks** (`libtun2socks.so`) | Bridges Android VPN TUN interface ↔ mihomo's SOCKS5 proxy |
| **NativeHelper** (`native_helper.c`) | JNI helper using `fork()/dup2()/exec()` to pass VPN fd to tun2socks |
| **BdCloudVpnService** | Android VPN service orchestrating all components |

## Key Technical Challenges & Solutions

### Why This Was So Hard

Android's security model imposes severe restrictions that make VPN development extremely challenging compared to desktop Linux:

#### 1. ❌ mihomo TUN Mode Doesn't Work on Android
**Problem:** mihomo's built-in TUN mode requires **netlink sockets** to configure routes. Android bans netlink for non-root apps → `route ip+net: netlinkrib: permission denied`.

**Solution:** Don't use mihomo's TUN mode at all. Run mihomo as a **plain SOCKS5 proxy** and use **tun2socks** to bridge the VPN TUN interface to SOCKS5.

#### 2. ❌ Android Kills File Descriptors in Child Processes
**Problem:** The VPN TUN file descriptor (fd) must be passed to tun2socks. Android's `ProcessBuilder` forcibly closes all fds ≥ 3 in child processes, making `fd://N` unusable.

**Solution:** Created a **JNI native helper** (`native_helper.c`) that uses `fork()` + `dup2()` + `exec()` to start tun2socks while preserving the VPN fd. This bypasses Android's `ProcessBuilder` limitations.

#### 3. ❌ DNS Over UDP Fails Through SOCKS5 Proxies
**Problem:** SOCKS5 proxies don't support UDP relay (UDP ASSOCIATE). DNS uses UDP port 53. All DNS queries through the proxy → `client handshake error: EOF` / `i/o timeout`.

**Solution Attempts That Failed:**
- `DST-PORT,53,DIRECT` rule → netlink permission denied (DIRECT doesn't work on Android)
- DnsRelay on `127.0.0.1:53` → Android rejects loopback as VPN DNS: `Bad address`
- DnsRelay on `172.19.0.1:53` (VPN gateway IP) → Packets to VPN's own IP still go through TUN on Android

**✅ Final Solution:** Use Android API 33+ `excludeRoute()` to exclude DNS server IPs from VPN routing entirely:
```kotlin
builder.excludeRoute(IpPrefix(InetAddress.getByName("1.1.1.1"), 32))
builder.excludeRoute(IpPrefix(InetAddress.getByName("8.8.8.8"), 32))
```
DNS queries go **directly** to real DNS servers, completely bypassing the VPN tunnel.

### How Other Clash Apps Solve This

Apps like **FlClash**, **ClashForAndroid**, **ClashMetaForAndroid** compile mihomo's Go source code as an **in-process shared library** (`.so`). This eliminates ALL the above issues because:
- VPN fd is passed directly via JNI (no fork needed)
- DNS is handled internally by the Go library
- No netlink needed — everything runs in-process

Our approach uses the **CLI binary** with tun2socks, which requires more creative workarounds but avoids the Go toolchain dependency.

## Security Features

- **APK Signature Verification** — Detects re-signing/tampering
- **Root/Emulator Detection** — Blocks execution on rooted devices and emulators
- **Debugger Detection** — Prevents runtime debugging
- **Frida/Xposed Detection** — Catches instrumentation frameworks
- **ProGuard/R8 Obfuscation** — Aggressive code shrinking and obfuscation
- **Log Stripping** — Debug/verbose logs removed in release builds

## Build Requirements

- **Android Studio** (latest stable)
- **Android SDK** (API 35)
- **Android NDK** (for JNI native helper compilation)
- **CMake 3.22.1+**
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35

## Building

1. Open the project in Android Studio
2. Install NDK and CMake via **Tools → SDK Manager → SDK Tools**
3. Build via **Build → Build Bundle(s) / APK(s) → Build APK(s)**

## Project Structure

```
app/src/main/
├── java/org/bdcloud/clash/
│   ├── core/
│   │   ├── BdCloudVpnService.kt    # VPN service orchestrator
│   │   ├── ClashManager.kt          # Config generator
│   │   ├── NativeHelper.kt          # JNI binding for fd passing
│   │   └── ...
│   ├── ui/                           # Activities & Fragments
│   ├── api/                          # Backend API client
│   └── util/
│       ├── SecurityChecker.kt        # Root/emulator/debugger checks
│       └── IntegrityChecker.kt       # APK signature verification
├── cpp/
│   ├── native_helper.c               # fork/dup2/exec JNI implementation
│   └── CMakeLists.txt
├── jniLibs/arm64-v8a/
│   ├── libmihomo.so                  # Clash Meta proxy engine
│   └── libtun2socks.so               # TUN-to-SOCKS5 bridge
└── res/                              # Layouts, themes, drawables
```

## Author

**Tanvir Alam Mishona** (atmshuvoks)

## License

Private — All rights reserved.
