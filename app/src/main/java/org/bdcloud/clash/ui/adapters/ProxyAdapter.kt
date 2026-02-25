package org.bdcloud.clash.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.bdcloud.clash.R
import org.bdcloud.clash.api.DecryptedProxy

class ProxyAdapter(
    private val proxies: List<DecryptedProxy>,
    private val onProxyClick: (DecryptedProxy) -> Unit
) : RecyclerView.Adapter<ProxyAdapter.ViewHolder>() {

    private var selectedPosition = -1
    private val latencies = mutableMapOf<Int, String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val textName: TextView = view.findViewById(R.id.textProxyName)
        val textLocation: TextView = view.findViewById(R.id.textProxyLocation)
        val textServer: TextView = view.findViewById(R.id.textProxyServer)
        val textLatency: TextView = view.findViewById(R.id.textLatency)
        val viewStatus: View = view.findViewById(R.id.viewStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proxy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val proxy = proxies[position]

        holder.textName.text = proxy.name
        holder.textLocation.text = proxy.location.replace("MikroTik", "Server")
        holder.textServer.text = "${proxy.server}:${proxy.port}"

        val latency = latencies[proxy.id]
        if (latency != null) {
            holder.textLatency.text = latency
            holder.textLatency.visibility = View.VISIBLE
            val color = when {
                latency.contains("timeout") -> R.color.status_disconnected
                latency.replace("ms", "").toIntOrNull()?.let { it < 200 } == true -> R.color.status_connected
                else -> R.color.status_connecting
            }
            holder.textLatency.setTextColor(holder.itemView.context.getColor(color))
        } else {
            holder.textLatency.visibility = View.GONE
        }

        // Highlight selected proxy with stroke
        val isSelected = position == selectedPosition
        holder.card.strokeWidth = if (isSelected) 4 else 0
        if (isSelected) {
            holder.card.strokeColor = holder.itemView.context.getColor(R.color.secondary)
        }

        holder.card.setOnClickListener {
            val prevPos = selectedPosition
            selectedPosition = holder.adapterPosition
            if (prevPos >= 0) notifyItemChanged(prevPos)
            notifyItemChanged(selectedPosition)
            onProxyClick(proxy)
        }
    }

    override fun getItemCount() = proxies.size

    fun setSelectedPosition(position: Int) {
        val prevPos = selectedPosition
        selectedPosition = position
        if (prevPos >= 0) notifyItemChanged(prevPos)
        if (selectedPosition >= 0) notifyItemChanged(selectedPosition)
    }

    fun updateLatencies(results: Map<Int, String>) {
        latencies.clear()
        latencies.putAll(results)
        notifyDataSetChanged()
    }
}
