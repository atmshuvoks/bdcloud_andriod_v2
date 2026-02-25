package org.bdcloud.clash.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient
import org.bdcloud.clash.api.DecryptedProxy
import org.bdcloud.clash.api.SessionKeyInfo
import org.bdcloud.clash.core.ClashManager
import org.bdcloud.clash.crypto.ProxyCrypto
import org.bdcloud.clash.ui.adapters.ProxyAdapter

class ProxyListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textError: TextView
    private lateinit var textEmpty: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnPingAll: MaterialButton

    private var adapter: ProxyAdapter? = null
    private val proxies = mutableListOf<DecryptedProxy>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_proxies, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerProxies)
        progressBar = view.findViewById(R.id.progressProxies)
        textError = view.findViewById(R.id.textError)
        textEmpty = view.findViewById(R.id.textEmpty)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnPingAll = view.findViewById(R.id.btnPingAll)

        adapter = ProxyAdapter(proxies) { proxy ->
            // Store the selected proxy in ClashManager
            ClashManager.selectedProxy = proxy
            Toast.makeText(requireContext(), "Selected: ${proxy.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        btnRefresh.setOnClickListener { loadProxies() }
        btnPingAll.setOnClickListener { pingAllProxies() }

        // Check if we already have proxies from ClashManager
        if (ClashManager.proxies.isNotEmpty()) {
            proxies.clear()
            proxies.addAll(ClashManager.proxies)
            adapter?.notifyDataSetChanged()

            // Highlight the selected proxy
            val selectedIdx = ClashManager.selectedProxy?.let { sel ->
                proxies.indexOfFirst { it.id == sel.id }
            } ?: -1
            if (selectedIdx >= 0) {
                adapter?.setSelectedPosition(selectedIdx)
            }

            textEmpty.visibility = if (proxies.isEmpty()) View.VISIBLE else View.GONE
        } else {
            loadProxies()
        }
    }

    private fun loadProxies() {
        progressBar.visibility = View.VISIBLE
        textError.visibility = View.GONE
        textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val keyResponse = ApiClient.service.getSessionKey()
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

                    val proxiesResponse = ApiClient.service.getProxies()
                    val proxiesBody = proxiesResponse.body()
                        ?: throw Exception("Empty proxies response")
                    if (proxiesBody.success != true) {
                        throw Exception(proxiesBody.error?.message ?: "Proxies request failed")
                    }

                    val payload = proxiesBody.payload
                        ?: throw Exception("No encrypted payload")

                    ProxyCrypto.decryptProxies(payload, keyInfo)
                }

                proxies.clear()
                proxies.addAll(result)

                // Also update ClashManager's proxy list
                ClashManager.proxies.clear()
                ClashManager.proxies.addAll(result)

                adapter?.notifyDataSetChanged()
                textEmpty.visibility = if (proxies.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                textError.text = e.message ?: "Failed to load proxies"
                textError.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun pingAllProxies() {
        btnPingAll.isEnabled = false
        btnPingAll.text = "Testing..."

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    proxies.map { proxy ->
                        try {
                            val start = System.currentTimeMillis()
                            val socket = java.net.Socket()
                            socket.connect(
                                java.net.InetSocketAddress(proxy.server, proxy.port),
                                5000
                            )
                            socket.close()
                            val elapsed = System.currentTimeMillis() - start
                            proxy.id to "${elapsed}ms"
                        } catch (e: Exception) {
                            proxy.id to "timeout"
                        }
                    }.toMap()
                }

                adapter?.updateLatencies(results)

            } catch (e: Exception) {
                // ignore
            } finally {
                btnPingAll.isEnabled = true
                btnPingAll.text = getString(R.string.test_latency)
            }
        }
    }
}
