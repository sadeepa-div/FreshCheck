package com.freshcheck.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshcheck.app.data.local.database.FreshCheckDatabase
import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.data.repository.*
import com.freshcheck.app.data.mapper.toEntity
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

/** Real Room/SQLite behavior using an isolated, synthetic in-memory database per test. */
@RunWith(AndroidJUnit4::class)
class FreshCheckDatabaseTest {
    private lateinit var db: FreshCheckDatabase
    private lateinit var repo: FreshCheckRepository
    private val at = Instant.parse("2026-11-18T08:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, FreshCheckDatabase::class.java
        ).build()
        repo = RoomFreshCheckRepository(db, clock)
    }
    @After fun close() = db.close()

    private fun food(id: String = "test-food") = FoodItemUi(id, "Apple", ProduceCategory.Fruit,
        2.0, "pieces", FoodStorage.Refrigerated, LocalDate.now(clock), null, emptyList(), produceId = "apple")
    private fun scan(request: String, foodId: String? = null, mode: ScanMode = ScanMode.BeforeBuying) =
        ScanSubmission(ScanRequest(request, mode, foodId),
            AnalysisResult("Apple", ProduceCategory.Fruit, FreshnessCondition.UseSoon,
                confidence = null, produceId = "apple",
                guidance = StorageGuidance("Fridge", "Manual re-check", "Wash before preparation")))
    private fun reminder(id: String = "reminder", foodId: String = "test-food", dueAt: Instant = at) =
        ReminderEntity(id, foodId, dueAt, createdAt = at, updatedAt = at)
    private fun <T> success(result: DatabaseResult<T>): T {
        assertTrue(result.toString(), result is DatabaseResult.Success)
        return (result as DatabaseResult.Success).value
    }
    private fun failure(result: DatabaseResult<*>, reason: DatabaseError) {
        assertTrue(result.toString(), result is DatabaseResult.Failure)
        assertEquals(reason, (result as DatabaseResult.Failure).reason)
    }

    @Test fun foodCrudAndFlowInvalidation() = runBlocking {
        assertTrue(repo.observeActiveFood().first().isEmpty())
        val emission = async { withTimeout(5000) { repo.observeActiveFood().first { it.isNotEmpty() } } }
        success(repo.insertFood(food()))
        assertEquals("test-food", emission.await().single().id)
        assertEquals("Apple", success(repo.findFood("test-food"))!!.name)
        success(repo.updateFood(food().copy(quantity = 3.0, notes = "Edited")))
        val updated = success(repo.findFood("test-food"))!!
        assertEquals(3.0, updated.quantity, 0.0)
        assertEquals("Edited", updated.notes)
        assertEquals(at, db.foodItemDao().findById("test-food")!!.createdAt)
        assertEquals(1, repo.observeAllFood().first().size)
        success(repo.deleteFood("test-food"))
        assertNull(success(repo.findFood("test-food")))
        assertTrue(repo.observeAllFood().first().isEmpty())
    }

    @Test fun standaloneBeforeBuyingHistoryAndIndividualDeletion() = runBlocking {
        val id = success(repo.saveScan(scan("standalone"), at))
        val stored = success(repo.findScan(id))!!
        assertNull(stored.foodId)
        assertNull(stored.originalFoodId)
        assertEquals(ScanMode.BeforeBuying, stored.mode)
        assertNull(stored.confidence)
        assertEquals("Manual re-check", stored.guidance!!.recheck)
        assertEquals(1, repo.observeScanHistory().first().size)
        assertTrue(repo.observeActiveFood().first().isEmpty())
        success(repo.deleteScan(id))
        assertTrue(repo.observeScanHistory().first().isEmpty())
        failure(repo.deleteScan(id), DatabaseError.MissingRecord)
    }

    @Test fun multipleRescansKeepSameFoodAndRejectDuplicateRequest() = runBlocking {
        success(repo.insertFood(food()))
        val request = scan("rescan-1", "test-food", ScanMode.AfterBuying)
        val first = success(repo.addScanToFood("test-food", request, at))
        val second = success(repo.saveScan(scan("rescan-2", "test-food", ScanMode.AfterBuying), at.plusSeconds(1)))
        assertNotEquals(first, second)
        assertEquals(2, repo.observeScansForFood("test-food").first().size)
        assertEquals(2, repo.observeActiveFood().first().single().scans.size)
        failure(repo.saveScan(request, at), DatabaseError.Conflict)
        assertEquals(2, repo.observeScanHistory().first().size)
    }

    @Test fun usedAndDiscardedAreDistinctAtomicTerminalOutcomes() = runBlocking {
        success(repo.insertFood(food("used")))
        success(repo.insertFood(food("discarded")))
        success(repo.saveScan(scan("scan-used", "used"), at))
        success(repo.saveScan(scan("scan-discarded", "discarded"), at))
        success(repo.insertReminder(reminder(foodId = "used")))
        success(repo.markUsed("used"))
        success(repo.markDiscarded("discarded"))
        assertTrue(repo.observeActiveFood().first().isEmpty())
        assertEquals(FoodStatus.Used, db.foodItemDao().findById("used")!!.status)
        assertEquals(FoodStatus.Discarded, db.foodItemDao().findById("discarded")!!.status)
        assertEquals(FoodOutcome.Used, repo.observeUsedOutcomes().first().single().outcome)
        assertEquals(FoodOutcome.Discarded, repo.observeDiscardedOutcomes().first().single().outcome)
        assertEquals(2, repo.observeAllOutcomes().first().size)
        assertEquals(2, repo.observeScanHistory().first().size)
        assertEquals(1, repo.observeScansForFood("used").first().size)
        val definition = repo.observeReminders("used").first().single()
        assertFalse(definition.enabled)
        assertEquals(ReminderState.Disabled, definition.state)
        assertTrue(success(repo.findDueReminders(at)).isEmpty())
        failure(repo.markUsed("used"), DatabaseError.InactiveFood)
        failure(repo.markDiscarded("used"), DatabaseError.InactiveFood)
        failure(repo.updateFood(food("used")), DatabaseError.InactiveFood)
        failure(repo.saveScan(scan("late", "used"), at), DatabaseError.InactiveFood)
        assertEquals(2, repo.observeAllOutcomes().first().size)
    }

    @Test fun deletingFoodRetainsDetachedScansAndCreatesNoOutcome() = runBlocking {
        success(repo.insertFood(food()))
        val scanId = success(repo.saveScan(scan("retained", "test-food"), at))
        success(repo.insertReminder(reminder()))
        success(repo.deleteFood("test-food"))
        assertNull(db.foodItemDao().findById("test-food"))
        val retained = success(repo.findScan(scanId))!!
        assertNull(retained.foodId)
        assertEquals("test-food", retained.originalFoodId)
        assertEquals("Apple", retained.name)
        assertNull(repo.observeScanHistory().first().single().foodId)
        assertTrue(repo.observeReminders("test-food").first().isEmpty())
        assertTrue(repo.observeAllOutcomes().first().isEmpty())
    }

    @Test fun outcomesSurviveExplicitFoodDeletionAndHistoryClearIsIndependent() = runBlocking {
        success(repo.insertFood(food()))
        success(repo.saveScan(scan("history", "test-food"), at))
        success(repo.markDiscarded("test-food"))
        success(repo.deleteFood("test-food"))
        assertEquals("test-food", repo.observeDiscardedOutcomes().first().single().foodId)
        assertEquals(1, repo.observeScanHistory().first().size)
        success(repo.clearScanHistory())
        assertTrue(repo.observeScanHistory().first().isEmpty())
        assertEquals(1, repo.observeAllOutcomes().first().size)
    }

    @Test fun outcomeInsertFailureRollsBackFoodStatusAndReminderChanges() = runBlocking {
        success(repo.insertFood(food()))
        success(repo.insertReminder(reminder()))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_outcome BEFORE INSERT ON food_outcomes
            BEGIN SELECT RAISE(ABORT, 'injected test failure'); END
        """.trimIndent())
        assertTrue(repo.markUsed("test-food") is DatabaseResult.Failure)
        assertEquals(FoodStatus.Active, db.foodItemDao().findById("test-food")!!.status)
        assertEquals(1, repo.observeActiveFood().first().size)
        assertTrue(repo.observeAllOutcomes().first().isEmpty())
        assertTrue(repo.observeReminders("test-food").first().single().enabled)
    }

    @Test fun remindersPersistUpdateFilterDueAndDeleteWithoutScheduling() = runBlocking {
        success(repo.insertFood(food()))
        success(repo.insertReminder(reminder()))
        success(repo.insertReminder(reminder("future", dueAt = at.plusSeconds(60))))
        success(repo.insertReminder(reminder("disabled").copy(enabled = false, state = ReminderState.Disabled)))
        assertEquals(listOf("reminder"), success(repo.findDueReminders(at)).map { it.id })
        assertEquals(ReminderState.DefinitionOnly, repo.observeReminders("test-food").first().first { it.id == "reminder" }.state)
        val existing = repo.observeReminders("test-food").first().first { it.id == "reminder" }
        success(repo.updateReminder(existing.copy(dueAt = at.plusSeconds(120), createdAt = at.minusSeconds(10))))
        assertEquals(at, db.reminderDao().findById("reminder")!!.createdAt)
        assertTrue(success(repo.findDueReminders(at)).isEmpty())
        success(repo.deleteReminder("reminder"))
        assertEquals(2, repo.observeReminders("test-food").first().size)
        failure(repo.updateReminder(existing), DatabaseError.MissingRecord)
    }

    @Test fun invalidInputsAndMissingFoodNeverCreateReplacementRows() = runBlocking {
        failure(repo.saveScan(scan("missing-rescan", "missing"), at), DatabaseError.MissingRecord)
        failure(repo.addScanToFood("missing", scan("wrong-association"), at), DatabaseError.InvalidInput)
        failure(repo.markUsed("missing"), DatabaseError.MissingRecord)
        failure(repo.deleteFood("missing"), DatabaseError.MissingRecord)
        failure(repo.updateFood(food()), DatabaseError.MissingRecord)
        failure(repo.insertReminder(reminder(foodId = "missing")), DatabaseError.MissingRecord)
        failure(repo.insertFood(food().copy(isDemo = true)), DatabaseError.InvalidInput)
        failure(repo.insertFood(food().copy(id = "demo-food")), DatabaseError.InvalidInput)
        failure(repo.insertFood(food().copy(quantity = Double.NaN)), DatabaseError.InvalidInput)
        failure(repo.markUsed(""), DatabaseError.InvalidInput)
        failure(repo.saveScan(scan(""), at), DatabaseError.InvalidInput)
        failure(repo.insertReminder(reminder("demo-reminder")), DatabaseError.InvalidInput)
        failure(repo.saveScan(scan("demo-scan").copy(result = DemoResults.create(FreshnessCondition.Fresh)), at), DatabaseError.InvalidInput)
        assertTrue(repo.observeAllFood().first().isEmpty())
        assertTrue(repo.observeScanHistory().first().isEmpty())
        assertTrue(repo.observeAllOutcomes().first().isEmpty())
    }

    @Test fun sqliteForeignKeysRejectOrphanScanAndReminder() = runBlocking {
        val orphan = scan("orphan", "missing").toEntity("orphan-scan", at)
        val scanError = runCatching { db.scanRecordDao().insert(orphan) }.exceptionOrNull()
        val reminderError = runCatching { db.reminderDao().insert(reminder(foodId = "missing")) }.exceptionOrNull()
        assertTrue(scanError is android.database.sqlite.SQLiteConstraintException)
        assertTrue(reminderError is android.database.sqlite.SQLiteConstraintException)
        assertTrue(repo.observeScanHistory().first().isEmpty())
    }

    @Test fun competingTerminalOperationsRecordOnlyOneOutcome() = runBlocking {
        success(repo.insertFood(food()))
        val results = awaitAll(
            async(Dispatchers.IO) { repo.markUsed("test-food") },
            async(Dispatchers.IO) { repo.markDiscarded("test-food") }
        )
        assertEquals(1, results.count { it is DatabaseResult.Success })
        assertEquals(1, results.count { it is DatabaseResult.Failure && it.reason == DatabaseError.InactiveFood })
        assertEquals(1, repo.observeAllOutcomes().first().size)
        assertTrue(repo.observeActiveFood().first().isEmpty())
    }

    @Test fun fileDatabaseKeepsFoodScansOutcomesAndRemindersAcrossReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase-one-test-${java.util.UUID.randomUUID()}.db"
        var disk: FreshCheckDatabase? = null
        try {
            val first = Room.databaseBuilder(context, FreshCheckDatabase::class.java, name).build()
            disk = first
            val writer = RoomFreshCheckRepository(first, clock)
            success(writer.insertFood(food("active")))
            success(writer.insertFood(food("used")))
            success(writer.saveScan(scan("disk-history", "used"), at))
            success(writer.insertReminder(reminder(foodId = "active")))
            success(writer.markUsed("used"))
            first.close()
            val reopened = Room.databaseBuilder(context, FreshCheckDatabase::class.java, name).build()
            disk = reopened
            val reader = RoomFreshCheckRepository(reopened, clock)
            assertEquals("active", reader.observeActiveFood().first().single().id)
            assertEquals(2, reader.observeAllFood().first().size)
            assertEquals(1, reader.observeScanHistory().first().size)
            assertEquals(1, reader.observeUsedOutcomes().first().size)
            assertEquals(ReminderState.DefinitionOnly, reader.observeReminders("active").first().single().state)
        } finally {
            disk?.close()
            context.deleteDatabase(name) // Only this test's uniquely named database.
        }
    }
}
