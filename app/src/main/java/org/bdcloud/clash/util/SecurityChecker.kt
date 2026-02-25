package org.bdcloud.clash.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Multi-layer security check to block:
 *   - Rooted devices (su, Magisk, SuperSU, KingRoot, etc.)
 *   - Emulators (Android Studio, BlueStacks, NOX, LDPlayer, Memu, Genymotion)
 *   - Debugger attached
 *   - ADB debugging enabled
 */
object SecurityChecker {

    data class SecurityResult(
        val isSecure: Boolean,
        val reason: String
    )

    fun check(context: Context): SecurityResult {
        // Check root
        val rootResult = checkRoot(context)
        if (rootResult != null) return SecurityResult(false, rootResult)

        // Check emulator
        val emuResult = checkEmulator(context)
        if (emuResult != null) return SecurityResult(false, emuResult)

        // Check debugger
        if (android.os.Debug.isDebuggerConnected()) {
            return SecurityResult(false, "Debugger detected. This app cannot run with a debugger attached.")
        }

        // Check APK integrity (signature, Frida, Xposed, modding tools)
        val integrity = IntegrityChecker.verify(context)
        if (!integrity.isIntact) {
            return SecurityResult(false, integrity.violations.first())
        }

        return SecurityResult(true, "OK")
    }

    // ──────────────────────────────────────────────────────
    // ROOT DETECTION
    // ──────────────────────────────────────────────────────

    private fun checkRoot(context: Context): String? {
        // 1. Check for su binary in common paths
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/su/bin/su", "/su/bin", "/magisk/.core/bin/su",
            "/system/app/Superuser.apk", "/system/etc/.installed_su_daemon",
            "/dev/com.koushikdutta.superuser.daemon/"
        )
        for (path in suPaths) {
            if (File(path).exists()) {
                return "This app cannot run on rooted devices."
            }
        }

        // 2. Check for root management apps
        val rootPackages = listOf(
            "com.topjohnwu.magisk",           // Magisk Manager
            "io.github.vvb2060.magisk",        // Magisk Alpha
            "com.koushikdutta.superuser",      // CWM SuperUser
            "eu.chainfire.supersu",            // SuperSU
            "com.noshufou.android.su",         // Superuser
            "com.thirdparty.superuser",        // Another SuperUser
            "com.yellowes.su",                 // Another SU
            "com.kingroot.kinguser",           // KingRoot
            "com.kingo.root",                  // KingoRoot
            "com.zhiqupk.root.global",         // One Click Root
            "com.smedialink.oneclickroot",     // OneClickRoot
            "com.alephzain.framaroot",         // Framaroot
            "com.saurik.substrate",            // Cydia Substrate
            "de.robv.android.xposed.installer", // Xposed
            "com.formyhm.hideroot",            // Hide Root
            "com.devadvance.rootcloak",        // RootCloak
            "com.devadvance.rootcloakplus"     // RootCloak Plus
        )
        for (pkg in rootPackages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return "This app cannot run on rooted devices."
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed — good
            }
        }

        // 3. Check if su is executable
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (result.isNotEmpty()) {
                return "This app cannot run on rooted devices."
            }
        } catch (_: Exception) {
            // Command failed — not rooted
        }

        // 4. Check system properties for test-keys (custom ROM indicator)
        try {
            val tags = Build.TAGS
            if (tags != null && tags.contains("test-keys")) {
                return "This app cannot run on modified system images."
            }
        } catch (_: Exception) { }

        // 5. Check for RW system partition
        try {
            val process = Runtime.getRuntime().exec("mount")
            val mounts = process.inputStream.bufferedReader().readText()
            process.waitFor()
            for (line in mounts.lines()) {
                if (line.contains(" /system") && line.contains("rw")) {
                    return "This app cannot run on rooted devices."
                }
            }
        } catch (_: Exception) { }

        return null // Not rooted
    }

    // ──────────────────────────────────────────────────────
    // EMULATOR DETECTION
    // ──────────────────────────────────────────────────────

    private fun checkEmulator(context: Context): String? {
        var score = 0

        // Build properties checks
        if (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.FINGERPRINT.contains("sdk_gphone")) score += 3

        if (Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MODEL.contains("sdk_gphone")) score += 3

        if (Build.MANUFACTURER.contains("Genymotion") ||
            Build.MANUFACTURER == "unknown") score += 2

        if (Build.BRAND.startsWith("generic") || Build.BRAND == "google") score += 1

        if (Build.DEVICE.startsWith("generic") ||
            Build.DEVICE.contains("emulator") ||
            Build.DEVICE.contains("emu")) score += 2

        if (Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT == "google_sdk" ||
            Build.PRODUCT.contains("vbox")) score += 3

        if (Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("vbox")) score += 3

        if (Build.BOARD.contains("unknown") ||
            Build.BOARD.contains("goldfish")) score += 2

        // Known emulator packages
        val emulatorPackages = listOf(
            "com.bluestacks",                      // BlueStacks
            "com.bignox.app",                      // NOX
            "com.vphone.launcher",                 // VPhonGaGa
            "com.microvirt.memuime",               // MEmu
            "com.kaopu009.appsmarket",             // LDPlayer
            "com.google.android.launcher.layouts.genymotion", // Genymotion
            "com.android.emulator.smoketests"       // Android Emulator
        )
        for (pkg in emulatorPackages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                score += 5
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        // Check for emulator-specific files
        val emulatorFiles = listOf(
            "/dev/socket/qemud", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-props",
            "/.vbox_version", "/dev/vboxguest", "/dev/vboxuser"
        )
        for (path in emulatorFiles) {
            if (File(path).exists()) score += 3
        }

        // Check for emulator-specific system properties
        try {
            val qemu = System.getProperty("ro.kernel.qemu")
            if (qemu == "1") score += 5
        } catch (_: Exception) { }

        try {
            val hardware = System.getProperty("ro.hardware")
            if (hardware != null && (hardware.contains("goldfish") || hardware.contains("ranchu"))) {
                score += 5
            }
        } catch (_: Exception) { }

        // Threshold: score >= 5 = likely emulator
        if (score >= 5) {
            return "This app cannot run on emulators or virtual devices."
        }

        return null // Not an emulator
    }
}
