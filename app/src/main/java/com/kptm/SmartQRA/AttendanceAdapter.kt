package com.kptm.SmartQRA

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class AttendanceAdapter(
    private val items: List<AttendanceRecord>
) : RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: android.widget.TextView = view.findViewById(R.id.tvStudentName)
        val tvEventName: android.widget.TextView = view.findViewById(R.id.tvEventName)
        val tvStatus: android.widget.TextView = view.findViewById(R.id.tvStatus)
        val tvTime: android.widget.TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvStudentName.text = item.studentName
        holder.tvEventName.text = item.eventName
        holder.tvStatus.text = item.status
        holder.tvTime.text = item.time
    }

    override fun getItemCount(): Int = items.size
}

data class AttendanceRecord(
    val eventId: String = "",
    val eventName: String = "",
    val studentName: String = "",
    val time: String = "",
    val status: String = ""
)