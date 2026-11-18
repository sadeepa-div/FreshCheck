package com.freshcheck.app.data.repository

import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.ui.insights.OutcomeRecord
import com.freshcheck.app.ui.insights.ScanHistoryRecord
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.scan.ScanSubmission
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Writes report success only after commit. Observation failures propagate for the future UI adapter. */
interface FreshCheckRepository {
    fun observeActiveFood(): Flow<List<FoodItemUi>>
    fun observeAllFood(): Flow<List<FoodItemEntity>>
    suspend fun findFood(id: String): DatabaseResult<FoodItemUi?>
    suspend fun insertFood(item: FoodItemUi): DatabaseResult<Unit>
    suspend fun updateFood(item: FoodItemUi): DatabaseResult<Unit>
    suspend fun deleteFood(id: String): DatabaseResult<Unit>
    suspend fun markUsed(id: String): DatabaseResult<Unit>
    suspend fun markDiscarded(id: String): DatabaseResult<Unit>
    fun observeScanHistory(): Flow<List<ScanHistoryRecord>>
    fun observeScansForFood(foodId: String): Flow<List<ScanHistoryRecord>>
    suspend fun findScan(id: String): DatabaseResult<ScanRecordEntity?>
    /** Explicit event instant; analyzedAtLabel is display text and is never parsed as a timestamp. */
    suspend fun saveScan(submission: ScanSubmission, scannedAt: Instant): DatabaseResult<String>
    suspend fun addScanToFood(foodId: String, submission: ScanSubmission, scannedAt: Instant): DatabaseResult<String>
    suspend fun deleteScan(id: String): DatabaseResult<Unit>
    suspend fun clearScanHistory(): DatabaseResult<Unit>
    fun observeUsedOutcomes(): Flow<List<OutcomeRecord>>
    fun observeDiscardedOutcomes(): Flow<List<OutcomeRecord>>
    fun observeAllOutcomes(): Flow<List<OutcomeRecord>>
    suspend fun insertReminder(reminder: ReminderEntity): DatabaseResult<Unit>
    suspend fun updateReminder(reminder: ReminderEntity): DatabaseResult<Unit>
    suspend fun deleteReminder(id: String): DatabaseResult<Unit>
    fun observeReminders(foodId: String): Flow<List<ReminderEntity>>
    suspend fun findDueReminders(at: Instant): DatabaseResult<List<ReminderEntity>>
}
