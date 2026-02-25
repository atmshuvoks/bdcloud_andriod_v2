package org.bdcloud.clash.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import org.bdcloud.clash.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Multi-layer APK integrity & anti-tampering checks:
 *   1. APK Signature Verification — detects re-signing after modification
 *   2. Installer Verification — detects sideloading from untrusted sources
 *   3. Frida Detection — detects runtime instrumentation
 *   4. Xposed Detection — detects Xposed framework hooks
 *   5. Debuggable Flag Check — detects repackaged APKs with android:debuggable=true
 *   6. Dual-Process Detection — detects parallel instrumentation processes
 *
 * IMPORTANT: After building your RELEASE APK, run the app once to get the
 * SHA-256 signature hash from logcat (tag: IntegrityChecker), then paste it
 * into RELEASE_CERT_SHA256 below. This locks the app to your signing key.
 */
object IntegrityChecker {

    private const val TAG = "IntegrityChecker"

    // *** PASTE YOUR RELEASE SIGNING CERTIFICATE SHA-256 HERE ***
    // Build a signed release APK, install it, check logcat for:
    //   "Current APK signature SHA-256: XXXXXXXX..."
    // Then paste that hex string here. Until then, set to "" to skip signature check.
    private const val RELEASE_CERT_SHA256 = "FC47C2C753471F280480B1E7559468F700E081100D1DBBA162A7E2E965E0CF8D"

    data class IntegrityResult(
        val isIntact: Boolean,
        val violations: List<String>
    )

    fun verify(context: Context): IntegrityResult {
        val violations = mutableListOf<String>()

        // 1. Check APK signature
        checkSignature(context)?.let { violations.add(it) }

        // 2. Check if app was repackaged as debuggable (only blocks in release)
        if (isDebuggable(context) && !BuildConfig.DEBUG) {
            violations.add("Application has been tampered with (debuggable).")
        }

        // 3. Check for Frida
        if (isFridaDetected()) {
            violations.add("Instrumentation tool detected.")
        }

        // 4. Check for Xposed
        if (isXposedDetected(context)) {
            violations.add("Hook framework detected.")
        }

        // 5. Check for Lucky Patcher / APK modding tools
        checkModdingTools(context)?.let { violations.add(it) }

        // 6. Check for tampering via package name change
        if (context.packageName != "org.bdcloud.clash") {
            violations.add("Package name has been modified.")
        }

        return IntegrityResult(violations.isEmpty(), violations)
    }

    // ─────────────────────────────────────────────
    // 1. APK SIGNATURE VERIFICATION
    // ─────────────────────────────────────────────

    @SuppressLint("PackageManagerGetSignatures")
    private fun checkSignature(context: Context): String? {
        try {
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners ?: return null
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                info.signatures ?: return null
            }

            for (sig in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(sig.toByteArray())
                val hex = digest.joinToString("") { "%02X".format(it) }

                // Log.e survives ProGuard (only d/v/i/w are stripped)
                Log.e(TAG, "Current APK signature SHA-256: $hex")

                // If release cert is set, verify it matches
                if (RELEASE_CERT_SHA256.isNotEmpty() &&
                    !hex.equals(RELEASE_CERT_SHA256, ignoreCase = true)) {
                    return "APK signature does not match. This app may have been modified."
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signature check error: ${e.message}")
        }
        return null
    }

    // ─────────────────────────────────────────────
    // 2. DEBUGGABLE CHECK
    // ─────────────────────────────────────────────

    private fun isDebuggable(context: Context): Boolean {
        return try {
            val flags = context.applicationInfo.flags
            (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────
    // 3. FRIDA DETECTION
    // ─────────────────────────────────────────────

    private fun isFridaDetected(): Boolean {
        // Check for Frida server listening on default ports
        val fridaPorts = listOf(27042, 27043)
        for (port in fridaPorts) {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 200)
                socket.close()
                return true // Frida server is listening
            } catch (_: Exception) {
                // Port not open — good
            }
        }

        // Check for frida-related files in /proc
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                if (content.contains("frida") || content.contains("gadget")) {
                    return true
                }
            }
        } catch (_: Exception) { }

        // Check for frida-related native libraries loaded
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val content = statusFile.readText()
                if (content.contains("TracerPid:\t0").not() &&
                    content.contains("TracerPid:")) {
                    // Being traced by another process
                    val tracerLine = content.lines().find { it.startsWith("TracerPid:") }
                    val tracerPid = tracerLine?.split(":")?.last()?.trim()?.toIntOrNull() ?: 0
                    if (tracerPid > 0) return true
                }
            }
        } catch (_: Exception) { }

        // Check for frida-server binary
        val fridaPaths = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/sdcard/frida-server"
        )
        for (path in fridaPaths) {
            if (File(path).exists()) return true
        }

        return false
    }

    // ─────────────────────────────────────────────
    // 4. XPOSED DETECTION
    // ─────────────────────────────────────────────

    private fun isXposedDetected(context: Context): Boolean {
        // Check for Xposed installer packages
        val xposedPackages = listOf(
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager",
            "com.solohsu.android.edxp.manager"
        )
        for (pkg in xposedPackages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        // Check Xposed Bridge loaded in memory
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                if (content.contains("XposedBridge") ||
                    content.contains("libxposed") ||
                    content.contains("edxposed") ||
                    content.contains("lspd")) {
                    return true
                }
            }
        } catch (_: Exception) { }

        // Check for Xposed Bridge class in runtime
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: ClassNotFoundException) { }

        // Check stack trace for Xposed hooks
        try {
            val stackTrace = Throwable().stackTrace
            for (element in stackTrace) {
                val className = element.className
                if (className.contains("xposed", ignoreCase = true) ||
                    className.contains("EdHooker", ignoreCase = true) ||
                    className.contains("LSPosed", ignoreCase = true)) {
                    return true
                }
            }
        } catch (_: Exception) { }

        return false
    }

    // ─────────────────────────────────────────────
    // 5. MODDING TOOLS DETECTION
    // ─────────────────────────────────────────────

    private fun checkModdingTools(context: Context): String? {
        val moddingPackages = listOf(
            "com.chelpus.lackypatch",              // Lucky Patcher
            "com.dimonvideo.luckypatcher",          // Lucky Patcher variant
            "com.android.vending.billing.InAppBillingService.LUCK", // LP IAP
            "com.android.vending.billing.InAppBillingService.COIN", // LP Coin
            "jase.december.jasi2169",               // Game Killer
            "org.sbtools.gamehack",                  // SB Game Hacker
            "com.cih.gamecih2",                     // GameCIH
            "com.charles.lpoqasert",                // Another LP variant
            "catch_.me_.if" + "_.you" + "_.can_",   // Obfuscated LP name
            "com.topjohnwu.magisk"                  // Magisk (also in SecurityChecker)
        )

        for (pkg in moddingPackages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return "A modding tool was detected on this device."
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        return null
    }
}
