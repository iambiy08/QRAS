package com.kptm.SmartQRA

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kptm.SmartQRA.databinding.ActivityReportsBinding
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db = FirebaseFirestore.getInstance()

    private var attendanceList = listOf<AttendanceRecord>()
    private var eventOverviewList = listOf<EventHistoryModel>()

    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvReportsList.layoutManager = LinearLayoutManager(this)

        eventId = intent.getStringExtra("EVENT_ID")

        binding.btnExportPdf.setOnClickListener { generatePdfReport() }
        binding.btnExportExcel.setOnClickListener { generateExcelReport() }

        if (eventId != null) {
            loadSingleEventReport(eventId!!)
        } else {
            loadAllEndedEventsOverview()
        }
    }

    private fun loadSingleEventReport(id: String) {
        db.collection("events").document(id).get()
            .addOnSuccessListener { eventDoc ->
                val eventName = eventDoc.getString("eventName") ?: "Event"
                binding.tvReportsHeader.text = eventName
                binding.tvSubHeader.text = "Attendance for this session"

                loadAttendeesForEvent(id, eventName)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendeesForEvent(id: String, eventName: String) {
        db.collection("events").document(id).collection("attendees")
            .orderBy("scanTime", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                attendanceList = snapshot.documents.map { doc ->
                    AttendanceRecord(
                        eventId = id,
                        eventName = eventName,
                        studentName = doc.getString("studentName") ?: "Unknown",
                        time = doc.getString("timeDisplay") ?: "",
                        status = doc.getString("status") ?: "Present"
                    )
                }

                binding.tvRecordCount.text = "Summary Records (Total: ${attendanceList.size})"
                binding.rvReportsList.adapter = AttendanceAdapter(attendanceList)
                binding.tvEmptyState.text = "No attendance records yet"
                binding.tvEmptyState.visibility =
                    if (attendanceList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load attendees: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAllEndedEventsOverview() {
        binding.tvReportsHeader.text = "Attendance Reports"
        binding.tvSubHeader.text = "Tap an event to view its full report"

        db.collection("events")
            .whereEqualTo("status", "Ended")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                eventOverviewList = snapshot.documents.map { doc ->
                    EventHistoryModel(
                        eventId = doc.id,
                        eventName = doc.getString("eventName") ?: "Unnamed Event",
                        date = doc.getString("date") ?: "N/A",
                        attendedCount = doc.getLong("attendedCount")?.toInt() ?: 0,
                        maxPeople = doc.getLong("maxPeople")?.toInt() ?: -1,
                        status = doc.getString("status") ?: "Ended",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                binding.tvRecordCount.text = "Summary Records (Total: ${eventOverviewList.size})"
                binding.rvReportsList.adapter = DashboardHistoryAdapter(eventOverviewList) { clicked ->
                    val intent = android.content.Intent(this, ReportsActivity::class.java).apply {
                        putExtra("EVENT_ID", clicked.eventId)
                    }
                    startActivity(intent)
                }

                binding.tvEmptyState.text = "No ended events yet"
                binding.tvEmptyState.visibility =
                    if (eventOverviewList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load reports: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generatePdfReport() {
        if (attendanceList.isEmpty()) {
            Toast.makeText(this, "Open a specific event's report to export attendee PDF", Toast.LENGTH_LONG).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("SmartQRA - Attendance Report", 40f, 50f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = true
        var yPosition = 100f
        canvas.drawText("Event Name | Student Name | Time | Status", 40f, yPosition, paint)

        paint.isFakeBoldText = false
        yPosition += 25f

        for (record in attendanceList) {
            val line = "${record.eventName} - ${record.studentName} (${record.time}) [${record.status}]"
            canvas.drawText(line, 40f, yPosition, paint)
            yPosition += 20f
        }

        pdfDocument.finishPage(page)

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Attendance_Report.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "PDF saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun generateExcelReport() {
        if (attendanceList.isEmpty()) {
            Toast.makeText(this, "Open a specific event's report to export attendee CSV", Toast.LENGTH_LONG).show()
            return
        }

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Attendance_Report.csv")

        try {
            val writer = FileWriter(file)
            writer.append("Event ID,Event Name,Student Name,Time,Status\n")

            for (record in attendanceList) {
                writer.append("${record.eventId},${record.eventName},${record.studentName},${record.time},${record.status}\n")
            }

            writer.flush()
            writer.close()
            Toast.makeText(this, "Excel file saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save Excel file", Toast.LENGTH_SHORT).show()
        }
    }
}