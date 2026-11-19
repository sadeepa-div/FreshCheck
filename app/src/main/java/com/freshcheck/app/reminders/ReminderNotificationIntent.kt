package com.freshcheck.app.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.freshcheck.app.MainActivity

/** Data-only allowlisted navigation. No nested Intent, Parcelable, URI grants or arbitrary destination. */
object ReminderNotificationIntent {
    const val ACTION = "com.freshcheck.app.OPEN_RECHECK_FOOD"
    const val SCHEME = "freshcheck-internal"
    const val HOST = "recheck"

    fun create(context: Context, row: com.freshcheck.app.data.local.entity.ReminderEntity): Intent {
        val uri = Uri.Builder().scheme(SCHEME).authority(HOST)
            .appendPath(row.foodId).appendPath(row.id).appendPath(row.updatedAt.toEpochMilli().toString()).build()
        return Intent(context, MainActivity::class.java).setAction(ACTION).setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    fun parse(intent: Intent?): NotificationFoodTarget? {
        if (intent?.action != ACTION || intent.selector != null || intent.clipData != null) return null
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        if (intent.flags and grants != 0) return null
        return parseData(intent.dataString)
    }
    /** java.net.URI keeps the same allowlist testable on the JVM without Android mocks. */
    fun parseData(data: String?): NotificationFoodTarget? = runCatching {
        if (data == null || data.length > 512) return null
        val uri = java.net.URI(data)
        if (uri.scheme != SCHEME || uri.host != HOST || uri.rawQuery != null ||
            uri.rawFragment != null || uri.userInfo != null || uri.port != -1) return null
        val parts = uri.rawPath.removePrefix("/").split("/")
        if (parts.size != 3 || parts.any { it.contains('%') } ||
            !ReminderRules.realId(parts[0]) || !ReminderRules.realId(parts[1]) ||
            parts[2].toLongOrNull() == null) return null
        NotificationFoodTarget(parts[0], requireNotNull(data))
    }.getOrNull()
}
