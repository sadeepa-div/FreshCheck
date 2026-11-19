package com.freshcheck.app.reminders

import android.content.Context
import com.freshcheck.app.data.local.database.FreshCheckDatabase

/** One application-context graph shared by ViewModel and workers in the default process. */
class ReminderGraph private constructor(context: Context) {
    val store = RoomReminderStore(FreshCheckDatabase.getInstance(context))
    val settings = ReminderPreferencesStore(context)
    val scheduler = WorkManagerReminderScheduler(context)
    val notifications = AndroidReminderNotifications(context)
    val coordinator = ReminderCoordinator(store, settings, scheduler, notifications)
    val delivery = ReminderDelivery(store, settings, notifications)

    companion object {
        @Volatile private var instance: ReminderGraph? = null
        fun get(context: Context): ReminderGraph = instance ?: synchronized(this) {
            instance ?: ReminderGraph(context.applicationContext).also { instance = it }
        }
    }
}
