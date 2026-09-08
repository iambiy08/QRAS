package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kptm.SmartQRA.databinding.ActivityAdminDashBinding

class AdminDash : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var historyAdapter: DashboardHistoryAdapter
    private val historyList = mutableListOf<EventHistoryModel>()

    private lateinit var recentEventsAdapter: RecentEventsAdapter
    private val recentEventsList = mutableListOf<EventHistoryModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        listenForRealtimeUpdates()

        binding.btnCreateEvent.setOnClickListener {
            startActivity(Intent(this, CreateEvent::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_dashboard

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true

                R.id.nav_show_qr -> {
                    openActiveQrDisplay()
                    true
                }

                R.id.nav_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    true
                }

                else -> false
            }
        }
    }

    private fun setupRecyclerViews() {
        // "Recent Events" = currently Active events, tap to view/re-open the live QR
        recentEventsAdapter = RecentEventsAdapter(recentEventsList) { clickedEvent ->
            openActiveQrDisplay()
        }
        binding.rvRecentEvents.apply {
            layoutManager = LinearLayoutManager(this@AdminDash)
            adapter = recentEventsAdapter
        }

        // "Attendance History" = Ended events, tap to view that event's report
        historyAdapter = DashboardHistoryAdapter(historyList) { clickedEvent ->
            val intent = Intent(this, ReportsActivity::class.java).apply {
                putExtra("EVENT_ID", clickedEvent.eventId)
            }
            startActivity(intent)
        }
        binding.rvAttendanceHistory.apply {
            layoutManager = LinearLayoutManager(this@AdminDash)
            adapter = historyAdapter
        }
    }

    private fun listenForRealtimeUpdates() {
        db.collection("events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                recentEventsList.clear()
                historyList.clear()

                snapshot?.documents?.forEach { doc ->
                    val status = doc.getString("status") ?: "Ended"
                    val model = EventHistoryModel(
                        eventId = doc.id,
                        eventName = doc.getString("eventName") ?: "Unnamed Event",
                        date = doc.getString("date") ?: "N/A",
                        attendedCount = doc.getLong("attendedCount")?.toInt() ?: 0,
                        maxPeople = doc.getLong("maxPeople")?.toInt() ?: -1,
                        status = status,
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )

                    if (status.equals("Active", ignoreCase = true)) {
                        recentEventsList.add(model)
                    } else {
                        historyList.add(model)
                    }
                }

                recentEventsAdapter.notifyDataSetChanged()
                historyAdapter.notifyDataSetChanged()
            }
    }

    private fun openActiveQrDisplay() {
        Toast.makeText(this, "Checking active events...", Toast.LENGTH_SHORT).show()

        db.collection("events")
            .whereEqualTo("status", "Active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No active event found.", Toast.LENGTH_LONG).show()
                    binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
                    return@addOnSuccessListener
                }

                val activeDoc = snapshot.documents[0]

                val targetEndTime = activeDoc.getLong("targetEndTime") ?: System.currentTimeMillis()
                val remainingMillis = targetEndTime - System.currentTimeMillis()
                val remainingMinutes = if (remainingMillis > 0) remainingMillis / 60000L else 1L
                val maxPeople = activeDoc.getLong("maxPeople")?.toInt() ?: -1

                val intent = Intent(this, QRDisplay::class.java).apply {
                    putExtra("EVENT_ID", activeDoc.id)
                    putExtra("EVENT_NAME", activeDoc.getString("eventName") ?: "Session QR")
                    putExtra("LOCATION", activeDoc.getString("location") ?: "N/A")
                    putExtra("MANAGED_BY", activeDoc.getString("managedBy") ?: "N/A")
                    putExtra("DATE", activeDoc.getString("date") ?: "")
                    putExtra("DURATION_MIN", remainingMinutes)
                    putExtra("MAX_PEOPLE", maxPeople)
                }
                startActivity(intent)
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Database Read Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
    }
}