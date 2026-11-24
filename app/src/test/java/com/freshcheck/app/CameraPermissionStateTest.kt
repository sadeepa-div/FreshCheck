package com.freshcheck.app

import com.freshcheck.app.ui.scan.CameraPermissionAction
import com.freshcheck.app.ui.scan.CameraPermissionSnapshot
import com.freshcheck.app.ui.scan.CameraPermissionStatus
import com.freshcheck.app.ui.scan.cameraPermissionAction
import com.freshcheck.app.ui.scan.cameraPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionStateTest {
    private val firstVisit = CameraPermissionSnapshot(
        hasCamera = true, granted = false, showRationale = false, requestedBefore = false
    )

    @Test fun firstVisitOffersAnExplicitRequest() {
        assertEquals(CameraPermissionStatus.NotRequested, cameraPermissionStatus(firstVisit))
        assertEquals(CameraPermissionAction.Request, cameraPermissionAction(CameraPermissionStatus.NotRequested))
    }

    @Test fun currentGrantOverridesOldDenialHistory() {
        assertEquals(CameraPermissionStatus.Granted,
            cameraPermissionStatus(firstVisit.copy(granted = true, requestedBefore = true)))
        assertEquals(CameraPermissionAction.None, cameraPermissionAction(CameraPermissionStatus.Granted))
    }

    @Test fun ordinaryDenialRequiresAnExplanationBeforeRequestingAgain() {
        assertEquals(CameraPermissionStatus.Denied,
            cameraPermissionStatus(firstVisit.copy(showRationale = true, requestedBefore = true)))
        assertEquals(CameraPermissionAction.Explain, cameraPermissionAction(CameraPermissionStatus.Denied))
    }

    @Test fun systemRationaleSurvivesMissingLocalHistory() {
        assertEquals(CameraPermissionStatus.Denied,
            cameraPermissionStatus(firstVisit.copy(showRationale = true)))
    }

    @Test fun previouslyRequestedWithoutRationaleOffersSettings() {
        assertEquals(CameraPermissionStatus.SettingsRequired,
            cameraPermissionStatus(firstVisit.copy(requestedBefore = true)))
        assertEquals(CameraPermissionAction.Settings, cameraPermissionAction(CameraPermissionStatus.SettingsRequired))
    }

    @Test fun revokedGrantIsReadFromAndroidRatherThanRememberedAsGranted() {
        assertEquals(CameraPermissionStatus.SettingsRequired,
            cameraPermissionStatus(firstVisit.copy(granted = false, requestedBefore = true)))
    }

    @Test fun noCameraNeverOffersAPermissionRequestEvenWithAnOldGrant() {
        assertEquals(CameraPermissionStatus.NoCamera,
            cameraPermissionStatus(firstVisit.copy(hasCamera = false, granted = true)))
        assertEquals(CameraPermissionAction.None, cameraPermissionAction(CameraPermissionStatus.NoCamera))
    }

    @Test fun missingActivityDoesNotOfferAnUnlaunchableRequest() {
        assertEquals(CameraPermissionStatus.Unavailable,
            cameraPermissionStatus(firstVisit.copy(hasActivity = false)))
        assertEquals(CameraPermissionAction.None, cameraPermissionAction(CameraPermissionStatus.Unavailable))
    }
}
