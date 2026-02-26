package org.bdcloud.clash.core

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.bdcloud.clash.BdCloudApp
import org.bdcloud.clash.R
import org.bdcloud.clash.ui.main.MainActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android VPN Service that routes traffic through mihomo proxy.
 *
 * Architecture (non-root, no netlink needed):
 *   1. Start mihomo as local SOCKS5/HTTP proxy (no TUN mode)
 *   2. Create Android VPN TUN interface
 *   3. Start tun2socks — reads packets from VPN TUN fd, forwards to mihomo SOCKS5
 *   4. All device traffic: App → VPN TUN → tun2socks → mihomo SOCKS5 → proxy server → internet
 */
class BdCloudVpnService : VpnService() {

    companion object {
        private const val TAG = "BdCloudVpn"
        const val ACTION_START = "org.bdcloud.clash.START_VPN"
        const val ACTION_STOP = "org.bdcloud.clash.STOP_VPN"
        const val EXTRA_CONFIG_PATH = "config_path"

        private val _isRunning = AtomicBoolean(false)
        private val _logLines = mutableListOf<String>()

        fun isActive(): Boolean = _isRunning.get()

        fun start(context: Context, configPath: String) {
            val intent = Intent(context, BdCloudVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_PATH, configPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BdCloudVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        @Synchronized
        fun getLogs(): List<String> = _logLines.toList()

        @Synchronized
        fun addLogLine(line: String) {
            // Filter out internal noise from production logs
            if (shouldFilterLog(line)) return
            _logLines.add(line)
            if (_logLines.size > 500) _logLines.removeAt(0)
        }

        private fun shouldFilterLog(line: String): Boolean {
            // Block tun2socks traffic/routing logs
            if (line.contains("INFO tunnel/")) return true
            if (line.contains("tcp.go:")) return true
            if (line.contains("copy data for")) return true
            if (line.contains("DEBUG tunnel/")) return true
            // Block mihomo internal config noise
            if (line.contains("time=") && line.contains("level=info")) return true
            if (line.contains("level=debug")) return true
            if (line.contains("msg=") && !line.contains("[VPN]")) return true
            // Block file paths and native lib info
            if (line.contains("nativeLib") || line.contains("/data/app/")) return true
            if (line.contains("Using native lib:")) return true
            return false
        }

        @Synchronized
        fun clearLogs() { _logLines.clear() }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var mihomoProcess: Process? = null
    private var tun2socksPid: Int = -1
    private var scope: CoroutineScope? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val configPath = intent.getStringExtra(EXTRA_CONFIG_PATH) ?: ""
                if (configPath.isBlank()) {
                    addLogLine("[ERROR] No config path provided")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startVpn(configPath)
            }
            ACTION_STOP -> stopVpn()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }

    private fun startVpn(configPath: String) {
        if (_isRunning.getAndSet(true)) {
            stopVpnInternal()
        }

         scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startForeground(BdCloudApp.VPN_NOTIFICATION_ID, buildNotification("Connecting..."))
        addLogLine("[VPN] Starting VPN service...")

        try {
            // ── Step 1: Resolve binaries ──
            val mihomoPath = resolveNativeLib("libmihomo.so")
                ?: throw Exception("mihomo binary not found in nativeLibraryDir")
            val tun2socksPath = resolveNativeLib("libtun2socks.so")
                ?: throw Exception("tun2socks binary not found in nativeLibraryDir")

            // ── Step 2: Start mihomo as local SOCKS5/HTTP proxy ──
            startMihomo(mihomoPath, configPath)
            Thread.sleep(2500)
            if (mihomoProcess?.isAlive != true) {
                val code = try { mihomoProcess?.exitValue() } catch (_: Exception) { -1 }
                throw Exception("mihomo exited immediately (code: $code)")
            }
            addLogLine("[VPN] Proxy engine started")

            // ── Step 3: Create VPN TUN interface ──
            val builder = Builder()
                .setSession("BDCLOUD VPN")
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(9000)
                .setBlocking(false)

            // Exclude our own app from VPN to prevent traffic loops
            builder.addDisallowedApplication(packageName)

            // Exclude DNS server IPs from VPN routes so DNS bypasses VPN entirely
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName("1.1.1.1"), 32))
                builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName("8.8.8.8"), 32))
                addLogLine("[VPN] DNS routing configured")
            } else {
                addLogLine("[VPN] DNS routing configured (legacy)")
            }

            vpnInterface = builder.establish()
                ?: throw Exception("VPN interface creation failed — permission denied?")

            val tunFd = vpnInterface!!.fd
            addLogLine("[VPN] Secure tunnel established")

            // ── Step 5: Start tun2socks via JNI (preserves VPN fd) ──
            startTun2socks(tun2socksPath, tunFd)

            Thread.sleep(1500)
            if (tun2socksPid > 0) {
                val procDir = java.io.File("/proc/$tun2socksPid")
                if (!procDir.exists()) {
                    val logFile = File(filesDir, "tun2socks.log")
                    val errorMsg = try { logFile.readText().trim() } catch (_: Exception) { "" }
                    if (errorMsg.isNotEmpty()) {
                        addLogLine("[VPN] Bridge startup error — check connection")
                    }
                    val exitResult = NativeHelper.waitForProcess(tun2socksPid)
                    val exitDesc = NativeHelper.decodeExitResult(exitResult)
                    throw Exception("tun2socks died immediately: $exitDesc")
                }
            } else {
                throw Exception("tun2socks fork failed (pid=$tun2socksPid)")
            }
            addLogLine("[VPN] Network bridge active")

            updateNotification("Connected — BDCLOUD VPN")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            addLogLine("[ERROR] VPN startup failed: ${e.message}")
            updateNotification("Connection failed")
            _isRunning.set(false)
            stopVpnInternal()
            stopSelf()
        }
    }

    // ────────────────────────────────────────────────
    // MIHOMO: Local SOCKS5/HTTP proxy (no TUN mode)
    // ────────────────────────────────────────────────

    private fun startMihomo(binaryPath: String, configPath: String) {
        val binFile = File(binaryPath)
        binFile.setExecutable(true, false)
        Log.d(TAG, "mihomo binary: $binaryPath (${binFile.length() / 1024}KB)")

        val configDir = File(configPath).parent ?: filesDir.absolutePath
        Log.d(TAG, "mihomo config: $configPath")

        val pb = ProcessBuilder(binaryPath, "-d", configDir, "-f", configPath)
        pb.directory(File(configDir))
        pb.redirectErrorStream(true)

        mihomoProcess = pb.start()
        Log.d(TAG, "mihomo process started")

        // Read mihomo stdout in background
        scope?.launch {
            try {
                val reader = BufferedReader(InputStreamReader(mihomoProcess?.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, line ?: continue)
                }
            } catch (e: Exception) {
                if (_isRunning.get()) Log.w(TAG, "log reader stopped: ${e.message}")
            }
        }

        // Monitor mihomo process
        scope?.launch {
            try {
                val exitCode = mihomoProcess?.waitFor() ?: -1
                addLogLine("[VPN] Proxy engine stopped")
                if (_isRunning.get()) {
                    withContext(Dispatchers.Main) {
                        _isRunning.set(false)
                        stopVpn()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ────────────────────────────────────────────────
    // TUN2SOCKS: Bridges VPN TUN ↔ mihomo SOCKS5
    // Uses JNI fork+dup2+exec to preserve VPN fd
    // ────────────────────────────────────────────────

    private fun startTun2socks(binaryPath: String, tunFd: Int) {
        val binFile = File(binaryPath)
        binFile.setExecutable(true, false)
        Log.d(TAG, "tun2socks binary: $binaryPath (${binFile.length() / 1024}KB)")

        val socksAddr = "socks5://127.0.0.1:${ClashManager.SOCKS_PORT}"
        val childFd = 3  // tun2socks will see the VPN TUN as fd 3
        val logFile = File(filesDir, "tun2socks.log")

        // Use JNI to fork+dup2+exec (Android ProcessBuilder can't pass fds)
        val args = arrayOf(
            binaryPath,          // argv[0]
            "-device", "fd://$childFd",
            "-proxy", socksAddr,
            "-loglevel", "info"  // safe level that suppresses debug traffic noise
        )

        tun2socksPid = NativeHelper.forkExecWithFd(tunFd, childFd, binaryPath, args, logFile.absolutePath)
        Log.d(TAG, "tun2socks forked pid=$tun2socksPid fd=$tunFd→$childFd $socksAddr")

        // Monitor tun2socks process via waitpid in background
        scope?.launch {
            try {
                val exitResult = NativeHelper.waitForProcess(tun2socksPid)
                val exitDesc = NativeHelper.decodeExitResult(exitResult)

                // Read log file for error details
                try {
                    val logContent = logFile.readText().trim()
                    if (logContent.isNotEmpty()) {
                        logContent.lines().forEach { line ->
                            Log.d(TAG, "tun2socks: $line")
                        }
                    }
                } catch (_: Exception) { }

                addLogLine("[VPN] Network bridge stopped")

                if (_isRunning.get()) {
                    withContext(Dispatchers.Main) {
                        _isRunning.set(false)
                        stopVpn()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ────────────────────────────────────────────────
    // RESOLVE NATIVE LIBRARIES
    // ────────────────────────────────────────────────

    private fun resolveNativeLib(name: String): String? {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val f = File(nativeLibDir, name)
        if (f.exists() && f.canExecute()) {
            Log.d(TAG, "Using native lib: ${f.absolutePath}")
            return f.absolutePath
        }
        addLogLine("[ERROR] Required component missing: $name")
        return null
    }

    // ────────────────────────────────────────────────
    // STOP / CLEANUP
    // ────────────────────────────────────────────────

    private fun stopVpn() {
        stopVpnInternal()
        _isRunning.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopVpnInternal() {
        // Stop tun2socks via JNI kill
        try {
            if (tun2socksPid > 0) {
                NativeHelper.killProcess(tun2socksPid)
                tun2socksPid = -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping tun2socks: ${e.message}")
        }


        // Stop mihomo
        try {
            mihomoProcess?.destroy()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mihomoProcess?.destroyForcibly()
            }
            mihomoProcess = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mihomo: ${e.message}")
        }

        // Close VPN interface
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN: ${e.message}")
        }

        scope?.cancel()
        scope = null
        addLogLine("[VPN] Service stopped")
    }

    // ────────────────────────────────────────────────
    // NOTIFICATIONS
    // ────────────────────────────────────────────────

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, BdCloudApp.VPN_CHANNEL_ID)
            .setContentTitle("BDCLOUD VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(BdCloudApp.VPN_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }
}
