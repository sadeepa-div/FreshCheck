package com.freshcheck.app.integration

import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.data.repository.*
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.scan.ScanSubmission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.time.*

/** Synthetic, in-memory test fixture. Never used by the application or to seed Room. */
class FakeFreshCheckRepository : FreshCheckRepository {
    val food = MutableStateFlow<List<FoodItemUi>>(emptyList())
    val history = MutableStateFlow<List<ScanHistoryRecord>>(emptyList())
    val used = MutableStateFlow<List<OutcomeRecord>>(emptyList())
    val discarded = MutableStateFlow<List<OutcomeRecord>>(emptyList())
    var inventorySource: Flow<List<FoodItemUi>> = food
    val calls = mutableListOf<String>()
    var failure: DatabaseResult.Failure? = null
    var barrier: CompletableDeferred<Unit>? = null
    var saved: ScanSubmission? = null
    private var scanCount = 0
    override fun observeActiveFood() = inventorySource
    override fun observeAllFood(): Flow<List<FoodItemEntity>> = flowOf(emptyList())
    override suspend fun findFood(id: String) = DatabaseResult.Success(food.value.firstOrNull { it.id == id })
    override suspend fun insertFood(item: FoodItemUi) = change("insert") { food.value += item }
    override suspend fun updateFood(item: FoodItemUi) = change("edit") { food.value = food.value.map { if (it.id == item.id) item else it } }
    override suspend fun deleteFood(id: String) = change("delete") { food.value = food.value.filterNot { it.id == id } }
    override suspend fun markUsed(id: String) = outcome(id, FoodOutcome.Used)
    override suspend fun markDiscarded(id: String) = outcome(id, FoodOutcome.Discarded)
    private suspend fun outcome(id: String, type: FoodOutcome) = change(type.name) {
        val item = food.value.first { it.id == id }
        val event = OutcomeRecord("event-$id", id, item.name, item.category, type, LocalDateTime.of(2026, 11, 18, 8, 0))
        if (type == FoodOutcome.Used) used.value += event else discarded.value += event
        food.value = food.value.filterNot { it.id == id }
    }
    override fun observeScanHistory() = history
    override fun observeScansForFood(foodId: String) = history.map { rows -> rows.filter { it.foodId == foodId } }
    override suspend fun findScan(id: String): DatabaseResult<ScanRecordEntity?> = DatabaseResult.Success(null)
    override suspend fun saveScan(submission: ScanSubmission, scannedAt: Instant): DatabaseResult<String> {
        calls += "scan"
        barrier?.await()
        failure?.let { return it }
        if (submission.rescanFoodId != null && food.value.none { it.id == submission.rescanFoodId })
            return DatabaseResult.Failure(DatabaseError.MissingRecord, "Food item does not exist.")
        saved = submission.copy(scannedAt = scannedAt)
        val id = "stored-${++scanCount}"
        history.value += ScanHistoryRecord(id, submission.result.produceName, submission.result.category,
            LocalDateTime.ofInstant(scannedAt, ZoneOffset.UTC), submission.result.condition, submission.result.confidence,
            submission.request.mode, submission.rescanFoodId, submission.result.imageUri, submission.result.guidance)
        return DatabaseResult.Success(id)
    }
    override suspend fun addScanToFood(foodId: String, submission: ScanSubmission, scannedAt: Instant): DatabaseResult<String> {
        check(foodId == submission.rescanFoodId)
        return saveScan(submission, scannedAt)
    }
    override suspend fun deleteScan(id: String) = change("deleteScan") { history.value = history.value.filterNot { it.id == id } }
    override suspend fun clearScanHistory() = change("clearHistory") { history.value = emptyList() }
    override fun observeUsedOutcomes() = used
    override fun observeDiscardedOutcomes() = discarded
    override fun observeAllOutcomes() = combine(used, discarded) { a, b -> a + b }
    override suspend fun insertReminder(reminder: ReminderEntity): DatabaseResult<Unit> = error("Not used by Phase 2 ViewModel")
    override suspend fun updateReminder(reminder: ReminderEntity): DatabaseResult<Unit> = error("Not used by Phase 2 ViewModel")
    override suspend fun deleteReminder(id: String): DatabaseResult<Unit> = error("Not used by Phase 2 ViewModel")
    override fun observeReminders(foodId: String): Flow<List<ReminderEntity>> = flowOf(emptyList())
    override suspend fun findDueReminders(at: Instant): DatabaseResult<List<ReminderEntity>> = DatabaseResult.Success(emptyList())
    private suspend fun change(name: String, commit: () -> Unit): DatabaseResult<Unit> {
        calls += name
        barrier?.await()
        failure?.let { return it }
        commit()
        return DatabaseResult.Success(Unit)
    }
}
