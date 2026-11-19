package com.freshcheck.app.reminders

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Private durable preferences; commits occur on the coordinator's IO dispatcher, never in a Composable. */
class ReminderPreferencesStore(context: Context, name: String = "freshcheck_reminder_preferences") : ReminderSettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun current() = ReminderPreferences(prefs.getBoolean("notifications", false), prefs.getBoolean("rechecks", true))
    override val changes = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(current())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    override suspend fun set(value: ReminderPreferences) {
        check(prefs.edit().putBoolean("notifications", value.notifications).putBoolean("rechecks", value.rechecks).commit()) {
            "Unable to persist reminder preferences."
        }
    }
    fun permissionAsked() = prefs.getBoolean("permission_asked", false)
    suspend fun markPermissionAsked() {
        check(prefs.edit().putBoolean("permission_asked", true).commit())
    }
}
