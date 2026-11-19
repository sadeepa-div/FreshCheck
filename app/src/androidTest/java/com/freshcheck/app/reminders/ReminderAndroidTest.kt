package com.freshcheck.app.reminders

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.*
import com.freshcheck.app.data.local.database.FreshCheckDatabase
import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.data.repository.RoomFreshCheckRepository
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.ProduceCategory
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.time.Clock

/** Isolated Room and WorkManager test databases; no production graph or actual notification posting. */
@RunWith(AndroidJUnit4::class)
class ReminderAndroidTest {
    private lateinit var context: Context
    private lateinit var db: FreshCheckDatabase
    private lateinit var store: RoomReminderStore
    private val now = Instant.parse("2026-11-18T00:00:00Z")
    private fun food(id: String = "food") = FoodItemUi(id, "Apples", ProduceCategory.Fruit, 2.0, "pieces",
        FoodStorage.Refrigerated, LocalDate.of(2026, 11, 18), null, emptyList())
    private fun draft(id: String? = null, foodId: String = "food") =
        ReminderDraft(foodId, id, "2026-11-19", "12:30", "UTC")
    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FreshCheckDatabase::class.java).build()
        store = RoomReminderStore(db)
    }
    @After fun close() = db.close()

    @Test fun reminderAndRecheckSaveTogetherWithoutOverwritingFoodAndRevisionInvalidatesOldWork() = runBlocking {
        val repo = RoomFreshCheckRepository(db, Clock.fixed(now, ZoneOffset.UTC))
        repo.insertFood(food())
        val first = store.save(draft(), LocalDate.parse("2026-11-19"), now.plusSeconds(86400), now)
        val item = db.foodItemDao().findById("food")!!
        assertEquals(2.0, item.quantity, 0.0)
        assertEquals(FoodStorage.Refrigerated, item.storage)
        assertEquals(LocalDate.parse("2026-11-19"), item.recheck)
        val edited = store.save(draft(first.id), item.recheck!!, first.dueAt.plusSeconds(3600), now)
        assertEquals(first.id, edited.id)
        assertTrue(edited.updatedAt > first.updatedAt)
        assertTrue(store.markPending(ReminderVersion.of(edited), ReminderState.Enqueued))
        assertEquals(edited.id, db.reminderDao().findDue(edited.dueAt).single().id)
        assertFalse(store.claim(ReminderVersion.of(first), first.dueAt.plusSeconds(7200)))
        assertTrue(store.claim(ReminderVersion.of(edited), edited.dueAt))
        assertFalse(store.claim(ReminderVersion.of(edited), edited.dueAt))
    }

    @Test fun terminalAndDeletedFoodAndRecheckEditsInvalidateDelivery() = runBlocking {
        val repo = RoomFreshCheckRepository(db, Clock.fixed(now, ZoneOffset.UTC))
        for ((index, operation) in listOf("used", "discarded", "deleted", "date", "removed").withIndex()) {
            val id = "food-$index"
            repo.insertFood(food(id))
            val row = store.save(draft(foodId = id), LocalDate.parse("2026-11-19"), now.plusSeconds(86400), now)
            when (operation) {
                "used" -> repo.markUsed(id)
                "discarded" -> repo.markDiscarded(id)
                "deleted" -> repo.deleteFood(id)
                "date" -> repo.updateFood(food(id).copy(recheck = LocalDate.parse("2026-11-20")))
                else -> repo.updateFood(food(id).copy(recheck = null))
            }
            assertFalse(store.claim(ReminderVersion.of(row), row.dueAt))
        }
    }

    @Test fun foodMutationBetweenClaimAndPostingSuppressesNotification() = runBlocking {
        val repo = RoomFreshCheckRepository(db, Clock.fixed(now, ZoneOffset.UTC))
        repo.insertFood(food())
        val row = store.save(draft(), LocalDate.parse("2026-11-19"), now.plusSeconds(86400), now)
        assertTrue(store.claim(ReminderVersion.of(row), row.dueAt))
        repo.markUsed("food")
        var posted = false
        val result = store.postClaimed(ReminderVersion.of(row)) { posted = true; PostResult.Posted }
        assertEquals(PostResult.Stale, result)
        assertFalse(posted)
    }

    @Test fun failedReminderInsertRollsBackFoodRecheck() = runBlocking {
        RoomFreshCheckRepository(db, Clock.fixed(now, ZoneOffset.UTC)).insertFood(food())
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_reminder_insert BEFORE INSERT ON reminders
            BEGIN SELECT RAISE(ABORT, 'test failure'); END
        """.trimIndent())
        assertTrue(runCatching {
            store.save(draft(), LocalDate.parse("2026-11-19"), now.plusSeconds(86400), now)
        }.isFailure)
        assertNull(db.foodItemDao().findById("food")!!.recheck)
        assertTrue(store.all().isEmpty())
    }

    @Test fun uniqueWorkIsIdempotentReplacesOldRevisionAndCanCancel() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val scheduler = WorkManagerReminderScheduler(context)
        val actualNow = Instant.now()
        val old = ReminderVersion("test-work", actualNow.plusSeconds(86400).toEpochMilli(), 1)
        scheduler.ensure(old, actualNow)
        val work = WorkManager.getInstance(context)
        val before = work.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.name(old.id)).get().single { !it.state.isFinished }.id
        scheduler.ensure(old, actualNow)
        assertEquals(before, work.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.name(old.id)).get()
            .single { !it.state.isFinished }.id)
        val current = old.copy(revisionMillis = 2)
        scheduler.ensure(current, actualNow)
        assertTrue(scheduler.enqueued(current))
        assertFalse(scheduler.enqueued(old))
        assertEquals(1, work.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.name(old.id)).get().count { !it.state.isFinished })
        scheduler.cancel(current.id)
        assertFalse(scheduler.enqueued(current))
    }

    @Test fun invalidWorkerInputReturnsWithoutOpeningProductionGraph() = runBlocking {
        val worker = TestListenableWorkerBuilder<FoodReminderWorker>(context)
            .setInputData(workDataOf(WorkManagerReminderScheduler.ID to "demo-reminder")).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test fun workerRetriesStorageFailuresAndStopsAtTheBoundUsingAnIsolatedEngine() = runBlocking {
        val settings = object : ReminderSettingsStore {
            override val changes = kotlinx.coroutines.flow.flowOf(ReminderPreferences(true, true))
            override fun current() = ReminderPreferences(true, true)
            override suspend fun set(value: ReminderPreferences) = Unit
        }
        val notifications = object : ReminderNotifications {
            override fun availability() = NotificationAvailability(true, true, true)
            override fun post(row: ReminderEntity, food: FoodItemEntity) = error("Posting must not run")
            override fun cancel(id: String) = Unit
            override fun retainOnly(ids: Set<String>) = Unit
        }
        val engine = ReminderDelivery(store, settings, notifications)
        db.close() // Only this test's in-memory database; force a transient read failure.
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, name: String, params: WorkerParameters): ListenableWorker? =
                if (name == FoodReminderWorker::class.java.name) FoodReminderWorker(appContext, params, engine) else null
        }
        fun worker(attempt: Int) = TestListenableWorkerBuilder<FoodReminderWorker>(context)
            .setWorkerFactory(factory).setRunAttemptCount(attempt)
            .setInputData(workDataOf(WorkManagerReminderScheduler.ID to "test-reminder",
                WorkManagerReminderScheduler.DUE to now.toEpochMilli(),
                WorkManagerReminderScheduler.REVISION to now.toEpochMilli())).build()
        assertEquals(ListenableWorker.Result.retry(), worker(0).doWork())
        assertEquals(ListenableWorker.Result.failure(), worker(4).doWork())
    }

    @Test fun preferencesPersistAcrossStoreInstancesInAnIsolatedFile() = runBlocking(Dispatchers.IO) {
        val name = "test-reminder-prefs-${java.util.UUID.randomUUID()}"
        try {
            val first = ReminderPreferencesStore(context, name)
            assertFalse(first.current().notifications)
            first.set(ReminderPreferences(true, false))
            first.markPermissionAsked()
            val reopened = ReminderPreferencesStore(context, name)
            assertEquals(ReminderPreferences(true, false), reopened.current())
            assertTrue(reopened.permissionAsked())
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun notificationIntentIsExplicitAndBothTapParsersRejectUriGrantsAndExtraRoutes() {
        val row = ReminderEntity("reminder", "food", now, createdAt = now, updatedAt = now)
        val intent = ReminderNotificationIntent.create(context, row)
        assertEquals("com.freshcheck.app.MainActivity", intent.component!!.className)
        assertEquals("food", ReminderNotificationIntent.parse(intent)!!.foodId)
        assertNull(ReminderNotificationIntent.parse(Intent(intent).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        assertNull(ReminderNotificationIntent.parse(Intent(intent).setAction("arbitrary-action")))
        val clipped = Intent(intent).apply { clipData = ClipData.newPlainText("unexpected", "payload") }
        assertNull(ReminderNotificationIntent.parse(clipped))
    }
}
