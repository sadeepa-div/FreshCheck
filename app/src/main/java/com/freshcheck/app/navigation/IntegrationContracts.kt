package com.freshcheck.app.navigation
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.history.HistoryActions
import com.freshcheck.app.ui.scan.ScanSubmission

/** UI projections only. A lifecycle-aware adapter should collect Member 3's flows and supply snapshots. */
data class AppUiData(
    val inventoryConnected: Boolean = false,
    val activeFood: List<FoodItemUi> = emptyList(),
    val scanHistory: List<ScanHistoryRecord> = emptyList(),
    val usedEvents: List<OutcomeRecord> = emptyList(),
    val discardedEvents: List<OutcomeRecord> = emptyList(),
    val completedAnalysis: ScanSubmission? = null,
    val nutritionProfile: com.freshcheck.app.ui.nutrition.NutritionProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val reminderData: com.freshcheck.app.reminders.ReminderUiData = com.freshcheck.app.reminders.ReminderUiData(),
    val foodToOpen: com.freshcheck.app.reminders.NotificationFoodTarget? = null
)
sealed interface UiOperationResult {
    data object Confirmed : UiOperationResult
    data class Failed(val message: String) : UiOperationResult
}
typealias FoodCommand = (String, (UiOperationResult) -> Unit) -> Unit
data class FoodMutationActions(
    val markUsed: FoodCommand = { _, done -> done(UiOperationResult.Failed("Food management integration pending. Nothing marked as used.")) },
    val markDiscarded: FoodCommand = { _, done -> done(UiOperationResult.Failed("Food management integration pending. Nothing marked as discarded.")) },
    val delete: FoodCommand = { _, done -> done(UiOperationResult.Failed("Food management integration pending. Nothing deleted.")) },
    val edit: (FoodItemUi, (UiOperationResult) -> Unit) -> Unit = { _, done -> done(UiOperationResult.Failed("Food management integration pending. Changes not saved.")) }
)
data class AppIntegrationActions(
    val onScanRequest: (com.freshcheck.app.ui.scan.ScanRequest) -> Unit = {},
    val food: FoodMutationActions = FoodMutationActions(),
    val history: HistoryActions = HistoryActions(),
    val saveScan: (ScanSubmission, (UiOperationResult) -> Unit) -> Unit = { _, done ->
        done(UiOperationResult.Failed("Scan persistence integration pending. Nothing saved."))
    },
    val onScanClosed: () -> Unit = {},
    val retryLoad: () -> Unit = {},
    val reminders: com.freshcheck.app.reminders.ReminderActions = com.freshcheck.app.reminders.ReminderActions(),
    val onFoodOpened: () -> Unit = {}
)
