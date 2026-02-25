package org.bdcloud.clash.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.bdcloud.clash.R
import org.bdcloud.clash.api.PaymentHistoryItem

class PaymentHistoryAdapter(
    private val payments: List<PaymentHistoryItem>
) : RecyclerView.Adapter<PaymentHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPlan: TextView = view.findViewById(android.R.id.text1)
        val textDetails: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = MaterialCardView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(8))
            }
            setCardBackgroundColor(context.getColor(R.color.surface))
            radius = dpToPx(12).toFloat()
            cardElevation = dpToPx(1).toFloat()
            setContentPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val plan = TextView(parent.context).apply {
            id = android.R.id.text1
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val details = TextView(parent.context).apply {
            id = android.R.id.text2
            textSize = 12f
            setTextColor(context.getColor(R.color.text_muted))
        }

        layout.addView(plan)
        layout.addView(details)
        card.addView(layout)

        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val payment = payments[position]
        holder.textPlan.text = "${payment.planName ?: "Plan #${payment.id}"} • ৳${payment.amount.toInt()}"
        holder.textDetails.text = "${payment.status} • ${payment.provider} • ${payment.createdAt ?: "—"}"
    }

    override fun getItemCount() = payments.size

    private fun dpToPx(dp: Int): Int = (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
