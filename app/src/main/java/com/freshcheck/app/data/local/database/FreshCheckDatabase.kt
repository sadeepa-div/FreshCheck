package com.freshcheck.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.freshcheck.app.data.local.converter.DatabaseConverters
import com.freshcheck.app.data.local.dao.*
import com.freshcheck.app.data.local.entity.*

@Database(
    entities = [FoodItemEntity::class, ScanRecordEntity::class, FoodOutcomeEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class FreshCheckDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun foodOutcomeDao(): FoodOutcomeDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "freshcheck.db"
        @Volatile private var instance: FreshCheckDatabase? = null

        fun getInstance(context: Context): FreshCheckDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, FreshCheckDatabase::class.java, DATABASE_NAME
            ).build().also { instance = it }
        }
    }
}
