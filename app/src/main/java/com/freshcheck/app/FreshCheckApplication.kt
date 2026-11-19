package com.freshcheck.app

import android.app.Application
import com.freshcheck.app.reminders.ReminderReconcileWorker

class FreshCheckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // AndroidX Startup owns WorkManager initialization. Do not initialize it a second time.
        ReminderReconcileWorker.enqueue(this)
    }
}
