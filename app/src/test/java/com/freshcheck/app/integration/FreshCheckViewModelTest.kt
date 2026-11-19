package com.freshcheck.app.integration

import androidx.lifecycle.ViewModelStore
import com.freshcheck.app.data.repository.*
import com.freshcheck.app.navigation.UiOperationResult
import com.freshcheck.app.ui.home.homeSummary
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class FreshCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val at = Instant.parse("2026-11-18T08:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() {
        stores.forEach { it.clear() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun model(repo: FakeFreshCheckRepository): FreshCheckViewModel = model { repo }
    private fun model(provider: () -> FreshCheckRepository): FreshCheckViewModel =
        FreshCheckViewModel(provider, dispatcher, clock).also { vm ->
            stores += ViewModelStore().apply { put("test", vm) }
        }
    private fun food(id: String = "food") = FoodItemUi(id, "Apple", ProduceCategory.Fruit, 2.0,
        "pieces", FoodStorage.Refrigerated, LocalDate.of(2026, 11, 18), null, emptyList(), produceId = "apple")
    private fun scan(id: String = "request", foodId: String? = null, mode: ScanMode = ScanMode.BeforeBuying) =
        ScanSubmission(ScanRequest(id, mode, foodId),
            AnalysisResult("Apple", ProduceCategory.Fruit, FreshnessCondition.Uncertain, produceId = "apple"), at)
    private fun failed(result: AnalysisAcceptance, contains: String = "") {
        assertTrue(result.toString(), result is AnalysisAcceptance.Rejected)
        assertTrue((result as AnalysisAcceptance.Rejected).message.contains(contains, ignoreCase = true))
    }
    private fun failed(result: UiOperationResult, contains: String = "") {
        assertTrue(result.toString(), result is UiOperationResult.Failed)
        assertTrue((result as UiOperationResult.Failed).message, result.message.contains(contains, ignoreCase = true))
    }

    @Test fun initialLoadingBecomesConnectedEmptyWithoutDemoRecords() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.inventoryConnected)
        runCurrent()
        val state = vm.uiState.value
        assertTrue(state.inventoryConnected)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.activeFood.isEmpty() && state.scanHistory.isEmpty())
        assertTrue(state.usedEvents.isEmpty() && state.discardedEvents.isEmpty())
    }

    @Test fun allStreamsMustEmitBeforeConnectionIsReady() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val input = MutableSharedFlow<List<FoodItemUi>>()
        repo.inventorySource = input
        val vm = model(repo)
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.inventoryConnected)
        input.emit(emptyList())
        runCurrent()
        assertTrue(vm.uiState.value.inventoryConnected)
    }

    @Test fun inventoryHistoryAndOutcomesUpdateExistingHomeAndInsightCalculations() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        runCurrent()
        repo.food.value = listOf(food("used"), food("discarded"))
        repo.history.value = listOf(ScanHistoryRecord("standalone", "Apple", ProduceCategory.Fruit,
            LocalDateTime.ofInstant(at, ZoneOffset.UTC), FreshnessCondition.Uncertain, null, ScanMode.BeforeBuying))
        runCurrent()
        assertEquals(2, homeSummary(vm.uiState.value.activeFood, vm.uiState.value.scanHistory).totalFood)
        assertNull(vm.uiState.value.scanHistory.single().foodId)
        assertEquals(UiOperationResult.Confirmed, vm.markUsed("used"))
        assertEquals(UiOperationResult.Confirmed, vm.markDiscarded("discarded"))
        runCurrent()
        val state = vm.uiState.value
        assertTrue(state.activeFood.isEmpty())
        assertEquals(1, state.usedEvents.size)
        assertEquals(1, state.discardedEvents.size)
        assertEquals(1, state.scanHistory.size)
        val summary = activitySummary(state.activeFood, state.scanHistory, state.usedEvents + state.discardedEvents,
            LocalDate.now(clock), InsightPeriod.Week)
        assertEquals(1, summary.used)
        assertEquals(1, summary.discarded)
        assertEquals(1, summary.scans)
    }

    @Test fun editDeleteHistoryDeleteAndClearCallbacksConfirmExactlyOnce() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply { food.value = listOf(food()) }
        val vm = model(repo)
        runCurrent()
        val actions = vm.integrationActions(backgroundScope, connected = true)
        val completions = mutableListOf<UiOperationResult>()
        actions.food.edit(food().copy(quantity = 3.0), completions::add)
        runCurrent()
        assertEquals(listOf(UiOperationResult.Confirmed), completions)
        assertEquals(3.0, vm.uiState.value.activeFood.single().quantity, 0.0)
        actions.food.delete("food", completions::add)
        runCurrent()
        assertEquals(2, completions.size)
        assertTrue(vm.uiState.value.activeFood.isEmpty())
        val results = mutableListOf<Boolean>()
        assertTrue(actions.history.canDelete && actions.history.canClear)
        actions.history.delete("scan", results::add)
        runCurrent()
        actions.history.clear(results::add)
        runCurrent()
        assertEquals(listOf(true, true), results)
        assertEquals(listOf("edit", "delete", "deleteScan", "clearHistory"), repo.calls)
    }

    @Test fun failuresNeverConfirmAndDoNotOverwriteRecords() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply {
            food.value = listOf(food())
            failure = DatabaseResult.Failure(DatabaseError.Storage, "Disk unavailable")
        }
        val vm = model(repo)
        runCurrent()
        val results = mutableListOf<UiOperationResult>()
        val history = mutableListOf<Boolean>()
        val actions = vm.integrationActions(backgroundScope, true)
        actions.food.edit(food().copy(quantity = 3.0), results::add)
        runCurrent()
        actions.food.delete("food", results::add)
        runCurrent()
        actions.history.clear(history::add)
        runCurrent()
        assertEquals(2, results.size)
        results.forEach { failed(it, "Disk unavailable") }
        assertEquals(listOf(false), history)
        assertEquals(2.0, vm.uiState.value.activeFood.single().quantity, 0.0)
        assertEquals("Disk unavailable", vm.uiState.value.error)
    }

    @Test fun failedInitializationCanRetryWithoutRecreatingSuccessfulRepository() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        var attempts = 0
        val vm = model { if (++attempts == 1) error("open failure") else repo }
        runCurrent()
        assertFalse(vm.uiState.value.inventoryConnected)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
        failed(vm.deleteFood("food"), "not ready")
        vm.retryLoad()
        runCurrent()
        assertTrue(vm.uiState.value.inventoryConnected)
        vm.retryLoad()
        runCurrent()
        assertEquals(2, attempts)
    }

    @Test fun observationErrorRetainsLastProductionSnapshotAndCanRecover() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val fail = CompletableDeferred<Unit>()
        repo.inventorySource = flow {
            emit(listOf(food()))
            fail.await()
            error("read failure")
        }
        val vm = model(repo)
        runCurrent()
        fail.complete(Unit)
        runCurrent()
        assertFalse(vm.uiState.value.inventoryConnected)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("food", vm.uiState.value.activeFood.single().id)
        assertTrue(vm.uiState.value.error!!.contains("inventory"))
        repo.inventorySource = repo.food
        vm.retryLoad()
        runCurrent()
        assertTrue(vm.uiState.value.inventoryConnected)
        assertTrue(vm.uiState.value.activeFood.isEmpty())
    }

    @Test fun demoWritesAndPoisonedDemoObservationAreRejected() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        runCurrent()
        failed(vm.editFood(food().copy(isDemo = true)), "Demonstration")
        failed(vm.deleteFood("demo-food"), "non-demo")
        val submission = scan().copy(result = DemoResults.create(FreshnessCondition.Fresh))
        vm.onScanRequest(submission.request)
        failed(vm.acceptAnalysis(submission), "Demonstration")
        assertTrue(repo.calls.isEmpty())
        repo.food.value = listOf(food().copy(isDemo = true))
        runCurrent()
        assertFalse(vm.uiState.value.inventoryConnected)
        assertTrue(vm.uiState.value.activeFood.isEmpty())
    }

    @Test fun realStandaloneScanPersistsOnlyAcceptedPayloadWithOriginalTimestamp() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        runCurrent()
        val submission = scan()
        vm.onScanRequest(submission.request)
        failed(vm.saveScan(submission), "accepted")
        assertEquals(AnalysisAcceptance.Accepted, vm.acceptAnalysis(submission))
        vm.onScanRequest(submission.request) // Re-attaching the same restored scanner is idempotent.
        assertEquals(submission.request, vm.scanRequests.value)
        assertEquals(submission, vm.uiState.value.completedAnalysis)
        assertEquals(UiOperationResult.Confirmed, vm.saveScan(submission))
        runCurrent()
        assertEquals(submission, repo.saved)
        assertEquals(ScanMode.BeforeBuying, vm.uiState.value.scanHistory.single().mode)
        assertNull(vm.uiState.value.scanHistory.single().confidence)
        assertNull(vm.uiState.value.scanHistory.single().foodId)
        failed(vm.saveScan(submission), "already")
        assertEquals(1, repo.calls.count { it == "scan" })
        vm.onScanClosed()
        assertNull(vm.scanRequests.value)
        assertNull(vm.uiState.value.completedAnalysis)
        failed(vm.acceptAnalysis(submission), "stale")
    }

    @Test fun rescanIdentityIsPreservedAndMissingFoodIsNotReplaced() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply { food.value = listOf(food()) }
        val vm = model(repo)
        runCurrent()
        val submission = scan(foodId = "food", mode = ScanMode.AfterBuying)
        vm.onScanRequest(submission.request)
        assertEquals(AnalysisAcceptance.Accepted, vm.acceptAnalysis(submission))
        vm.onScanRequest(submission.request) // Re-attaching the same restored scanner is idempotent.
        assertEquals(submission.request, vm.scanRequests.value)
        assertEquals(submission, vm.uiState.value.completedAnalysis)
        assertEquals(UiOperationResult.Confirmed, vm.saveScan(submission))
        assertEquals("food", repo.saved!!.rescanFoodId)
        val missing = scan("missing", "absent", ScanMode.AfterBuying)
        vm.onScanRequest(missing.request)
        assertEquals(AnalysisAcceptance.Accepted, vm.acceptAnalysis(missing))
        failed(vm.saveScan(missing), "does not exist")
        assertEquals(listOf("food"), repo.food.value.map { it.id })
    }

    @Test fun staleRequestsChangedModesMissingTimesAndFutureTimesCannotSave() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        runCurrent()
        val old = scan()
        vm.onScanRequest(old.request)
        vm.onScanRequest(old.request.copy(mode = ScanMode.AfterBuying))
        failed(vm.acceptAnalysis(old), "stale")
        vm.onScanRequest(old.request)
        failed(vm.acceptAnalysis(old.copy(scannedAt = null)), "timestamp")
        failed(vm.acceptAnalysis(old.copy(scannedAt = at.plusSeconds(60))), "future")
        failed(vm.acceptAnalysis(old.copy(request = old.request.copy(rescanFoodId = "other"))), "stale")
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun newAfterBuyingFoodRemainsPendingWithoutUserSuppliedDetails() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        val vm = model(repo)
        runCurrent()
        val submission = scan(mode = ScanMode.AfterBuying)
        vm.onScanRequest(submission.request)
        assertEquals(AnalysisAcceptance.Accepted, vm.acceptAnalysis(submission))
        failed(vm.saveScan(submission), "quantity")
        assertTrue(repo.calls.isEmpty())
        assertTrue(repo.food.value.isEmpty())
    }

    @Test fun repeatedTapsCannotStartDuplicateDatabaseWork() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply {
            food.value = listOf(food())
            barrier = CompletableDeferred()
        }
        val vm = model(repo)
        runCurrent()
        val results = mutableListOf<UiOperationResult>()
        val actions = vm.integrationActions(backgroundScope, true)
        actions.food.delete("food", results::add)
        runCurrent()
        actions.food.delete("food", results::add)
        runCurrent()
        assertEquals(listOf("delete"), repo.calls)
        assertEquals(1, results.size)
        failed(results.single(), "in progress")
        repo.barrier!!.complete(Unit)
        runCurrent()
        assertEquals(2, results.size)
        assertEquals(1, results.count { it == UiOperationResult.Confirmed })
    }

    @Test fun disposedRouteCancelsWorkAndSuppressesCompletion() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply {
            food.value = listOf(food())
            barrier = CompletableDeferred()
        }
        val vm = model(repo)
        runCurrent()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val results = mutableListOf<UiOperationResult>()
        vm.integrationActions(scope, true).food.delete("food", results::add)
        runCurrent()
        scope.cancel()
        repo.barrier!!.complete(Unit)
        runCurrent()
        assertTrue(results.isEmpty())
        assertEquals(1, repo.food.value.size)
        // The mutation lock was released; a new live caller can retry.
        assertEquals(UiOperationResult.Confirmed, vm.deleteFood("food"))
    }

    @Test fun clearingViewModelStoreCancelsRepositoryObservation() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository()
        var cancelled = false
        repo.inventorySource = flow {
            try { emit(emptyList()); awaitCancellation() } finally { cancelled = true }
        }
        model(repo)
        runCurrent()
        stores.last().clear()
        runCurrent()
        assertTrue(cancelled)
    }

    @Test fun notificationNavigationWaitsForDataRejectsMissingFoodAndAcknowledgesOnce() = runTest(dispatcher) {
        val repo = FakeFreshCheckRepository().apply { food.value = listOf(food()) }
        val vm = model(repo)
        val target = com.freshcheck.app.reminders.NotificationFoodTarget("food", "tap-token")
        assertFalse(vm.openNotificationFood(target))
        runCurrent()
        assertTrue(vm.openNotificationFood(target))
        assertEquals(target, vm.uiState.value.foodToOpen)
        vm.foodOpened()
        vm.foodOpened()
        assertNull(vm.uiState.value.foodToOpen)
        assertFalse(vm.openNotificationFood(target.copy(foodId = "missing")))
        assertFalse(vm.openNotificationFood(target.copy(foodId = "demo-food")))
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun completionGateDefersAndDeliversOnceOrDropsAfterDisposal() = runTest(dispatcher) {
        val ready = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val values = mutableListOf<Boolean>()
        val once = UiCompletionGate(scope) { ready.await() }.once(values::add)
        once(true)
        once(false)
        runCurrent()
        assertTrue(values.isEmpty())
        ready.complete(Unit)
        runCurrent()
        assertEquals(listOf(true), values)
        val discarded = UiCompletionGate(scope).once(values::add)
        scope.cancel()
        discarded(false)
        runCurrent()
        assertEquals(listOf(true), values)
    }
}
