package com.freshcheck.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Only protected system actions; this component is not exported. No app-controlled broadcast commands. */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            ReminderReconcileWorker.enqueue(context, force = true)
        }
    }
}
