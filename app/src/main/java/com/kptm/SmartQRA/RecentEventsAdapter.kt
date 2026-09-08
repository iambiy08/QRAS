package com.kptm.SmartQRA

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class RecentEventsAdapter(
    private val items: List<EventHistoryModel>,
    private val onItemClick: (EventHistoryModel) -> Unit
) : RecyclerView.Adapter<RecentEventsAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvTitle: android.widget.TextView = view.findViewById(R.id.tvEventTitle)
        val tvDate: android.widget.TextView = view.findViewById(R.id.tvEventDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.eventName
        holder.tvDate.text = "${item.date} • ${item.attendedCount} checked in • Active"
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}