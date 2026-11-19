package com.freshcheck.app.integration

import com.freshcheck.app.navigation.*
import com.freshcheck.app.ui.history.HistoryActions
import kotlinx.coroutines.*

/** A route-scoped adapter; it never stores Activity/Composable completion callbacks in the ViewModel. */
fun FreshCheckViewModel.integrationActions(scope: CoroutineScope, connected: Boolean): AppIntegrationActions {
    fun complete(done: (UiOperationResult) -> Unit, operation: suspend () -> UiOperationResult) {
        scope.launch(Dispatchers.Main.immediate) {
            val result = operation()
            ensureActive()
            done(result)
        }
    }
    return AppIntegrationActions(
        onScanRequest = ::onScanRequest,
        onScanClosed = ::onScanClosed,
        retryLoad = ::retryLoad,
        onFoodOpened = ::foodOpened,
        food = FoodMutationActions(
            markUsed = { id, done -> complete(done) { markUsed(id) } },
            markDiscarded = { id, done -> complete(done) { markDiscarded(id) } },
            delete = { id, done -> complete(done) { deleteFood(id) } },
            edit = { item, done -> complete(done) { editFood(item) } }
        ),
        history = HistoryActions(
            canDelete = connected,
            delete = { id, done -> complete({ done(it == UiOperationResult.Confirmed) }) { deleteScan(id) } },
            canClear = connected,
            clear = { done -> complete({ done(it == UiOperationResult.Confirmed) }) { clearHistory() } }
        ),
        reminders = com.freshcheck.app.reminders.ReminderActions(
            save = { draft, done -> scope.launch(Dispatchers.Main.immediate) { done(saveReminder(draft)) } },
            disable = { id, done -> scope.launch(Dispatchers.Main.immediate) { done(disableReminder(id)) } },
            delete = { id, done -> scope.launch(Dispatchers.Main.immediate) { done(deleteReminder(id)) } },
            setPreferences = { value -> scope.launch(Dispatchers.Main.immediate) { setReminderPreferences(value) } },
            refresh = ::refreshReminders
        ),
        saveScan = { submission, done -> complete(done) { saveScan(submission) } }
    )
}
