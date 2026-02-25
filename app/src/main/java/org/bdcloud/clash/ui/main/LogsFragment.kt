package org.bdcloud.clash.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.bdcloud.clash.R
import org.bdcloud.clash.core.BdCloudVpnService

class LogsFragment : Fragment() {

    private lateinit var textLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnClear: MaterialButton

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded) {
                refreshLogs()
                view?.postDelayed(this, 1500)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textLogs = view.findViewById(R.id.textLogs)
        scrollView = view.findViewById(R.id.scrollLogs)
        btnClear = view.findViewById(R.id.btnClearLogs)

        btnClear.setOnClickListener {
            BdCloudVpnService.clearLogs()
            textLogs.text = "Logs cleared."
        }

        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed(refreshRunnable, 1500)
    }

    override fun onPause() {
        super.onPause()
        view?.removeCallbacks(refreshRunnable)
    }

    private fun refreshLogs() {
        val logs = BdCloudVpnService.getLogs()
        if (logs.isEmpty()) {
            textLogs.text = "No logs yet. Connect VPN to see mihomo output."
        } else {
            textLogs.text = logs.takeLast(200).joinToString("\n")
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
