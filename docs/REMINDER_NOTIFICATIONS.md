# Phase 3 — Re-check reminders and local notifications

## Scope and architecture

FreshCheck reuses freshcheck.db and the existing reminders table. No schema columns, indexes,
database version, migration or production sample rows were added. ReminderState is stored as
TEXT names, so adding lifecycle values does not require an SQLite schema migration. Older
application binaries do not understand the new enum values and are not a supported downgrade.

The flow is:
1. The saved Food Details action opens the compact Material 3 ReminderScreen.
2. The user explicitly chooses date, time and ZoneId (and an offset for a DST overlap).
3. FreshCheckViewModel invokes ReminderCoordinator off the main thread.
4. RoomReminderStore saves the definition and food.recheck in one Room transaction.
5. ReminderCoordinator separately awaits WorkManager enqueue/cancel operations.
6. The worker reads current Room state and current notification capability before posting.

ReminderScheduler, ReminderNotifications, ReminderSettingsStore and ReminderStore are ports
with isolated JVM fakes. Android adapters are WorkManagerReminderScheduler,
AndroidReminderNotifications, ReminderPreferencesStore and RoomReminderStore.
ReminderGraph holds one application-context graph shared by ViewModel and default-process workers.
MainActivity, existing AppUiData/action contracts and Member 1's destination state are retained.

WorkManager 2.12.0 is the stable version selected. Its minSdk is 24; FreshCheck uses 26.
The existing AGP 9.5.1, Kotlin 2.2.10, SDK 37, Room and coroutine versions were preserved.
The debug build verifies compatibility. Only work-runtime-ktx and test-only work-testing were added.

References:
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/develop/ui/compose/notifications/notification-permission

## Room lifecycle and transaction boundaries

The original stable reminder ID, food FK, dueAt, enabled, state, createdAt and updatedAt columns
are reused. Food/scans/outcomes retention is unchanged. Setting a reminder updates only the
food re-check date and audit time; unrelated food fields and scan history are untouched.
Changing that date disables other stale definitions for that food in the same transaction.

State meanings:
- DefinitionOnly: saved/pending definition; no scheduling claim.
- Enqueued: an enqueue was acknowledged. The UI additionally queries current WorkManager
  state and current Android capability before describing work as active.
- Blocked: enabled intent retained, but current permission/settings/food validity prevents work.
- Disabled: explicitly disabled or invalidated by a food mutation; no future delivery.
- Claimed: delivery consumed before the external notification call. A running current worker
  may finish; if interrupted, this state is not automatically replayed.
- Posted: the notification request was submitted to Android. This does not prove presentation,
  sound, user visibility, or that the notification was read.
- Failed: bounded worker attempts were exhausted; explicitly update the reminder to retry.

Configuration updatedAt is a monotonic epoch-millisecond revision: max(current wall-clock
millisecond, previous revision + 1). Status-only transitions preserve that revision.
Phase 2's disableForFood advances it monotonically too. The worker input contains reminder ID,
dueAt milliseconds and revision milliseconds. Old schedules cannot deliver a newer definition,
even when an edit keeps the same dueAt.

Room transactions and WorkManager's internal database are separate systems. They are not
described as one atomic operation. DefinitionOnly acts as a durable pending intent to schedule.
A save can succeed in Room while enqueue fails; the UI reports a saved definition with
unconfirmed/inactive work and exposes the scheduling error. The next reconciliation repairs it.

## Date, time and timezone policy

A new reminder has no fabricated date or time. An existing food re-check date can prefill the
date; an existing definition can prefill its stored instant in the current display zone.
Picker defaults are not saved until the user explicitly confirms them. Manual ISO date and
HH:mm entry are also supported, with validation on save.

The explicitly selected ZoneId converts the local date/time to a fixed Instant:
- Invalid or missing date/time/zone and past or present times are rejected.
- A DST gap is rejected instead of silently shifted forward.
- A DST overlap requires the user to choose one of the actual UTC offsets.
- The stored instant does not move when the device travels to another time zone.
- The original zone label is not separately persisted; editing displays the instant in the
  current device zone, which is shown and editable. The user must review it before confirming.
- A timezone/clock change triggers reconciliation and recomputes the remaining initial delay.
- A worker also compares now to dueAt. A backward clock movement causes retry, never early posting.

The reminder is a manual check-in suggestion, not an expiry prediction or food-safety result.

## WorkManager scheduling

Each definition owns one unique name: freshcheck-recheck:<reminderId>.
OneTimeWorkRequest input includes the exact dueAt and revision. Matching unfinished work is
retained, repeated reconciliation does not create duplicates, and edits replace outdated work.
Enqueue/cancel Operation futures are awaited before the result is reported.

No periodic minute scanner, AlarmManager, exact-alarm permission, network constraint or
expedited/foreground work is used. Android Doze, battery policies, system quotas and OEM
behavior can delay execution. The UI never promises exact alarm timing.

FreshCheckApplication queues one unique reconciliation job at process startup.
AndroidX Startup owns WorkManager initialization; there is no second manual initializer.
The private ReminderSystemReceiver accepts only protected TIME_SET, TIMEZONE_CHANGED,
BOOT_COMPLETED and MY_PACKAGE_REPLACED broadcasts and queues reconciliation.
The ViewModel also reconciles on foreground resume, explicit refresh, settings changes,
reminder changes and successful production food mutations. Nothing is enqueued by recomposition.

Reconciliation reads saved definitions, food status, persistent switches and current Android
capability; ensures desired jobs, cancels stale/orphan jobs and removes outdated app-owned
notifications. It preserves a matching worker while that worker finishes posting a claim.
Claimed/Posted definitions are never newly enqueued just because the app restarts.

Recovery limitation: if the process dies after the Room commit and before WorkManager accepts
work, the pending definition is repaired at the next startup, boot/package/time event, foreground
refresh or explicit reconciliation. It is not guaranteed to run before one of those triggers.
A force-stopped app may not run background work until Android allows it again.

## Delivery and duplicate prevention

FoodReminderWorker is a CoroutineWorker; database and delivery operations run on Dispatchers.IO.
It rejects malformed/demo input before opening the production graph. It then verifies:
- the exact current dueAt and configuration revision;
- an enabled, pending reminder;
- a real food/canonical ID, existing Active food and a retained re-check date;
- that the reminder is actually due;
- both persistent switches, runtime permission, app notifications and channel availability.

A compare-and-set claim is committed before notification posting. RoomReminderStore then
rechecks current reminder/food state inside a short transaction while posting, serializing
against Used/Discarded/Delete/re-check mutations. Capability is checked again immediately
before the OS call; SecurityException handles last-moment permission revocation.

This deliberately prefers at-most-once attempts over automatic duplicate delivery. A crash
between claim and posting may miss a notification. Claimed without a current running job is
reported as uncertain; the user can explicitly update it to try again. Posted/Claimed records
are not automatically recreated. Stable tag + notification ID and setOnlyAlertOnce provide an
additional duplicate-presentation safeguard.

Storage failures before claim retry with exponential WorkManager backoff, up to five attempts.
After the bound, a current pending row is marked Failed where possible. An early execution
after a backward clock move retries without posting; clock reconciliation normally replaces
the remaining delay. Permission/global/channel blocking is not retried in a loop: the enabled
definition stays Blocked until capability/settings change and reconciliation runs.

## Notification permission and channel

Manifest declares POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED. No exact alarm or unrelated
network permission was explicitly added. WorkManager's standard library components/permissions
are merged by the dependency.

A contextual explanation is offered only when the user enables reminder notifications,
requests permission explicitly, or saves a reminder needing permission with notifications enabled.
API 33+ uses the Activity Result RequestPermission contract. Below API 33, no runtime request
is launched. Denial does not discard date/time or pretend that work guarantees notification delivery.

Permission checks are dynamic, not persisted grant booleans. Only the fact a request was
previously attempted is persisted. shouldShowRequestPermissionRationale and that flag distinguish
a repeat explanation from a path to app settings when Android is not offering another prompt.
Returning from Android settings refreshes capability and reconciles pending definitions.

The stable channel is freshcheck_recheck_reminders, named Food Re-check Reminders, with
IMPORTANCE_DEFAULT. User channel/app settings are respected; the app does not override them.
Notifications use a dedicated monochrome small icon, the current saved food name, neutral
re-check wording, private lock-screen visibility, auto-cancel, and immutable explicit PendingIntent.
Posting means submission to Android, not proof that the OS displayed it.

## UI and persistent preferences

FoodDetailsScreen now routes Set or Edit Reminder to ReminderScreen instead of food editing.
Users can save/update, select existing definitions, create another explicitly, disable, or
cancel/delete a definition. Current definition, capability and actual work status are displayed.
The existing Material 3 theme, navigation, food editing and sample exploration are preserved.

Settings and the reminder editor share ReminderSettingsPanel. It exposes two real persistent
preferences: notifications (default false) and re-check reminders (default true, subordinate
to the master switch). Private SharedPreferences is used for these small durable flags,
with commit on IO and Flow listeners; preferences are not rememberSaveable state.
Checked switches express user intent, not delivery permission. Permission/app/channel status,
refresh and Android settings actions remain separately visible.

Samples are protected at the screen, ViewModel/data boundaries, store and worker policy.
DemoInventory, isDemo UI items, demo- IDs and sample scanner output never schedule real reminders.
No sample definitions are seeded on a fresh install.

## Used, Discarded, Delete and re-check edits

Phase 2 repository transactions still disable definitions after Used/Discarded, and cascade
definition deletion after food deletion. Date changes/removal disable stale definitions.
Their revision changes invalidate old worker input. The ViewModel then reconciles actual
WorkManager jobs and visible notifications after the committed mutation.

If cancellation fails after a food mutation, the food change remains committed and the UI
reports that reconciliation needs attention. Workers independently re-read Room and will not
deliver for inactive/deleted/disabled food. Direct future repository callers must also invoke
reconciliation; the worker guards still protect delivery in the meantime.

## Notification taps

The existing exported launcher MainActivity is reused; no second activity/navigation system is added.
PendingIntent uses an explicit component, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT and a unique
data URI per reminder revision. It carries only bounded opaque food/reminder IDs and revision,
not image grants, nested Intents, arbitrary destinations, user credentials or model outputs.

Cold start and onNewIntent use the same action/scheme/host/path allowlist. URI grants, ClipData,
selectors, malformed IDs, queries, fragments and unexpected routes are rejected.
onNewIntent updates the Activity intent before parsing. Pending and consumed tap tokens are
saved across recreation. Duplicate consumed taps do not reopen the same reminder revision.

The route waits for the production inventory snapshot, validates the target is still Active,
then uses Member 1's existing My Food/openFoodId path. The My Food demo mode is exited for a
real notification target. Missing/Used/Discarded/deleted food shows a useful message rather than
opening an invalid details screen. Navigation is acknowledged only after the coordinator
sets its existing destination/item state.

## Testing and validation

ReminderPolicyTest uses fake store, scheduler, settings and notification ports for local-time,
DST, past-time, creation/update/disable/delete, unique jobs, errors/recovery, permission/app/channel/
global switches, stale workers, inactive/deleted food, claimed/posted restart behavior and duplicate
delivery policy. FreshCheckViewModelTest adds notification navigation validation/acknowledgement.

ReminderAndroidTest uses a fresh in-memory Room database and WorkManager's test initialization.
It covers atomic date/definition saves, versioned claims, mutations before delivery, rollback,
unique work replacement/cancellation, invalid worker input, isolated preference files, secure
tap intents and bounded retry via an injected isolated delivery engine.
FreshCheckTestRunner uses a neutral Application so production startup reconciliation is not
queued by the test application. No test posts actual notifications or deliberately opens the
production Room graph.

Commands:
- .\gradlew.bat :app:assembleDebug
- .\gradlew.bat :app:testDebugUnitTest
- .\gradlew.bat :app:assembleDebugAndroidTest
- git diff --check

Use the verified F:\android studio\jbr JDK via process-local JAVA_HOME when needed.
Android runtime tests, actual permission dialogs, notification presentation, reboot/Doze/clock
behavior and warm/cold taps require a compatible test device. Compiling an APK is not a runtime pass.
No emulator startup or disk cleanup is required by this phase.

Validation on November 19, 2026 used F:\android studio\jbr:
- :app:assembleDebug — passed.
- :app:testDebugUnitTest — passed, including 19 new reminder policy tests and one new
  notification-navigation ViewModel test, together with the existing regression tests.
- :app:assembleDebugAndroidTest — passed. Nine new reminder instrumentation tests compiled
  and packaged; they were not executed on Android.
- Android Studio Gradle sync — passed.
- git diff --check — passed after removing an extra blank line at ReminderDao EOF.

Actual permission dialogs, channel behavior, notification presentation, background timing,
reboots and cold/warm taps still require final device testing. No emulator was started,
no disk cleanup was performed, and tests did not access real production records.

## Remaining dependencies and limitations

Member 2's real capture/inference and new-food creation inputs remain pending as in Phase 2.
Existing persisted food can use reminders independently of camera/model integration.
URI/image ownership remains unchanged. Notifications and reminders do not interpret visual
freshness as food safety. Android can delay or suppress work/presentation; exactly-once visible
delivery cannot be transacted across Room and NotificationManager.

## Security hardening and alignment update

Alignment areas: contextual runtime permission, immutable explicit PendingIntent, bounded
notification navigation and protected private system receiver.
Priority: Medium. Prevent mutable-token redirection and untrusted navigation payloads.
Scope: MainActivity, AndroidManifest, ReminderNotificationIntent, AndroidReminderNotifications,
FreshCheckRoute and ReminderSystemReceiver. No custom exported service/provider was added.
The launcher remains exported because Android must launch it; navigation payloads confer no
database mutation authority. No nested Intent is read or forwarded.

Implementation: the following unified diff records permission declarations, private receiver,
shared cold/warm validation and immutable explicit notification navigation. The permission
launcher and settings paths live in FreshCheckRoute. JVM allowlist tests passed; Android
intent/permission tests compiled, with runtime verification deferred.

```diff
diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml
index 6895a42..da11b74 100644
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -2,7 +2,11 @@
 <manifest xmlns:android="http://schemas.android.com/apk/res/android"
     xmlns:tools="http://schemas.android.com/tools">
 
+    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
+    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
+
     <application
+        android:name=".FreshCheckApplication"
         android:allowBackup="true"
         android:dataExtractionRules="@xml/data_extraction_rules"
         android:fullBackupContent="@xml/backup_rules"
@@ -23,6 +27,14 @@
                 <category android:name="android.intent.category.LAUNCHER" />
             </intent-filter>
         </activity>
+        <receiver android:name=".reminders.ReminderSystemReceiver" android:exported="false">
+            <intent-filter>
+                <action android:name="android.intent.action.TIME_SET" />
+                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
+                <action android:name="android.intent.action.BOOT_COMPLETED" />
+                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
+            </intent-filter>
+        </receiver>
     </application>
 
 </manifest>
\ No newline at end of file
diff --git a/app/src/main/java/com/freshcheck/app/MainActivity.kt b/app/src/main/java/com/freshcheck/app/MainActivity.kt
index a51afd8..97c09bf 100644
--- a/app/src/main/java/com/freshcheck/app/MainActivity.kt
+++ b/app/src/main/java/com/freshcheck/app/MainActivity.kt
@@ -1,15 +1,49 @@
 package com.freshcheck.app
+
+import android.content.Intent
 import android.os.Bundle
 import androidx.activity.ComponentActivity
 import androidx.activity.compose.setContent
 import androidx.activity.enableEdgeToEdge
+import androidx.compose.runtime.getValue
+import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import com.freshcheck.app.integration.FreshCheckRoute
-
+import com.freshcheck.app.reminders.NotificationFoodTarget
+import com.freshcheck.app.reminders.ReminderNotificationIntent
+import kotlinx.coroutines.flow.MutableStateFlow
 
 class MainActivity : ComponentActivity() {
+    private val notificationTarget = MutableStateFlow<NotificationFoodTarget?>(null)
+    private var consumedToken: String? = null
+
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
+        consumedToken = savedInstanceState?.getString("consumed_reminder")
+        val restored = ReminderNotificationIntent.parseData(savedInstanceState?.getString("pending_reminder"))
+        if (restored != null) notificationTarget.value = restored else receive(intent)
         enableEdgeToEdge()
-        setContent { FreshCheckRoute() }
+        setContent {
+            val target by notificationTarget.collectAsStateWithLifecycle()
+            FreshCheckRoute(target) {
+                consumedToken = notificationTarget.value?.token ?: consumedToken
+                notificationTarget.value = null
+            }
+        }
+    }
+
+    override fun onNewIntent(intent: Intent) {
+        super.onNewIntent(intent)
+        setIntent(intent)
+        receive(intent) // Same allowlist for cold and warm notification taps.
+    }
+    private fun receive(intent: Intent?) {
+        val target = ReminderNotificationIntent.parse(intent) ?: return
+        if (target.token != consumedToken && target.token != notificationTarget.value?.token)
+            notificationTarget.value = target
+    }
+    override fun onSaveInstanceState(outState: Bundle) {
+        outState.putString("consumed_reminder", consumedToken)
+        outState.putString("pending_reminder", notificationTarget.value?.token)
+        super.onSaveInstanceState(outState)
     }
 }
diff --git a/app/src/main/java/com/freshcheck/app/reminders/ReminderNotificationIntent.kt b/app/src/main/java/com/freshcheck/app/reminders/ReminderNotificationIntent.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/freshcheck/app/reminders/ReminderNotificationIntent.kt
@@ -0,0 +1,39 @@
+package com.freshcheck.app.reminders
+
+import android.content.Context
+import android.content.Intent
+import android.net.Uri
+import com.freshcheck.app.MainActivity
+
+/** Data-only allowlisted navigation. No nested Intent, Parcelable, URI grants or arbitrary destination. */
+object ReminderNotificationIntent {
+    const val ACTION = "com.freshcheck.app.OPEN_RECHECK_FOOD"
+    const val SCHEME = "freshcheck-internal"
+    const val HOST = "recheck"
+
+    fun create(context: Context, row: com.freshcheck.app.data.local.entity.ReminderEntity): Intent {
+        val uri = Uri.Builder().scheme(SCHEME).authority(HOST)
+            .appendPath(row.foodId).appendPath(row.id).appendPath(row.updatedAt.toEpochMilli().toString()).build()
+        return Intent(context, MainActivity::class.java).setAction(ACTION).setData(uri)
+            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
+    }
+    fun parse(intent: Intent?): NotificationFoodTarget? {
+        if (intent?.action != ACTION || intent.selector != null || intent.clipData != null) return null
+        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
+            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
+        if (intent.flags and grants != 0) return null
+        return parseData(intent.dataString)
+    }
+    /** java.net.URI keeps the same allowlist testable on the JVM without Android mocks. */
+    fun parseData(data: String?): NotificationFoodTarget? = runCatching {
+        if (data == null || data.length > 512) return null
+        val uri = java.net.URI(data)
+        if (uri.scheme != SCHEME || uri.host != HOST || uri.rawQuery != null ||
+            uri.rawFragment != null || uri.userInfo != null || uri.port != -1) return null
+        val parts = uri.rawPath.removePrefix("/").split("/")
+        if (parts.size != 3 || parts.any { it.contains('%') } ||
+            !ReminderRules.realId(parts[0]) || !ReminderRules.realId(parts[1]) ||
+            parts[2].toLongOrNull() == null) return null
+        NotificationFoodTarget(parts[0], requireNotNull(data))
+    }.getOrNull()
+}
diff --git a/app/src/main/java/com/freshcheck/app/reminders/AndroidReminderNotifications.kt b/app/src/main/java/com/freshcheck/app/reminders/AndroidReminderNotifications.kt
new file mode 100644
--- /dev/null
+++ b/app/src/main/java/com/freshcheck/app/reminders/AndroidReminderNotifications.kt
@@ -0,0 +1,74 @@
+package com.freshcheck.app.reminders
+
+import android.Manifest
+import android.app.*
+import android.content.Context
+import android.content.pm.PackageManager
+import android.os.Build
+import androidx.core.app.NotificationCompat
+import androidx.core.app.NotificationManagerCompat
+import androidx.core.content.ContextCompat
+import com.freshcheck.app.R
+import com.freshcheck.app.data.local.entity.*
+
+class AndroidReminderNotifications(context: Context) : ReminderNotifications {
+    private val context = context.applicationContext
+    private val manager = context.getSystemService(NotificationManager::class.java)
+    private val compat = NotificationManagerCompat.from(context)
+
+    fun createChannel() {
+        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Food Re-check Reminders",
+            NotificationManager.IMPORTANCE_DEFAULT).apply {
+            description = "Optional check-in reminders for your saved food. Delivery times are approximate."
+        })
+    }
+    override fun availability(): NotificationAvailability = try {
+        createChannel()
+        val permission = Build.VERSION.SDK_INT < 33 ||
+            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
+        val app = compat.areNotificationsEnabled()
+        val channel = manager.getNotificationChannel(CHANNEL)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
+        NotificationAvailability(permission, app, channel, when {
+            !permission -> "Notification permission is not granted."
+            !app -> "Android notifications are disabled for FreshCheck."
+            !channel -> "The Food Re-check Reminders channel is disabled or unavailable."
+            else -> "Android notification permission and reminder channel are available. Delivery may be delayed."
+        })
+    } catch (failure: Exception) {
+        NotificationAvailability(explanation = "Notification channel is unavailable. Check system settings.")
+    }
+
+    @android.annotation.SuppressLint("MissingPermission") // Dynamic availability check plus SecurityException boundary below.
+    override fun post(row: ReminderEntity, food: FoodItemEntity): PostResult {
+        if (!availability().allowed) return PostResult.Blocked
+        return try {
+            val pending = PendingIntent.getActivity(context, 0, ReminderNotificationIntent.create(context, row),
+                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
+            val notification = NotificationCompat.Builder(context, CHANNEL)
+                .setSmallIcon(R.drawable.ic_recheck_notification)
+                .setContentTitle("Food re-check reminder")
+                .setContentText("Time to re-check your ${food.name.take(100)}.")
+                .setStyle(NotificationCompat.BigTextStyle().bigText(
+                    "Time to re-check your ${food.name.take(100)}. Check its condition manually; this is not an expiry prediction."))
+                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
+                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
+            // Availability is checked immediately before notify; revocation can still race and is handled below.
+            compat.notify(tag(row.id), NOTIFICATION_ID, notification)
+            PostResult.Posted // Submitted to Android, not proof that it appeared or was seen.
+        } catch (denied: SecurityException) { PostResult.Blocked }
+        catch (failure: Exception) { PostResult.Uncertain }
+    }
+    override fun cancel(id: String) = compat.cancel(tag(id), NOTIFICATION_ID)
+    override fun retainOnly(ids: Set<String>) {
+        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach {
+            val tag = it.tag ?: return@forEach
+            if (tag.removePrefix(PREFIX) !in ids) compat.cancel(tag, it.id)
+        }
+    }
+    companion object {
+        const val CHANNEL = "freshcheck_recheck_reminders"
+        const val PREFIX = "freshcheck-reminder:"
+        const val NOTIFICATION_ID = 1 // tag + ID, rather than a collision-prone hash, identifies each reminder.
+        fun tag(id: String) = PREFIX + id
+    }
+}
```

## Existing UI and coordinator changes

- MainActivity: receives and deduplicates notification targets across launch/recreation.
- FreshCheckRoute: lifecycle collection/reconciliation, contextual permission launcher, settings navigation.
- FreshCheckApp: forwards reminder actions and uses the existing food-details navigation target.
- FoodDetailsScreen: replaces the edit-food reminder placeholder with the dedicated reminder action.
- MyFoodFlow: opens ReminderScreen and exits demo exploration for valid notification food targets.
- MyFoodUiState: adds an optional reminder callback to the existing food action contract.
- ProfileScreen: forwards the reminder state/actions to SettingsScreen.
- SettingsScreen: replaces disabled reminder switches with the persistent capability-aware panel.

EditFoodItemScreen, camera/inference, UI theme and unrelated screens were not modified.
All Phase 3 changes are intended for manual Git review; no staging, commit, push or branch change
is part of this implementation.
