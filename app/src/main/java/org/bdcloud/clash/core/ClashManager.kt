package org.bdcloud.clash.core

import android.content.Context
import android.util.Log
import org.bdcloud.clash.api.DecryptedProxy
import java.io.File
import java.util.UUID

/**
 * Manages mihomo/Clash core lifecycle:
 *   - Generate YAML config from decrypted proxies
 *   - Track selected proxy
 *   - Interface with mihomo controller API (127.0.0.1:9090)
 */
object ClashManager {

    private const val TAG = "ClashManager"

    const val CONTROLLER_PORT = 9090
    const val MIXED_PORT = 7893
    const val SOCKS_PORT = 7891
    const val HTTP_PORT = 7890

    private var controllerSecret: String = ""
    private var currentConfigPath: String = ""

    val proxies = mutableListOf<DecryptedProxy>()
    var selectedProxy: DecryptedProxy? = null
    var currentMode: ProxyMode = ProxyMode.SELECT

    fun getControllerSecret(): String {
        if (controllerSecret.isBlank()) {
            controllerSecret = UUID.randomUUID().toString().replace("-", "").take(16)
        }
        return controllerSecret
    }

    /**
     * Generate mihomo YAML config from decrypted proxy list.
     * Runs mihomo as a SOCKS5/HTTP proxy only (tun2socks handles VPN bridging).
     */
    fun generateConfig(
        context: Context,
        decryptedProxies: List<DecryptedProxy>,
        selectedProxyName: String? = null,
        mode: ProxyMode = ProxyMode.SELECT
    ): String {
        // Defensive copy — decryptedProxies might BE the same list as proxies
        val inputCopy = decryptedProxies.toList()
        proxies.clear()
        proxies.addAll(inputCopy)

        // Auto-select first proxy if none selected
        if (selectedProxy == null && inputCopy.isNotEmpty()) {
            selectedProxy = inputCopy.first()
        }

        val secret = getControllerSecret()
        val sb = StringBuilder()

        // Global settings — proxy mode only, NO TUN (tun2socks handles VPN bridging)
        sb.appendLine("mixed-port: $MIXED_PORT")
        sb.appendLine("socks-port: $SOCKS_PORT")
        sb.appendLine("port: $HTTP_PORT")
        sb.appendLine("allow-lan: true")
        sb.appendLine("bind-address: 127.0.0.1")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("external-controller: 127.0.0.1:$CONTROLLER_PORT")
        sb.appendLine("secret: \"$secret\"")
        sb.appendLine("unified-delay: true")
        sb.appendLine("geodata-mode: false")
        sb.appendLine("tcp-concurrent: true")
        sb.appendLine()

        // DNS — disabled, DnsRelay handles DNS on VPN gateway (172.19.0.1:53)
        sb.appendLine("dns:")
        sb.appendLine("  enable: false")
        sb.appendLine()

        // Proxies
        sb.appendLine("proxies:")
        for (proxy in decryptedProxies) {
            val safeName = sanitizeName(proxy.name)
            sb.appendLine("  - name: \"$safeName\"")
            sb.appendLine("    type: socks5")
            sb.appendLine("    server: ${proxy.server}")
            sb.appendLine("    port: ${proxy.port}")
            if (proxy.username.isNotBlank()) {
                sb.appendLine("    username: \"${proxy.username}\"")
            }
            if (proxy.password.isNotBlank()) {
                sb.appendLine("    password: \"${proxy.password}\"")
            }
            sb.appendLine("    udp: false")  // SOCKS5 proxies don't support UDP relay
            sb.appendLine()
        }

        // Proxy groups
        val proxyNames = decryptedProxies.map { "\"${sanitizeName(it.name)}\"" }
        val proxyNamesStr = proxyNames.joinToString(", ")

        sb.appendLine("proxy-groups:")

        when (mode) {
            ProxyMode.SELECT -> {
                sb.appendLine("  - name: \"BDCLOUD\"")
                sb.appendLine("    type: select")
                sb.appendLine("    proxies: [$proxyNamesStr]")
            }
            ProxyMode.URL_TEST -> {
                sb.appendLine("  - name: \"BDCLOUD\"")
                sb.appendLine("    type: url-test")
                sb.appendLine("    proxies: [$proxyNamesStr]")
                sb.appendLine("    url: http://www.gstatic.com/generate_204")
                sb.appendLine("    interval: 300")
            }
            ProxyMode.LOAD_BALANCE -> {
                sb.appendLine("  - name: \"BDCLOUD\"")
                sb.appendLine("    type: load-balance")
                sb.appendLine("    proxies: [$proxyNamesStr]")
                sb.appendLine("    url: http://www.gstatic.com/generate_204")
                sb.appendLine("    interval: 300")
                sb.appendLine("    strategy: round-robin")
            }
            ProxyMode.FALLBACK -> {
                sb.appendLine("  - name: \"BDCLOUD\"")
                sb.appendLine("    type: fallback")
                sb.appendLine("    proxies: [$proxyNamesStr]")
                sb.appendLine("    url: http://www.gstatic.com/generate_204")
                sb.appendLine("    interval: 300")
            }
        }
        sb.appendLine()

        // Rules — all traffic through proxy (DIRECT doesn't work on Android: netlink banned)
        sb.appendLine("rules:")
        sb.appendLine("  - MATCH,BDCLOUD")

        val configContent = sb.toString()

        // Write to internal storage
        val configDir = File(context.filesDir, "clash")
        configDir.mkdirs()
        val configFile = File(configDir, "config.yaml")
        configFile.writeText(configContent)
        currentConfigPath = configFile.absolutePath

        Log.d(TAG, "Config written to $currentConfigPath with ${inputCopy.size} proxies, mode=$mode")
        return currentConfigPath
    }

    fun getConfigPath(): String = currentConfigPath

    /**
     * Tell mihomo which proxy to use in SELECT mode via controller API.
     * Must call AFTER mihomo has started.
     */
    fun selectProxyInMihomo(proxyName: String) {
        try {
            val url = "http://127.0.0.1:$CONTROLLER_PORT/proxies/BDCLOUD"
            val secret = getControllerSecret()
            val json = "{\"name\":\"${sanitizeName(proxyName)}\"}"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val requestBody = okhttp3.RequestBody.create(
                null, json
            )

            val request = okhttp3.Request.Builder()
                .url(url)
                .put(requestBody)
                .addHeader("Authorization", "Bearer $secret")
                .build()

            val response = client.newCall(request).execute()
            Log.d(TAG, "Select proxy '$proxyName': ${response.code}")
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select proxy: ${e.message}")
        }
    }

    private fun sanitizeName(name: String): String {
        return name.replace("\"", "'").replace("\n", " ").trim()
    }

    enum class ProxyMode {
        SELECT,
        URL_TEST,
        LOAD_BALANCE,
        FALLBACK
    }
}
