package com.example.patientsinquiry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QA::class, Reminder::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun qaDao(): QADao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(ctx, AppDatabase::class.java, "patients_inquiry.db").allowMainThreadQueries().build().also { INSTANCE = it }
            }
    }
}
