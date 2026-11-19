package com.freshcheck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshcheck.app.integration.FreshCheckRoute
import com.freshcheck.app.reminders.NotificationFoodTarget
import com.freshcheck.app.reminders.ReminderNotificationIntent
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notificationTarget = MutableStateFlow<NotificationFoodTarget?>(null)
    private var consumedToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumedToken = savedInstanceState?.getString("consumed_reminder")
        val restored = ReminderNotificationIntent.parseData(savedInstanceState?.getString("pending_reminder"))
        if (restored != null) notificationTarget.value = restored else receive(intent)
        enableEdgeToEdge()
        setContent {
            val target by notificationTarget.collectAsStateWithLifecycle()
            FreshCheckRoute(target) {
                consumedToken = notificationTarget.value?.token ?: consumedToken
                notificationTarget.value = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent) // Same allowlist for cold and warm notification taps.
    }
    private fun receive(intent: Intent?) {
        val target = ReminderNotificationIntent.parse(intent) ?: return
        if (target.token != consumedToken && target.token != notificationTarget.value?.token)
            notificationTarget.value = target
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("consumed_reminder", consumedToken)
        outState.putString("pending_reminder", notificationTarget.value?.token)
        super.onSaveInstanceState(outState)
    }
}
