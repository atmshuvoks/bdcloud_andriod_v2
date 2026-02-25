package org.bdcloud.clash.ui.main

import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.widget.ImageButton
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient
import org.bdcloud.clash.api.SessionKeyInfo
import org.bdcloud.clash.core.BdCloudVpnService
import org.bdcloud.clash.core.ClashManager
import org.bdcloud.clash.crypto.ProxyCrypto
import org.bdcloud.clash.util.TokenManager

class DashboardFragment : Fragment() {

    private lateinit var textStatus: TextView
    private lateinit var textProxyName: TextView
    private lateinit var btnConnect: ImageButton
    private lateinit var viewConnectGlow: View
    private lateinit var textConnectHint: TextView
    private lateinit var textEmail: TextView
    private lateinit var textSubscription: TextView
    private lateinit var textExpires: TextView
    private lateinit var chipGroupMode: ChipGroup
    private lateinit var textError: TextView

    private var isConnected = false
    private var selectedMode = ClashManager.ProxyMode.SELECT

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            connectVpn()
        } else {
            Toast.makeText(requireContext(), "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textStatus = view.findViewById(R.id.textStatus)
        textProxyName = view.findViewById(R.id.textProxyName)
        btnConnect = view.findViewById(R.id.btnConnect)
        viewConnectGlow = view.findViewById(R.id.viewConnectGlow)
        textConnectHint = view.findViewById(R.id.textConnectHint)
        textEmail = view.findViewById(R.id.textEmail)
        textSubscription = view.findViewById(R.id.textSubscription)
        textExpires = view.findViewById(R.id.textExpires)
        chipGroupMode = view.findViewById(R.id.chipGroupMode)
        textError = view.findViewById(R.id.textError)

        // Load user info
        val ctx = requireContext()
        textEmail.text = TokenManager.getEmail(ctx) ?: "—"
        textSubscription.text = getString(R.string.subscription_status, TokenManager.getSubscriptionStatus(ctx))
        val expires = TokenManager.getSubscriptionExpires(ctx)
        textExpires.text = if (expires.isNullOrBlank()) "" else getString(R.string.subscription_expires, expires)

        // Update VPN status
        updateConnectionStatus()

        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnectVpn()
            } else {
                loadProxiesAndConnect()
            }
        }

        chipGroupMode.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedMode = when {
                checkedIds.contains(R.id.chipSelect) -> ClashManager.ProxyMode.SELECT
                checkedIds.contains(R.id.chipAutoTest) -> ClashManager.ProxyMode.URL_TEST
                checkedIds.contains(R.id.chipLoadBalance) -> ClashManager.ProxyMode.LOAD_BALANCE
                checkedIds.contains(R.id.chipFallback) -> ClashManager.ProxyMode.FALLBACK
                else -> ClashManager.ProxyMode.SELECT
            }
            ClashManager.currentMode = selectedMode
        }
    }

    private val statusChecker = object : Runnable {
        override fun run() {
            if (isAdded) {
                updateConnectionStatus()
                view?.postDelayed(this, 2000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
        view?.postDelayed(statusChecker, 2000)
    }

    override fun onPause() {
        super.onPause()
        view?.removeCallbacks(statusChecker)
    }

    private fun updateConnectionStatus() {
        val wasConnected = isConnected
        isConnected = BdCloudVpnService.isActive()

        if (isConnected) {
            textStatus.text = getString(R.string.vpn_connected)
            textStatus.setTextColor(resources.getColor(R.color.status_connected, null))
            viewConnectGlow.setBackgroundResource(R.drawable.bg_connect_circle_connected)
            textConnectHint.text = "Tap to disconnect"
            textConnectHint.setTextColor(resources.getColor(R.color.status_connected, null))
            val proxyName = when (ClashManager.currentMode) {
                ClashManager.ProxyMode.SELECT -> ClashManager.selectedProxy?.name ?: "${ClashManager.proxies.size} proxies"
                ClashManager.ProxyMode.URL_TEST -> "Auto Best (${ClashManager.proxies.size} proxies)"
                ClashManager.ProxyMode.LOAD_BALANCE -> "Load Balance (${ClashManager.proxies.size} proxies)"
                ClashManager.ProxyMode.FALLBACK -> "Fallback (${ClashManager.proxies.size} proxies)"
            }
            textProxyName.text = proxyName
        } else {
            textStatus.text = getString(R.string.vpn_disconnected)
            textStatus.setTextColor(resources.getColor(R.color.status_disconnected, null))
            viewConnectGlow.setBackgroundResource(R.drawable.bg_connect_circle)
            textConnectHint.text = "Tap to connect"
            textConnectHint.setTextColor(resources.getColor(R.color.text_muted, null))
            textProxyName.text = "Tap connect to start"

            if (wasConnected) {
                textError.text = "VPN disconnected — check Logs tab for details"
                textError.visibility = View.VISIBLE
            }
        }
        btnConnect.isEnabled = true
    }

    private fun loadProxiesAndConnect() {
        textError.visibility = View.GONE
        btnConnect.isEnabled = false
        textStatus.text = getString(R.string.vpn_connecting)
        textStatus.setTextColor(resources.getColor(R.color.status_connecting, null))

        lifecycleScope.launch {
            try {
                val proxies = withContext(Dispatchers.IO) {
                    // Fetch session key
                    val keyResponse = ApiClient.service.getSessionKey()
                    if (!keyResponse.isSuccessful) {
                        throw Exception("Failed to get session key: ${keyResponse.code()}")
                    }
                    val keyBody = keyResponse.body()
                        ?: throw Exception("Empty session key response")
                    if (keyBody.success != true) {
                        throw Exception(keyBody.error?.message ?: "Session key failed")
                    }

                    val keyInfo = SessionKeyInfo(
                        sessionId = keyBody.sessionId ?: throw Exception("Missing sessionId"),
                        hint = keyBody.hint ?: "",
                        keyData = keyBody.keyData ?: throw Exception("Missing keyData"),
                        expiresIn = keyBody.expiresIn ?: 0
                    )

                    // Fetch encrypted proxies
                    val proxiesResponse = ApiClient.service.getProxies()
                    if (!proxiesResponse.isSuccessful) {
                        throw Exception("Failed to get proxies: ${proxiesResponse.code()}")
                    }
                    val proxiesBody = proxiesResponse.body()
                        ?: throw Exception("Empty proxies response")
                    if (proxiesBody.success != true) {
                        throw Exception(proxiesBody.error?.message ?: "Proxies request failed")
                    }

                    val payload = proxiesBody.payload
                        ?: throw Exception("No payload in proxies response")

                    // Decrypt
                    ProxyCrypto.decryptProxies(payload, keyInfo)
                }

                if (proxies.isEmpty()) {
                    showError("No proxies available")
                    updateConnectionStatus()
                    return@launch
                }

                // Generate config
                val configPath = ClashManager.generateConfig(
                    requireContext(), proxies, mode = selectedMode
                )

                textProxyName.text = "${proxies.size} proxies loaded"

                // Request VPN permission and connect
                requestVpnPermission()

            } catch (e: Exception) {
                showError(e.message ?: "Failed to load proxies")
                updateConnectionStatus()
            } finally {
                btnConnect.isEnabled = true
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        val configPath = ClashManager.getConfigPath()
        if (configPath.isBlank()) {
            showError("No config available")
            return
        }
        BdCloudVpnService.start(requireContext(), configPath)
        isConnected = true
        updateConnectionStatus()
    }

    private fun disconnectVpn() {
        BdCloudVpnService.stop(requireContext())
        isConnected = false
        updateConnectionStatus()
    }

    private fun showError(message: String) {
        textError.text = message
        textError.visibility = View.VISIBLE
    }
}
