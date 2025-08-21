package com.example.patientsinquiry.data

import androidx.room.*

@Dao
interface QADao {
    @Insert fun insert(qa: QA)
    @Query("SELECT * FROM qa ORDER BY id DESC LIMIT 100")
    fun latest(): List<QA>
    @Query("SELECT * FROM qa WHERE lower(topic) LIKE :q OR lower(question) LIKE :q OR lower(answer) LIKE :q ORDER BY id DESC LIMIT 100")
    fun search(q: String): List<QA>
}

@Dao
interface ReminderDao {
    @Insert
    fun insert(rem: Reminder): Long
    @Query("SELECT * FROM reminders WHERE done=0 ORDER BY dueTs ASC")
    fun list(includeDone: Boolean = false): List<Reminder>
    @Query("UPDATE reminders SET done=1 WHERE id=:id")
    fun markDone(id: Int)
}
