package com.freshcheck.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freshcheck.app.ui.components.Panel

/** Injectable platform boundary; tests never change the user's real permission or preferences. */
internal interface CameraPermissionAccess {
    fun snapshot(): CameraPermissionSnapshot
    fun setRequestedBefore(value: Boolean)
    fun openSettings(): Boolean
}

private class AndroidCameraPermissionAccess(context: Context) : CameraPermissionAccess {
    private val activity = context.cameraPermissionActivity()
    private val app = context.applicationContext
    // Only request history is stored. Android remains authoritative for the current grant.
    private val preferences = app.getSharedPreferences("camera_permission_history", Context.MODE_PRIVATE)

    override fun snapshot() = CameraPermissionSnapshot(
        hasCamera = app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        granted = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        showRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false,
        requestedBefore = preferences.getBoolean("requested_before", false),
        hasActivity = activity != null
    )

    override fun setRequestedBefore(value: Boolean) {
        preferences.edit { putBoolean("requested_before", value) }
    }

    override fun openSettings(): Boolean = try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())
        if (activity != null) activity.startActivity(intent)
        else app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun Context.cameraPermissionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.cameraPermissionActivity() else null
    else -> null
}

class CameraPermissionHandler internal constructor(
    val status: CameraPermissionStatus,
    val requesting: Boolean,
    val showRationale: Boolean,
    val error: String?,
    val onAction: () -> Unit,
    val onConfirmRationale: () -> Unit,
    val onDismissRationale: () -> Unit
)

/** Requests are explicit actions only. Composition and ON_RESUME only read the current grant. */
@Composable
fun rememberCameraPermissionHandler(): CameraPermissionHandler {
    val context = LocalContext.current
    val access = remember(context) { AndroidCameraPermissionAccess(context) }
    return rememberCameraPermissionHandler(access)
}

@Composable
internal fun rememberCameraPermissionHandler(access: CameraPermissionAccess): CameraPermissionHandler {
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(access) { mutableStateOf(cameraPermissionStatus(access.snapshot())) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh(): CameraPermissionStatus {
        return cameraPermissionStatus(access.snapshot()).also {
            status = it
            if (it != CameraPermissionStatus.Denied) showRationale = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requesting = false
        error = null
        refresh()
    }

    fun requestPermission() {
        if (requesting) return
        val current = refresh()
        if (current != CameraPermissionStatus.NotRequested && current != CameraPermissionStatus.Denied) return
        val wasRequested = access.snapshot().requestedBefore
        requesting = true
        showRationale = false
        error = null
        access.setRequestedBefore(true)
        try {
            launcher.launch(Manifest.permission.CAMERA)
        } catch (_: IllegalStateException) {
            access.setRequestedBefore(wasRequested)
            requesting = false
            error = "Camera permission could not be requested. Reopen the scanner and try again."
            refresh()
        }
    }

    DisposableEffect(lifecycleOwner, access) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return CameraPermissionHandler(status, requesting, showRationale, error,
        onAction = {
            if (!requesting) {
                error = null
                when (cameraPermissionAction(refresh())) {
                    CameraPermissionAction.Request -> requestPermission()
                    CameraPermissionAction.Explain -> showRationale = true
                    CameraPermissionAction.Settings -> {
                        if (!access.openSettings()) {
                            error = "Unable to open app settings. Open Settings > Apps > FreshCheck > Permissions."
                        }
                    }
                    CameraPermissionAction.None -> Unit
                }
            }
        },
        onConfirmRationale = { requestPermission() },
        onDismissRationale = { showRationale = false }
    )
}

@Composable
fun CameraPermissionPanel(permission: CameraPermissionHandler, enabled: Boolean = true) {
    Panel(Modifier.fillMaxWidth()) {
        Text("Camera access", style = MaterialTheme.typography.titleMedium)
        Text(when (permission.status) {
            CameraPermissionStatus.NotRequested -> "Camera permission is needed for taking photos. You can use Gallery without it."
            CameraPermissionStatus.Granted -> "Camera permission granted. Live camera preview and capture are not connected yet."
            CameraPermissionStatus.Denied -> "Camera permission was denied. Gallery remains available; you can choose to try again."
            CameraPermissionStatus.SettingsRequired -> "Camera access is off and Android may not show another request. You can enable it in app settings or continue with Gallery."
            CameraPermissionStatus.NoCamera -> "No camera hardware detected. Gallery remains available."
            CameraPermissionStatus.Unavailable -> "Camera permission needs an active screen. Reopen the scanner to try again."
        })
        val actionLabel = when (permission.status) {
            CameraPermissionStatus.NotRequested -> "Allow camera"
            CameraPermissionStatus.Denied -> "Try camera permission again"
            CameraPermissionStatus.SettingsRequired -> "Open app settings"
            else -> null
        }
        actionLabel?.let {
            TextButton(permission.onAction, enabled = enabled && !permission.requesting) {
                Text(if (permission.requesting) "Waiting for Android" else it)
            }
        }
        permission.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (permission.showRationale) {
        AlertDialog(
            onDismissRequest = permission.onDismissRationale,
            title = { Text("Allow camera access?") },
            text = { Text("FreshCheck needs camera access to take a produce photo. Gallery does not need camera permission. You can continue without allowing access.") },
            confirmButton = {
                TextButton(permission.onConfirmRationale, enabled = enabled && !permission.requesting) { Text("Continue") }
            },
            dismissButton = { TextButton(permission.onDismissRationale) { Text("Not now") } }
        )
    }
}
