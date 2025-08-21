package com.example.patientsinquiry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qa")
data class QA(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ts: String,
    val topic: String?,
    val question: String,
    val answer: String
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dueTs: Long,
    val text: String,
    val done: Boolean = false
)
