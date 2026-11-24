package com.freshcheck.app.ui.scan

/** Permission is separate from camera readiness: a granted permission does not bind a camera. */
enum class CameraPermissionStatus {
    NotRequested, Granted, Denied, SettingsRequired, NoCamera, Unavailable
}

internal data class CameraPermissionSnapshot(
    val hasCamera: Boolean,
    val granted: Boolean,
    val showRationale: Boolean,
    val requestedBefore: Boolean,
    val hasActivity: Boolean = true
)

internal fun cameraPermissionStatus(snapshot: CameraPermissionSnapshot): CameraPermissionStatus = when {
    !snapshot.hasCamera -> CameraPermissionStatus.NoCamera
    snapshot.granted -> CameraPermissionStatus.Granted
    !snapshot.hasActivity -> CameraPermissionStatus.Unavailable
    snapshot.showRationale -> CameraPermissionStatus.Denied
    snapshot.requestedBefore -> CameraPermissionStatus.SettingsRequired
    else -> CameraPermissionStatus.NotRequested
}

internal enum class CameraPermissionAction { Request, Explain, Settings, None }

internal fun cameraPermissionAction(status: CameraPermissionStatus): CameraPermissionAction = when (status) {
    CameraPermissionStatus.NotRequested -> CameraPermissionAction.Request
    CameraPermissionStatus.Denied -> CameraPermissionAction.Explain
    CameraPermissionStatus.SettingsRequired -> CameraPermissionAction.Settings
    else -> CameraPermissionAction.None
}
