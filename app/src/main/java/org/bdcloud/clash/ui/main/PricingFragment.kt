package org.bdcloud.clash.ui.main

import android.content.Intent
import android.net.Uri
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
import org.bdcloud.clash.api.Plan
import org.bdcloud.clash.api.PaymentHistoryItem
import org.bdcloud.clash.api.SsoRequest
import org.bdcloud.clash.ui.adapters.PlanAdapter
import org.bdcloud.clash.ui.adapters.PaymentHistoryAdapter

class PricingFragment : Fragment() {

    private lateinit var recyclerPlans: RecyclerView
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var progressHistory: ProgressBar
    private lateinit var textNoPlans: TextView
    private lateinit var textNoHistory: TextView
    private lateinit var btnManage: MaterialButton
    private lateinit var btnRefreshHistory: MaterialButton

    private val plans = mutableListOf<Plan>()
    private val history = mutableListOf<PaymentHistoryItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_pricing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerPlans = view.findViewById(R.id.recyclerPlans)
        recyclerHistory = view.findViewById(R.id.recyclerHistory)
        progressHistory = view.findViewById(R.id.progressHistory)
        textNoPlans = view.findViewById(R.id.textNoPlans)
        textNoHistory = view.findViewById(R.id.textNoHistory)
        btnManage = view.findViewById(R.id.btnManage)
        btnRefreshHistory = view.findViewById(R.id.btnRefreshHistory)

        recyclerPlans.layoutManager = LinearLayoutManager(requireContext())
        recyclerPlans.adapter = PlanAdapter(plans)

        recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        recyclerHistory.adapter = PaymentHistoryAdapter(history)

        btnManage.setOnClickListener { openPortalSso() }
        btnRefreshHistory.setOnClickListener { loadHistory() }

        loadPlans()
        loadHistory()
    }

    private fun loadPlans() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.service.getPlans() }
                val body = response.body()
                if (body?.success == true) {
                    plans.clear()
                    plans.addAll(body.plans ?: emptyList())
                    recyclerPlans.adapter?.notifyDataSetChanged()
                    textNoPlans.visibility = if (plans.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                textNoPlans.text = e.message ?: "Failed to load plans"
                textNoPlans.visibility = View.VISIBLE
            }
        }
    }

    private fun loadHistory() {
        progressHistory.visibility = View.VISIBLE
        textNoHistory.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.service.getPaymentHistory() }
                val body = response.body()
                if (body?.success == true) {
                    history.clear()
                    history.addAll(body.payments ?: emptyList())
                    recyclerHistory.adapter?.notifyDataSetChanged()
                    textNoHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                textNoHistory.text = e.message ?: "Failed to load history"
                textNoHistory.visibility = View.VISIBLE
            } finally {
                progressHistory.visibility = View.GONE
            }
        }
    }

    private fun openPortalSso() {
        btnManage.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.createPortalSso(SsoRequest("/pricing"))
                }
                val body = response.body()
                if (body?.success == true && !body.portalUrl.isNullOrBlank()) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(body.portalUrl)))
                } else {
                    Toast.makeText(requireContext(),
                        body?.error?.message ?: "Failed to create SSO link",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
            } finally {
                btnManage.isEnabled = true
            }
        }
    }
}
