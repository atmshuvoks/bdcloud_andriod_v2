package org.bdcloud.clash.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.bdcloud.clash.R
import org.bdcloud.clash.api.Plan

class PlanAdapter(
    private val plans: List<Plan>
) : RecyclerView.Adapter<PlanAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(android.R.id.text1)
        val textPrice: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = MaterialCardView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(8))
            }
            setCardBackgroundColor(context.getColor(R.color.surface_elevated))
            radius = dpToPx(12).toFloat()
            cardElevation = dpToPx(2).toFloat()
            setContentPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val name = TextView(parent.context).apply {
            id = android.R.id.text1
            textSize = 16f
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val price = TextView(parent.context).apply {
            id = android.R.id.text2
            textSize = 14f
            setTextColor(context.getColor(R.color.secondary))
        }

        layout.addView(name)
        layout.addView(price)
        card.addView(layout)

        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val plan = plans[position]
        holder.textName.text = "${plan.name} ${plan.badge?.let { "• $it" } ?: ""}"
        holder.textPrice.text = "৳${plan.price.toInt()} • ${plan.durationDays} days"
    }

    override fun getItemCount() = plans.size

    private fun dpToPx(dp: Int): Int = (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
