package com.kptm.SmartQRA

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class DashboardHistoryAdapter(
    private val items: List<EventHistoryModel>,
    private val onItemClick: (EventHistoryModel) -> Unit
) : RecyclerView.Adapter<DashboardHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvName: android.widget.TextView = view.findViewById(R.id.tvHistoryEventName)
        val tvId: android.widget.TextView = view.findViewById(R.id.tvHistoryId)
        val tvDate: android.widget.TextView = view.findViewById(R.id.tvHistoryDate)
        val tvCount: android.widget.TextView = view.findViewById(R.id.tvAttendanceCount)
        val tvStatus: android.widget.TextView = view.findViewById(R.id.tvHistoryStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.eventName
        holder.tvId.text = "ID: ${item.shortId}"
        holder.tvDate.text = "Date: ${item.date}"

        val maxText = if (item.maxPeople > 0) item.maxPeople.toString() else "-"
        holder.tvCount.text = "${item.attendedCount} / $maxText Attended"

        holder.tvStatus.text = item.status
        if (item.status.equals("Active", ignoreCase = true)) {
            holder.tvStatus.setTextColor(Color.parseColor("#16A34A"))
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}