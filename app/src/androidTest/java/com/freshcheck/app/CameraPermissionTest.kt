package com.freshcheck.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.freshcheck.app.ui.result.FreshnessCondition
import com.freshcheck.app.ui.scan.*
import com.freshcheck.app.ui.theme.FreshCheckTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fake permission results/settings only: no real grant changes, system dialogs or production prefs. */
class CameraPermissionTest {
    @get:Rule val compose = createComposeRule()

    private class Access : CameraPermissionAccess {
        var hasCamera = true
        var granted = false
        var rationale = false
        var requested = false
        var settingsOpened = 0
        var settingsAvailable = true
        override fun snapshot() = CameraPermissionSnapshot(hasCamera, granted, rationale, requested)
        override fun setRequestedBefore(value: Boolean) { requested = value }
        override fun openSettings(): Boolean {
            settingsOpened++
            return settingsAvailable
        }
    }

    private class Registry : ActivityResultRegistry() {
        var launches = 0
        var requestCode = 0
        var permission: Any? = null
        var failToLaunch = false
        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?
        ) {
            launches++
            this.requestCode = requestCode
            permission = input
            if (failToLaunch) throw IllegalStateException("Synthetic test launch failure")
        }
        fun respond(granted: Boolean) { dispatchResult(requestCode, granted) }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        init { registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    private val access = Access()
    private val registry = Registry()
    private val owner = Owner()
    private val redraw = mutableStateOf(0)
    private var galleryClicks = 0
    private val restoration = StateRestorationTester(compose)

    private fun content() {
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        restoration.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalLifecycleOwner provides owner
            ) {
                FreshCheckTheme {
                    val permission = rememberCameraPermissionHandler(access)
                    ScanScreen(ScanMode.BeforeBuying, {},
                        ScannerUiState(selectedImageLabel = "Synthetic test redraw ${redraw.value}"),
                        ScannerActions({}, { galleryClicks++ }, {}),
                        FreshnessCondition.Fresh, {}, {},
                        permissionContent = { CameraPermissionPanel(permission) })
                }
            }
        }
    }

    @Test fun firstVisitRecompositionAndResumeNeverLaunchAutomatically() {
        content()
        compose.onNodeWithText("Allow camera").assertExists()
        compose.runOnIdle { redraw.value++; owner.resume() }
        compose.runOnIdle { assertEquals(0, registry.launches) }
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, galleryClicks); assertEquals(0, registry.launches) }
        compose.onNodeWithContentDescription("Capture produce image · camera integration pending").assertIsNotEnabled()
    }

    @Test fun explicitRequestHandlesGrantWithoutEnablingUnimplementedCapture() {
        content()
        compose.onNodeWithText("Allow camera").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, registry.launches)
            assertEquals(Manifest.permission.CAMERA, registry.permission)
            assertTrue(access.requested)
        }
        compose.onNodeWithText("Waiting for Android").assertIsNotEnabled()
        compose.runOnIdle { access.granted = true; registry.respond(true) }
        compose.onNodeWithText("Camera permission granted.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Capture produce image · camera integration pending").assertIsNotEnabled()
        compose.runOnIdle { owner.resume(); assertEquals(1, registry.launches) }
    }

    @Test fun existingGrantDoesNotLaunchARequest() {
        access.granted = true
        content()
        compose.onNodeWithText("Camera permission granted.", substring = true).assertExists()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, registry.launches) }
    }

    @Test fun recreationWhileRequestIsPendingDoesNotLaunchAnotherRequest() {
        content()
        compose.onNodeWithText("Allow camera").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Waiting for Android").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, registry.launches)
            access.granted = true
            registry.respond(true)
        }
        compose.onNodeWithText("Camera permission granted.", substring = true).assertExists()
    }

    @Test fun failedRequestLaunchDoesNotRecordAPermanentDenial() {
        registry.failToLaunch = true
        content()
        compose.onNodeWithText("Allow camera").performScrollTo().performClick()
        compose.onNodeWithText("Camera permission could not be requested.", substring = true).assertExists()
        compose.onNodeWithText("Allow camera").assertIsEnabled()
        compose.runOnIdle { assertFalse(access.requested); assertEquals(1, registry.launches) }
    }

    @Test fun denialRationaleCanBeCancelledOrExplicitlyConfirmed() {
        content()
        compose.onNodeWithText("Allow camera").performScrollTo().performClick()
        compose.runOnIdle { access.rationale = true; registry.respond(false) }
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Try camera permission again").performScrollTo().performClick()
        compose.onNodeWithText("Allow camera access?").assertExists()
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { owner.resume(); assertEquals(1, registry.launches); assertEquals(1, galleryClicks) }
        compose.onNodeWithText("Allow camera access?").assertDoesNotExist()
        compose.onNodeWithText("Try camera permission again").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(2, registry.launches); registry.respond(false) }
    }

    @Test fun permanentDenialOffersSettingsAndResumeRechecksGrantAndRevocation() {
        access.requested = true
        content()
        compose.onNodeWithText("Open app settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, access.settingsOpened); assertEquals(0, registry.launches) }
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled()
        compose.runOnIdle { access.granted = true; owner.resume() }
        compose.onNodeWithText("Camera permission granted.", substring = true).assertExists()
        compose.runOnIdle { access.granted = false; owner.resume() }
        compose.onNodeWithText("Open app settings").assertExists()
        compose.runOnIdle { assertEquals(0, registry.launches) }
    }

    @Test fun denialWithoutRationaleTransitionsToSettingsWithoutAutomaticRetry() {
        content()
        compose.onNodeWithText("Allow camera").performScrollTo().performClick()
        compose.runOnIdle { registry.respond(false); owner.resume() }
        compose.onNodeWithText("Open app settings").assertExists()
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(1, registry.launches) }
    }

    @Test fun missingCameraKeepsGalleryAvailableWithoutARequest() {
        access.hasCamera = false
        content()
        compose.onNodeWithText("No camera hardware detected.", substring = true).assertExists()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, galleryClicks); assertEquals(0, registry.launches) }
    }

    @Test fun missingSettingsAppShowsAnActionableError() {
        access.requested = true
        access.settingsAvailable = false
        content()
        compose.onNodeWithText("Open app settings").performScrollTo().performClick()
        compose.onNodeWithText("Unable to open app settings.", substring = true).assertExists()
        compose.onNodeWithText("Gallery").performScrollTo().assertIsEnabled()
    }

    @Test fun packagedManifestDeclaresCameraPermissionAndOptionalHardware() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.CAMERA))
        @Suppress("DEPRECATION")
        val features = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures.orEmpty()
        for (name in listOf(PackageManager.FEATURE_CAMERA_ANY, PackageManager.FEATURE_CAMERA,
            PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            val feature = features.single { it.name == name }
            assertEquals(0, feature.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        }
    }
}
