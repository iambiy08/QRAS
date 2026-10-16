package com.kptm.SmartQRA

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Admin "Attendance Reports" list: tap a row to open its report, tap the bin to delete it.
 */
class ReportEventAdapter(
    private val items: List<EventHistoryModel>,
    private val onClick: (EventHistoryModel) -> Unit,
    private val onDelete: (EventHistoryModel) -> Unit
) : RecyclerView.Adapter<ReportEventAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvReportEventName)
        val tvId: TextView = view.findViewById(R.id.tvReportId)
        val tvDate: TextView = view.findViewById(R.id.tvReportDate)
        val tvAttendance: TextView = view.findViewById(R.id.tvReportAttendance)
        val tvStatus: TextView = view.findViewById(R.id.tvReportStatus)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.eventName
        holder.tvId.text = "ID: ${item.shortId}"
        holder.tvDate.text = "Date: ${item.date}"
        holder.tvAttendance.text =
            if (item.maxPeople > 0) "${item.attendedCount} / ${item.maxPeople} Attended"
            else "${item.attendedCount} Attended"
        holder.tvStatus.text = item.status

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}