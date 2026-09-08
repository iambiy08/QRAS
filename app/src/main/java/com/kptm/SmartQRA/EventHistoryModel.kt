package com.kptm.SmartQRA

data class EventHistoryModel(
    val eventId: String = "",
    val shortId: String = "",
    val eventName: String = "",
    val date: String = "",
    val attendedCount: Int = 0,
    val maxPeople: Int = -1,
    val status: String = "Active",
    val timestamp: Long = 0L
)