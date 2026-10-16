package com.kptm.SmartQRA

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kptm.SmartQRA.databinding.ActivityReportsBinding
import java.io.OutputStreamWriter

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val db = FirebaseFirestore.getInstance()

    private var attendanceList = mutableListOf<AttendanceRecord>()
    private var eventOverviewList = listOf<EventHistoryModel>()
    private var eventId: String? = null

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) writePdfToUri(uri) }

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
                Toast.makeText(this, "Open a specific event's report to export", Toast.LENGTH_LONG).show()
            } else {
                createPdfLauncher.launch("Attendance_Report_${System.currentTimeMillis()}.pdf")
            }
        }

        binding.btnExportExcel.setOnClickListener {
            if (attendanceList.isEmpty()) {
                Toast.makeText(this, "Open a specific event's report to export", Toast.LENGTH_LONG).show()
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

    private fun loadSingleEventReport(id: String) {
        db.collection("events").document(id).get()
            .addOnSuccessListener { eventDoc ->
                val eventName = eventDoc.getString("eventName") ?: "Event"
                val shortId = eventDoc.getString("shortId") ?: "N/A"
                val invitedEmails = (eventDoc.get("invitedEmails") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                binding.tvReportsHeader.text = "$eventName (ID: $shortId)"
                binding.tvSubHeader.text = "Attendance Report"

                loadAttendeesWithAbsentees(id, eventName, shortId, invitedEmails)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * STEP 1: load every user and link all their identifiers (doc id, uid, userId, email)
     *         into one identity group.
     */
    private fun loadAttendeesWithAbsentees(
        id: String,
        eventName: String,
        eventShortId: String,
        invitedEmails: List<String>
    ) {
        db.collection("users").get()
            .addOnSuccessListener { usersSnapshot ->
                val graph = IdentityGraph()
                val displayNames = HashMap<String, String>() // any alias -> real name

                usersSnapshot.documents.forEach { uDoc ->
                    val tokens = listOf(
                        uDoc.id,
                        uDoc.getString("uid"),
                        uDoc.getString("userId"),
                        uDoc.getString("email")
                    ).map { IdentityUtils.normalize(it) }
                        .filter { it.isNotEmpty() }

                    graph.union(tokens)

                    val name = uDoc.getString("name")?.trim().orEmpty()
                    if (name.isNotEmpty()) tokens.forEach { displayNames[it] = name }
                }

                buildReport(id, eventName, eventShortId, invitedEmails, graph, displayNames)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users map: ${it.message}", Toast.LENGTH_SHORT).show()
                // Still show attendees; only exact-token matching will be available.
                buildReport(id, eventName, eventShortId, invitedEmails, IdentityGraph(), emptyMap())
            }
    }

    /**
     * STEP 2: load attendees, link their tokens into the same graph,
     * STEP 3: add a "Not Attended" row only for invited people who are NOT in a present group.
     */
    private fun buildReport(
        id: String,
        eventName: String,
        eventShortId: String,
        invitedEmails: List<String>,
        graph: IdentityGraph,
        displayNames: Map<String, String>
    ) {
        // No orderBy() here: Firestore silently drops documents that lack the ordered field.
        // We sort on the device instead so every scanned attendee is always included.
        db.collection("events").document(id).collection("attendees")
            .get()
            .addOnSuccessListener { snapshot ->
                attendanceList.clear()

                val docs = snapshot.documents.sortedBy { scanTimeMillis(it) }
                val attendeeTokenGroups = mutableListOf<List<String>>()

                docs.forEach { doc ->
                    val rawUserId = doc.getString("userId")?.trim().orEmpty()
                    val rawEmail = doc.getString("email")?.trim().orEmpty()

                    val tokens = listOf(doc.id, rawUserId, rawEmail)
                        .map { IdentityUtils.normalize(it) }
                        .filter { it.isNotEmpty() }

                    // Even if the users collection is missing something, the attendee's own
                    // uid + userId + email are linked together here.
                    graph.union(tokens)
                    attendeeTokenGroups.add(tokens)

                    attendanceList.add(
                        AttendanceRecord(
                            eventId = id,
                            eventShortId = eventShortId,
                            eventName = eventName,
                            userName = doc.getString("userName") ?: "Unknown",
                            userId = if (rawUserId.isNotEmpty()) rawUserId
                            else if (rawEmail.isNotEmpty()) rawEmail
                            else "N/A",
                            time = doc.getString("timeDisplay") ?: "-",
                            status = doc.getString("status") ?: "Present"
                        )
                    )
                }

                // Must be computed AFTER all unions are done
                val presentGroups: Set<String> = attendeeTokenGroups
                    .flatten()
                    .map { graph.find(it) }
                    .toHashSet()

                val listedAbsentGroups = HashSet<String>()

                invitedEmails.forEach { rawItem ->
                    val invitedToken = IdentityUtils.normalize(rawItem)
                    if (invitedToken.isEmpty()) return@forEach

                    val group = graph.find(invitedToken)

                    // Same person as someone who already scanned (by uid, userId OR email) -> skip
                    if (group in presentGroups) return@forEach

                    // Same absent person invited twice (e.g. by email AND by ID) -> one row only
                    if (!listedAbsentGroups.add(group)) return@forEach

                    attendanceList.add(
                        AttendanceRecord(
                            eventId = id,
                            eventShortId = eventShortId,
                            eventName = eventName,
                            userName = displayNames[invitedToken]
                                ?: if (invitedToken.contains("@")) invitedToken.substringBefore("@") else invitedToken,
                            userId = invitedToken,
                            time = "-",
                            status = "Not Attended"
                        )
                    )
                }

                Log.d(
                    "SmartQRA-Report",
                    "attendees=${docs.size}, invited=${invitedEmails.size}, " +
                            "absentRows=${listedAbsentGroups.size}, invitedRaw=$invitedEmails"
                )

                val totalAttended = docs.size
                val absentCount = listedAbsentGroups.size

                binding.tvRecordCount.text =
                    "Total Records: ${attendanceList.size} (Attended: $totalAttended, Absent: $absentCount)"
                binding.rvReportsList.adapter = AttendanceAdapter(attendanceList)
                binding.tvEmptyState.visibility =
                    if (attendanceList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load attendees: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** scanTime may be stored as a Long or a Firestore Timestamp; docs without it sort last. */
    private fun scanTimeMillis(doc: DocumentSnapshot): Long =
        when (val v = doc.get("scanTime")) {
            is Number -> v.toLong()
            is Timestamp -> v.toDate().time
            else -> Long.MAX_VALUE
        }

    private fun loadAllEndedEventsOverview() {
        binding.tvReportsHeader.text = "Attendance Reports"
        binding.tvSubHeader.text = "Tap an event to view its full report"
        binding.tilSearch.visibility = android.view.View.VISIBLE

        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        db.collection("events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                eventOverviewList = snapshot.documents
                    .filter { doc ->
                        val createdBy = doc.getString("createdBy") ?: ""
                        val status = doc.getString("status") ?: "Ended"
                        createdBy == currentUid && status.equals("Ended", ignoreCase = true)
                    }
                    .map { doc ->
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
    }

    private fun filterEventOverview(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) eventOverviewList else {
            eventOverviewList.filter {
                it.eventName.contains(trimmed, ignoreCase = true) || it.shortId.contains(trimmed, ignoreCase = true)
            }
        }
        renderEventOverviewList(filtered)
    }

    private fun renderEventOverviewList(list: List<EventHistoryModel>) {
        binding.tvRecordCount.text = "Summary Records (Total: ${list.size})"
        binding.rvReportsList.adapter = ReportEventAdapter(
            items = list,
            onClick = { clicked ->
                val intent = android.content.Intent(this, ReportsActivity::class.java).apply {
                    putExtra("EVENT_ID", clicked.eventId)
                }
                startActivity(intent)
            },
            onDelete = { toDelete -> confirmDeleteEvent(toDelete) }
        )
        binding.tvEmptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmDeleteEvent(event: EventHistoryModel) {
        AlertDialog.Builder(this)
            .setTitle("Delete report")
            .setMessage("Permanently delete \"${event.eventName}\" and all of its attendance records? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteEventWithAttendees(event) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Deletes every attendee record first (subcollections are NOT removed with the parent), then the event. */
    private fun deleteEventWithAttendees(event: EventHistoryModel) {
        val eventRef = db.collection("events").document(event.eventId)

        eventRef.collection("attendees").get()
            .addOnSuccessListener { snap ->
                // Firestore batches are limited to 500 operations
                val commits = snap.documents.chunked(400).map { chunk ->
                    db.batch().apply { chunk.forEach { delete(it.reference) } }.commit()
                }
                Tasks.whenAll(commits)
                    .addOnSuccessListener {
                        eventRef.delete()
                            .addOnSuccessListener {
                                eventOverviewList = eventOverviewList.filter { it.eventId != event.eventId }
                                filterEventOverview(binding.etSearch.text?.toString() ?: "")
                                Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to delete event: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete attendees: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load attendees: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun writePdfToUri(uri: Uri) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.textSize = 16f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("SmartQRA - Attendance Report", 40f, 40f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        var y = 70f

        for (record in attendanceList) {
            val text = "${record.userId} | ${record.userName} | ${record.time} | [${record.status}]"
            canvas.drawText(text, 40f, y, paint)
            y += 18f
            if (y > 800f) break
        }

        pdfDocument.finishPage(page)
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                pdfDocument.writeTo(out)
            }
            Toast.makeText(this, "PDF Exported Successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun writeCsvToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { writer ->
                    writer.write("User ID / Email,User Name,Time,Status\n")
                    for (record in attendanceList) {
                        writer.write(
                            "${csv(record.userId)},${csv(record.userName)},${csv(record.time)},${csv(record.status)}\n"
                        )
                    }
                }
            }
            Toast.makeText(this, "CSV Exported Successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Quote a CSV field if it contains a comma, quote or newline. */
    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
}

/**
 * Union-find over identity tokens. Every uid / userId / email that belongs to the same person
 * ends up with the same root, so "usr-35681", "biytest4@gmail.com" and the Firebase uid
 * all resolve to ONE group, even if the link is only visible through two different documents.
 */
private class IdentityGraph {
    private val parent = HashMap<String, String>()

    fun find(token: String): String {
        if (!parent.containsKey(token)) parent[token] = token
        var root = token
        while (parent[root] != root) root = parent[root]!!
        var cur = token
        while (cur != root) {          // path compression
            val next = parent[cur]!!
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(tokens: Collection<String>) {
        if (tokens.isEmpty()) return
        val first = find(tokens.first())
        tokens.forEach {
            val r = find(it)
            if (r != first) parent[r] = first
        }
    }
}