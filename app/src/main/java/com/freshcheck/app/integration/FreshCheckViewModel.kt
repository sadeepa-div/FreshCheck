package com.freshcheck.app.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshcheck.app.data.mapper.requireProductionId
import com.freshcheck.app.data.mapper.toEntity
import com.freshcheck.app.data.repository.*
import com.freshcheck.app.navigation.AppUiData
import com.freshcheck.app.navigation.UiOperationResult
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.scan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.time.Clock
import com.freshcheck.app.reminders.*

/** Display acceptance is deliberately different from confirmation of a database commit. */
sealed interface AnalysisAcceptance {
    data object Accepted : AnalysisAcceptance
    data class Rejected(val message: String) : AnalysisAcceptance
}

/** One owner per Activity ViewModelStore. Repository construction and work run off the main thread. */
class FreshCheckViewModel(
    private val repositoryProvider: () -> FreshCheckRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    private val reminderProvider: (() -> ReminderCoordinator)? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiData(isLoading = true))
    val uiState: StateFlow<AppUiData> = mutableState.asStateFlow()
    private var repository: FreshCheckRepository? = null
    private var observation: Job? = null
    private val mutation = Mutex()
    private val currentRequest = MutableStateFlow<ScanRequest?>(null)
    /** Member 2 can observe request identity without owning navigation or database state. */
    val scanRequests: StateFlow<ScanRequest?> = currentRequest.asStateFlow()
    private val savedRequests = mutableSetOf<String>()
    private var readError: String? = null
    private var commandError: String? = null

    private val reminderMutable = MutableStateFlow(ReminderUiData())
    val reminderUi: StateFlow<ReminderUiData> = reminderMutable.asStateFlow()
    private var reminderCoordinator: ReminderCoordinator? = null
    private var reminderObservation: Job? = null

    init { retryLoad(); initializeReminders() }

    private fun initializeReminders() {
        if (reminderProvider == null || reminderObservation?.isActive == true) return
        reminderObservation = viewModelScope.launch {
            try {
                val coordinator = reminderCoordinator ?: withContext(ioDispatcher) { reminderProvider.invoke() }
                    .also { reminderCoordinator = it }
                val issue = withContext(ioDispatcher) { coordinator.reconcile() }
                reminderMutable.update { it.copy(error = issue) }
                coordinator.changes.conflate().map { withContext(ioDispatcher) { coordinator.snapshot() } }.collect { snapshot ->
                    reminderMutable.update { snapshot.copy(busy = it.busy, error = it.error) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                reminderMutable.update { it.copy(error = "Reminder service could not load. Refresh to retry.", ready = false) }
            }
        }
    }

    fun refreshReminders() {
        val coordinator = reminderCoordinator
        if (coordinator == null) { initializeReminders(); return }
        viewModelScope.launch {
            try {
                val error = withContext(ioDispatcher) { coordinator.reconcile() }
                val snapshot = withContext(ioDispatcher) { coordinator.snapshot() }
                reminderMutable.update { snapshot.copy(busy = it.busy, error = error) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { reminderMutable.update { it.copy(error = "Reminder reconciliation could not finish. Retry.") } }
        }
    }

    suspend fun saveReminder(draft: ReminderDraft) = reminderCommand { it.save(draft) }
    suspend fun disableReminder(id: String) = reminderCommand { it.disable(id) }
    suspend fun deleteReminder(id: String) = reminderCommand { it.delete(id) }
    suspend fun setReminderPreferences(value: ReminderPreferences) = reminderCommand {
        val error = it.setPreferences(value)
        ReminderResult(true, false, error ?: "Preferences saved. Scheduling still depends on Android permission and channel settings.", error = error)
    }

    private suspend fun reminderCommand(block: suspend (ReminderCoordinator) -> ReminderResult): ReminderResult =
        withContext(Dispatchers.Main.immediate) {
            val coordinator = reminderCoordinator
                ?: return@withContext ReminderResult(false, false, "Reminder service is still loading.")
            if (!mutation.tryLock()) return@withContext ReminderResult(false, false, "Another operation is in progress.")
            reminderMutable.update { it.copy(busy = true) }
            try {
                val result = withContext(ioDispatcher) { block(coordinator) }
                val snapshot = withContext(ioDispatcher) { coordinator.snapshot() }
                reminderMutable.value = snapshot.copy(error = result.error ?: if (!result.saved) result.message else null)
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                reminderMutable.update { it.copy(error = "Reminder operation was not fully confirmed. Refresh saved state.") }
                ReminderResult(false, false, "Reminder operation was not fully confirmed. Refresh saved state.")
            } finally {
                reminderMutable.update { it.copy(busy = false) }
                mutation.unlock()
            }
        }

    fun openNotificationFood(target: NotificationFoodTarget): Boolean {
        if (!mutableState.value.inventoryConnected) return false
        if (!ReminderRules.realId(target.foodId) || mutableState.value.activeFood.none { it.id == target.foodId && !it.isDemo }) {
            failed("The food item from this reminder is no longer active or available.")
            return false
        }
        mutableState.update { it.copy(foodToOpen = target) }
        return true
    }
    fun foodOpened() { mutableState.update { it.copy(foodToOpen = null) } }

    /** Explicit retry retains last-known records; an error is never mapped to demo or empty success. */
    fun retryLoad() {
        observation?.cancel()
        readError = null
        mutableState.update { it.copy(inventoryConnected = false, isLoading = true, error = null) }
        observation = viewModelScope.launch {
            try {
                val source = repository ?: withContext(ioDispatcher) { repositoryProvider() }.also { repository = it }
                combine(
                    observe("inventory", source.observeActiveFood()) { rows ->
                        rows.map { item ->
                            require(!item.isDemo); requireProductionId(item.id)
                            item.produceId?.let(::requireProductionId)
                            item.scans.forEach { requireProductionId(it.id) }
                            item.copy(scans = item.scans.toList())
                        }
                    },
                    observe("scan history", source.observeScanHistory()) { rows ->
                        rows.onEach {
                            require(!it.isDemo); requireProductionId(it.id)
                            it.foodId?.let(::requireProductionId)
                        }.toList()
                    },
                    observe("Used outcomes", source.observeUsedOutcomes()) { rows ->
                        rows.onEach {
                            require(!it.isDemo && it.outcome == FoodOutcome.Used)
                            requireProductionId(it.id); requireProductionId(it.foodId)
                        }.toList()
                    },
                    observe("Discarded outcomes", source.observeDiscardedOutcomes()) { rows ->
                        rows.onEach {
                            require(!it.isDemo && it.outcome == FoodOutcome.Discarded)
                            requireProductionId(it.id); requireProductionId(it.foodId)
                        }.toList()
                    }
                ) { food, history, used, discarded ->
                    val streams = listOf(food, history, used, discarded)
                    readError = streams.mapNotNull { it.error }.joinToString(" ").ifBlank { null }
                    mutableState.value.copy(
                        inventoryConnected = streams.all { it.loaded } && readError == null,
                        activeFood = if (food.loaded) food.rows else mutableState.value.activeFood,
                        scanHistory = if (history.loaded) history.rows else mutableState.value.scanHistory,
                        usedEvents = if (used.loaded) used.rows else mutableState.value.usedEvents,
                        discardedEvents = if (discarded.loaded) discarded.rows else mutableState.value.discardedEvents,
                        isLoading = readError == null && streams.any { !it.loaded },
                        error = readError ?: commandError
                    )
                }.collect { mutableState.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                readError = "Unable to open the local database. Retry loading your saved records."
                mutableState.update { it.copy(inventoryConnected = false, isLoading = false, error = readError) }
            }
        }
    }

    private data class Observed<T>(
        val rows: List<T> = emptyList(), val loaded: Boolean = false, val error: String? = null
    )
    private fun <T> observe(
        label: String, source: Flow<List<T>>, snapshot: (List<T>) -> List<T>
    ): Flow<Observed<T>> = flow {
        var latest = Observed<T>()
        emit(latest)
        try {
            source.collect {
                latest = Observed(snapshot(it), loaded = true)
                emit(latest)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            emit(latest.copy(error = "Unable to load $label. Retry loading saved records; samples were not substituted."))
        }
    }.flowOn(ioDispatcher)

    fun onScanRequest(request: ScanRequest) {
        if (request == currentRequest.value) return
        currentRequest.value = request
        mutableState.update { it.copy(completedAnalysis = null) }
    }

    fun onScanClosed() {
        currentRequest.value = null
        mutableState.update { it.copy(completedAnalysis = null) }
    }

    /** Member 2 calls this with its original request and actual event time, never a formatted label. */
    suspend fun acceptAnalysis(submission: ScanSubmission): AnalysisAcceptance = withContext(Dispatchers.Main.immediate) {
        val problem = scanProblem(submission)
        if (problem != null) AnalysisAcceptance.Rejected(problem)
        else {
            mutableState.update { it.copy(completedAnalysis = submission) }
            AnalysisAcceptance.Accepted
        }
    }

    suspend fun editFood(item: FoodItemUi) = execute {
        item.toEntity(clock.instant()) // Reject provenance even when using a test/alternate repository.
        it.updateFood(item)
    }
    suspend fun markUsed(id: String) = execute { requireProductionId(id); it.markUsed(id) }
    suspend fun markDiscarded(id: String) = execute { requireProductionId(id); it.markDiscarded(id) }
    suspend fun deleteFood(id: String) = execute { requireProductionId(id); it.deleteFood(id) }
    suspend fun deleteScan(id: String) = execute { requireProductionId(id); it.deleteScan(id) }
    suspend fun clearHistory() = execute { it.clearScanHistory() }

    suspend fun saveScan(submission: ScanSubmission): UiOperationResult = withContext(Dispatchers.Main.immediate) {
        val problem = scanProblem(submission)
        when {
            problem != null -> UiOperationResult.Failed(problem)
            mutableState.value.completedAnalysis != submission ->
                UiOperationResult.Failed("This is not the current accepted analysis. Run the current scan again.")
            submission.request.mode == ScanMode.AfterBuying && submission.rescanFoodId == null ->
                UiOperationResult.Failed("New food creation is pending: quantity, unit, storage and date added must be supplied before saving.")
            else -> {
                val result = execute { source ->
                    val at = requireNotNull(submission.scannedAt)
                    submission.rescanFoodId?.let { source.addScanToFood(it, submission, at) }
                        ?: source.saveScan(submission, at)
                }
                if (result == UiOperationResult.Confirmed) savedRequests += submission.request.requestId
                result
            }
        }
    }

    private fun scanProblem(submission: ScanSubmission): String? {
        if (submission.request != currentRequest.value) return "This scan request is stale or closed. Start a new scan."
        if (submission.request.requestId in savedRequests) return "This scan has already been saved."
        return try {
            val at = requireNotNull(submission.scannedAt) { "The scanner must supply the actual analysis timestamp before saving." }
            require(!at.isAfter(clock.instant())) { "The analysis timestamp is in the future. Check the scanner timestamp." }
            submission.toEntity("validation-only", at)
            null
        } catch (failure: IllegalArgumentException) {
            failure.message ?: "Invalid scan submission."
        }
    }

    /** The caller owns the operation lifetime. Cancellation rolls back cancellable Room work. */
    private suspend fun execute(
        block: suspend (FreshCheckRepository) -> DatabaseResult<*>
    ): UiOperationResult = withContext(Dispatchers.Main.immediate) {
        val source = repository
        if (source == null || !mutableState.value.inventoryConnected)
            return@withContext UiOperationResult.Failed("Saved data is not ready. Wait for loading or retry after the database error.")
        if (!mutation.tryLock())
            return@withContext UiOperationResult.Failed("Another database operation is in progress. Please wait.")
        try {
            val result = withContext(ioDispatcher) { block(source) }
            when (result) {
                is DatabaseResult.Success -> {
                    commandError = try {
                        withContext(ioDispatcher) { reminderCoordinator?.reconcile() }?.let {
                            "Change saved. Reminder reconciliation needs attention: $it"
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { "Change saved. Reminder cancellation could not be confirmed; refresh reminders." }
                    mutableState.update { it.copy(error = readError ?: commandError) }
                    UiOperationResult.Confirmed
                }
                is DatabaseResult.Failure -> failed(result.message)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            failed(invalid.message ?: "Invalid production record.")
        } catch (failure: Exception) {
            failed("The database operation failed. No success was confirmed; retry after checking saved records.")
        } finally {
            mutation.unlock()
        }
    }

    private fun failed(message: String): UiOperationResult.Failed {
        commandError = message
        mutableState.update { it.copy(error = readError ?: message) }
        return UiOperationResult.Failed(message)
    }
}
