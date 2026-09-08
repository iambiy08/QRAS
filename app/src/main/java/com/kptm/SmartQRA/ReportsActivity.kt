package com.kptm.SmartQRA

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kptm.SmartQRA.databinding.ActivityReportsBinding
import java.io.OutputStreamWriter

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db = FirebaseFirestore.getInstance()

    private var attendanceList = listOf<AttendanceRecord>()
    private var eventOverviewList = listOf<EventHistoryModel>()

    private var eventId: String? = null

    // Launches the system "Save As" picker for a PDF file
    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) writePdfToUri(uri) }

    // Launches the system "Save As" picker for a CSV file
    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) writeCsvToUri(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvReportsList.layoutManager = LinearLayoutManager(this)

        eventId = intent.getStringExtra("EVENT_ID")

        binding.btnExportPdf.setOnClickListener {
            if (attendanceList.isEmpty()) {
                Toast.makeText(this, "Open a specific event's report to export attendee PDF", Toast.LENGTH_LONG).show()
            } else {
                createPdfLauncher.launch("Attendance_Report_${System.currentTimeMillis()}.pdf")
            }
        }

        binding.btnExportExcel.setOnClickListener {
            if (attendanceList.isEmpty()) {
                Toast.makeText(this, "Open a specific event's report to export attendee CSV", Toast.LENGTH_LONG).show()
            } else {
                createCsvLauncher.launch("Attendance_Report_${System.currentTimeMillis()}.csv")
            }
        }

        if (eventId != null) {
            loadSingleEventReport(eventId!!)
        } else {
            loadAllEndedEventsOverview()
        }
    }

    // ---------- MODE 1: Single event detail ----------
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

    // ---------- MODE 2: Overview of all ended events (with search) ----------
    private fun loadAllEndedEventsOverview() {
        binding.tvReportsHeader.text = "Attendance Reports"
        binding.tvSubHeader.text = "Tap an event to view its full report"
        binding.tilSearch.visibility = android.view.View.VISIBLE

        db.collection("events")
            .whereEqualTo("status", "Ended")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                eventOverviewList = snapshot.documents.map { doc ->
                    EventHistoryModel(
                        eventId = doc.id,
                        shortId = doc.getString("shortId") ?: "",
                        eventName = doc.getString("eventName") ?: "Unnamed Event",
                        date = doc.getString("date") ?: "N/A",
                        attendedCount = doc.getLong("attendedCount")?.toInt() ?: 0,
                        maxPeople = doc.getLong("maxPeople")?.toInt() ?: -1,
                        status = doc.getString("status") ?: "Ended",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                renderEventOverviewList(eventOverviewList)

                binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        filterEventOverview(s?.toString() ?: "")
                    }
                })
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load reports: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterEventOverview(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            eventOverviewList
        } else {
            eventOverviewList.filter { event ->
                event.eventName.contains(trimmed, ignoreCase = true) ||
                        event.shortId.contains(trimmed, ignoreCase = true)
            }
        }
        renderEventOverviewList(filtered)
    }

    private fun renderEventOverviewList(list: List<EventHistoryModel>) {
        binding.tvRecordCount.text = "Summary Records (Total: ${list.size})"
        binding.rvReportsList.adapter = DashboardHistoryAdapter(list) { clicked ->
            val intent = android.content.Intent(this, ReportsActivity::class.java).apply {
                putExtra("EVENT_ID", clicked.eventId)
            }
            startActivity(intent)
        }

        binding.tvEmptyState.text = "No matching events"
        binding.tvEmptyState.visibility =
            if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ---------- Export: writes to the Uri the user picked via the system file picker ----------
    private fun writePdfToUri(uri: Uri) {
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

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                pdfDocument.writeTo(out)
            }
            Toast.makeText(this, "PDF saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun writeCsvToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { writer ->
                    writer.write("Event ID,Event Name,Student Name,Time,Status\n")
                    for (record in attendanceList) {
                        writer.write("${record.eventId},${record.eventName},${record.studentName},${record.time},${record.status}\n")
                    }
                }
            }
            Toast.makeText(this, "Excel file saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save Excel file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}