package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityAttendanceHistoryBinding

class AttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val fullList = mutableListOf<EventHistoryModel>()
    private val displayList = mutableListOf<EventHistoryModel>()
    private val attendeeRefsByEventId = mutableMapOf<String, DocumentReference>()
    private lateinit var adapter: DashboardHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSwipeToHide()
        setupSearchBar()
        binding.btnClearAllHistory.setOnClickListener { clearAllHistory() }
        loadHistoryData()
    }

    private fun setupRecyclerView() {
        adapter = DashboardHistoryAdapter(displayList) { clickedEvent ->
            val intent = Intent(this, ReportsActivity::class.java).apply {
                putExtra("EVENT_ID", clickedEvent.eventId)
            }
            startActivity(intent)
        }

        binding.rvAttendanceHistory.apply {
            layoutManager = LinearLayoutManager(this@AttendanceHistoryActivity)
            adapter = this@AttendanceHistoryActivity.adapter
        }
    }

    private fun setupSwipeToHide() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= displayList.size) return

                val item = displayList[position]
                hideOneEvent(
                    item.eventId,
                    onDone = {
                        displayList.removeAt(position)
                        fullList.removeAll { it.eventId == item.eventId }
                        adapter.notifyItemRemoved(position)
                        updateEmptyState()
                    },
                    onFail = { adapter.notifyItemChanged(position) } // kad balik ke tempat asal
                )
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvAttendanceHistory)
    }

    private fun hideOneEvent(eventId: String, onDone: () -> Unit, onFail: () -> Unit) {
        val ref = attendeeRefsByEventId[eventId]
        if (ref == null) {
            onFail()
            return
        }
        ref.update("hiddenByUser", true)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener {
                onFail()
                Toast.makeText(this, "Failed to hide: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearAllHistory() {
        if (fullList.isEmpty()) {
            Toast.makeText(this, "History is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear All History")
            .setMessage("Hide all attendance history? This won't delete the actual records, just hide them from your view.")
            .setPositiveButton("Clear All") { _, _ ->
                // Batch Firestore had 500 operasi, jadi pecahkan kepada 400
                val refs = fullList.mapNotNull { attendeeRefsByEventId[it.eventId] }
                val commits = refs.chunked(400).map { chunk ->
                    db.batch().apply {
                        chunk.forEach { update(it, "hiddenByUser", true) }
                    }.commit()
                }
                Tasks.whenAll(commits)
                    .addOnSuccessListener {
                        fullList.clear()
                        displayList.clear()
                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSearchBar() {
        binding.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterHistory(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterHistory(query: String) {
        displayList.clear()

        if (query.isEmpty()) {
            displayList.addAll(fullList)
        } else {
            val searchPattern = query.lowercase().trim()
            for (item in fullList) {
                if (item.eventName.lowercase().contains(searchPattern) ||
                    item.eventId.lowercase().contains(searchPattern) ||
                    item.shortId.lowercase().contains(searchPattern) ||
                    item.date.lowercase().contains(searchPattern)
                ) {
                    displayList.add(item)
                }
            }
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (displayList.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvAttendanceHistory.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvAttendanceHistory.visibility = View.VISIBLE
        }
    }

    private fun loadHistoryData() {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        db.collectionGroup("attendees")
            .whereEqualTo("userUid", currentUid)
            .get()
            .addOnSuccessListener { attendeeSnapshot ->
                attendeeRefsByEventId.clear()

                // Ambil rekod yang TAK disorok sahaja
                val visibleAttendeeDocs = attendeeSnapshot.documents.filter {
                    it.getBoolean("hiddenByUser") != true
                }

                if (visibleAttendeeDocs.isEmpty()) {
                    fullList.clear()
                    filterHistory(binding.etSearchHistory.text.toString())
                    return@addOnSuccessListener
                }

                // Map eventId -> attendee DocumentReference (untuk swipe/clear)
                visibleAttendeeDocs.forEach { doc ->
                    val eventId = doc.reference.parent.parent?.id
                    if (eventId != null) attendeeRefsByEventId[eventId] = doc.reference
                }

                val eventRefs = visibleAttendeeDocs.mapNotNull { it.reference.parent.parent }
                val eventFetchTasks = eventRefs.map { it.get() }

                Tasks.whenAllSuccess<DocumentSnapshot>(eventFetchTasks)
                    .addOnSuccessListener { eventDocs ->
                        fullList.clear()
                        for (doc in eventDocs) {
                            if (!doc.exists()) continue
                            val status = doc.getString("status") ?: "Ended"
                            val model = EventHistoryModel(
                                eventId = doc.id,
                                shortId = doc.getString("shortId") ?: "",
                                eventName = doc.getString("eventName") ?: "Unnamed Event",
                                date = doc.getString("date") ?: "N/A",
                                attendedCount = doc.getLong("attendedCount")?.toInt() ?: 0,
                                maxPeople = doc.getLong("maxPeople")?.toInt() ?: -1,
                                status = status,
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                            fullList.add(model)
                        }
                        fullList.sortByDescending { it.timestamp }
                        filterHistory(binding.etSearchHistory.text.toString())
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load event details: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to load history: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}