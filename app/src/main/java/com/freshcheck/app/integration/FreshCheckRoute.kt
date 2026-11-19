package com.freshcheck.app.integration

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshcheck.app.navigation.FreshCheckApp
import com.freshcheck.app.reminders.*
import kotlinx.coroutines.*

@Composable
fun FreshCheckRoute(notificationTarget: NotificationFoodTarget? = null, onNotificationConsumed: () -> Unit = {}) {
    val localContext = LocalContext.current
    val context = localContext.applicationContext
    val activity = localContext as? ComponentActivity
    val factory = remember(context) { FreshCheckViewModelFactory(context) }
    val owner: FreshCheckViewModel = viewModel(factory = factory)
    val data by owner.uiState.collectAsStateWithLifecycle()
    val reminderData by owner.reminderUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val base = remember(owner, scope, data.inventoryConnected) { owner.integrationActions(scope, data.inventoryConnected) }
    val notifications = remember(context) { AndroidReminderNotifications(context) }
    val preferences = remember(context) { ReminderPreferencesStore(context) }
    var permissionDialog by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMessage = if (granted) null else "Permission was not granted. Your reminder definitions are retained; notification delivery is blocked."
        owner.refreshReminders()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) owner.refreshReminders()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openSystemSettings() {
        val availability = notifications.availability()
        val intent = when {
            !availability.permission -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            !availability.channelEnabled -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidReminderNotifications.CHANNEL)
            else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { localContext.startActivity(intent) }.onFailure {
            permissionMessage = "Android notification settings could not be opened on this device."
        }
    }
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !notifications.availability().permission) permissionDialog = true
        else { owner.refreshReminders(); if (!notifications.availability().allowed) openSystemSettings() }
    }
    val reminderActions = base.reminders.copy(
        save = { draft, done -> scope.launch {
            val result = owner.saveReminder(draft)
            ensureActive()
            done(result)
            if (result.permissionNeeded && reminderData.preferences.enabled) askPermission()
        } },
        setPreferences = { value -> scope.launch {
            val result = owner.setReminderPreferences(value)
            if (result.saved && value.enabled) askPermission()
        } },
        requestPermission = ::askPermission,
        openSettings = ::openSystemSettings
    )
    val actions = base.copy(reminders = reminderActions, onFoodOpened = {
        owner.foodOpened()
        onNotificationConsumed()
    })
    LaunchedEffect(notificationTarget?.token, data.inventoryConnected) {
        val target = notificationTarget
        if (target != null && data.inventoryConnected && !owner.openNotificationFood(target)) onNotificationConsumed()
    }
    FreshCheckApp(data = data.copy(reminderData = reminderData), actions = actions, production = true)

    if (permissionDialog) {
        val permanentlyBlocked = Build.VERSION.SDK_INT >= 33 && preferences.permissionAsked() &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        AlertDialog(onDismissRequest = { permissionDialog = false },
            title = { Text("Allow food re-check notifications?") },
            text = { Text(if (permanentlyBlocked)
                "Android is not offering another permission prompt. Open app settings to allow notifications. Saved reminders are retained."
                else "FreshCheck uses notifications only for re-check reminders you save. Times are approximate and may be delayed by Android. You can disable reminders at any time.") },
            confirmButton = { TextButton({
                permissionDialog = false
                if (permanentlyBlocked) openSystemSettings()
                else if (Build.VERSION.SDK_INT >= 33) scope.launch {
                    try {
                        withContext(Dispatchers.IO) { preferences.markPermissionAsked() }
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { permissionMessage = "The permission request could not start. Your reminder definition is retained." }
                }
            }) { Text(if (permanentlyBlocked) "Open app settings" else "Continue") } },
            dismissButton = { TextButton({ permissionDialog = false }) { Text("Not now") } })
    }
    permissionMessage?.let { message ->
        AlertDialog(onDismissRequest = { permissionMessage = null }, title = { Text("Notification availability") },
            text = { Text(message) },
            confirmButton = { TextButton({ permissionMessage = null }) { Text("OK") } },
            dismissButton = { TextButton({ permissionMessage = null; openSystemSettings() }) { Text("System settings") } })
    }
}
