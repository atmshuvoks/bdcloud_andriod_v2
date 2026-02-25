package org.bdcloud.clash.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient
import org.bdcloud.clash.api.DeviceIdHelper
import org.bdcloud.clash.core.BdCloudVpnService
import org.bdcloud.clash.util.TokenManager

class SettingsFragment : Fragment() {

    private lateinit var textEmail: TextView
    private lateinit var textSub: TextView
    private lateinit var textDeviceId: TextView
    private lateinit var textVersion: TextView
    private lateinit var btnResetDevice: MaterialButton
    private lateinit var btnLogout: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textEmail = view.findViewById(R.id.textSettingsEmail)
        textSub = view.findViewById(R.id.textSettingsSub)
        textDeviceId = view.findViewById(R.id.textDeviceId)
        textVersion = view.findViewById(R.id.textVersion)
        btnResetDevice = view.findViewById(R.id.btnResetDevice)
        btnLogout = view.findViewById(R.id.btnLogout)

        val ctx = requireContext()
        textEmail.text = TokenManager.getEmail(ctx) ?: "—"
        textSub.text = getString(R.string.subscription_status, TokenManager.getSubscriptionStatus(ctx))
        textDeviceId.text = DeviceIdHelper.getOrCreate(ctx)
        textVersion.text = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (e: Exception) { "2.0.0" }

        btnResetDevice.setOnClickListener { confirmResetDevice() }
        btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun confirmResetDevice() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Device Binding")
            .setMessage("This will unbind your account from this device. You'll need to login again. Continue?")
            .setPositiveButton("Reset") { _, _ -> performResetDevice() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performResetDevice() {
        btnResetDevice.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.service.resetDevice() }
                val body = response.body()
                if (body?.success == true) {
                    Toast.makeText(requireContext(), "Device reset successful", Toast.LENGTH_SHORT).show()
                    performLogout()
                } else {
                    Toast.makeText(requireContext(),
                        body?.error?.message ?: "Reset failed",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
            } finally {
                btnResetDevice.isEnabled = true
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Stop VPN if running
        if (BdCloudVpnService.isActive()) {
            BdCloudVpnService.stop(requireContext())
        }
        // Clear credentials
        TokenManager.clear(requireContext())
        // Go back to login
        (activity as? MainActivity)?.navigateToLogin()
    }
}
