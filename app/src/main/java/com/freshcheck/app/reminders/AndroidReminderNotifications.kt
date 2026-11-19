package com.freshcheck.app.reminders

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.freshcheck.app.R
import com.freshcheck.app.data.local.entity.*

class AndroidReminderNotifications(context: Context) : ReminderNotifications {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val compat = NotificationManagerCompat.from(context)

    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Food Re-check Reminders",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Optional check-in reminders for your saved food. Delivery times are approximate."
        })
    }
    override fun availability(): NotificationAvailability = try {
        createChannel()
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val app = compat.areNotificationsEnabled()
        val channel = manager.getNotificationChannel(CHANNEL)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
        NotificationAvailability(permission, app, channel, when {
            !permission -> "Notification permission is not granted."
            !app -> "Android notifications are disabled for FreshCheck."
            !channel -> "The Food Re-check Reminders channel is disabled or unavailable."
            else -> "Android notification permission and reminder channel are available. Delivery may be delayed."
        })
    } catch (failure: Exception) {
        NotificationAvailability(explanation = "Notification channel is unavailable. Check system settings.")
    }

    @android.annotation.SuppressLint("MissingPermission") // Dynamic availability check plus SecurityException boundary below.
    override fun post(row: ReminderEntity, food: FoodItemEntity): PostResult {
        if (!availability().allowed) return PostResult.Blocked
        return try {
            val pending = PendingIntent.getActivity(context, 0, ReminderNotificationIntent.create(context, row),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_recheck_notification)
                .setContentTitle("Food re-check reminder")
                .setContentText("Time to re-check your ${food.name.take(100)}.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Time to re-check your ${food.name.take(100)}. Check its condition manually; this is not an expiry prediction."))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            // Availability is checked immediately before notify; revocation can still race and is handled below.
            compat.notify(tag(row.id), NOTIFICATION_ID, notification)
            PostResult.Posted // Submitted to Android, not proof that it appeared or was seen.
        } catch (denied: SecurityException) { PostResult.Blocked }
        catch (failure: Exception) { PostResult.Uncertain }
    }
    override fun cancel(id: String) = compat.cancel(tag(id), NOTIFICATION_ID)
    override fun retainOnly(ids: Set<String>) {
        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach {
            val tag = it.tag ?: return@forEach
            if (tag.removePrefix(PREFIX) !in ids) compat.cancel(tag, it.id)
        }
    }
    companion object {
        const val CHANNEL = "freshcheck_recheck_reminders"
        const val PREFIX = "freshcheck-reminder:"
        const val NOTIFICATION_ID = 1 // tag + ID, rather than a collision-prone hash, identifies each reminder.
        fun tag(id: String) = PREFIX + id
    }
}
