package com.freshcheck.app
import com.freshcheck.app.navigation.*
import com.freshcheck.app.ui.home.*
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.*

class IntegrationContractsTest {
    private val today = LocalDate.of(2026, 11, 14)
    @Test fun rescanIdentityIsPreservedAndStaleResultsRejected() {
        val request = ScanRequest("request-1", ScanMode.AfterBuying, "food-42")
        val completion = ScanSubmission(request, DemoResults.create(FreshnessCondition.Fresh))
        assertEquals("food-42", completion.rescanFoodId)
        assertEquals(completion.result, analysisFor(request, completion))
        assertNull(analysisFor(request.copy(requestId = "request-2"), completion))
        assertNull(analysisFor(request.copy(rescanFoodId = "food-other"), completion))
        assertNull(analysisFor(request.copy(mode = ScanMode.BeforeBuying), completion))
        assertNull(ScanSubmission(ScanRequest("before", ScanMode.BeforeBuying), completion.result).rescanFoodId)
    }
    @Test fun homeSummaryUsesInputsAndEmptyDefaults() {
        assertEquals(0, homeSummary(emptyList(), emptyList()).totalFood)
        val food = sampleFood(today)
        val state = homeSummary(food, emptyList())
        assertEquals(5, state.totalFood); assertEquals(1, state.useSoonCount)
        assertEquals(listOf("Avocado"), state.useSoonNames); assertTrue(state.isDemo)
    }
    @Test fun standaloneHistoryIsIndependentOfInventory() {
        val scan = sampleHistory(today).first().copy(foodId = null)
        val state = AppUiData(inventoryConnected = true, scanHistory = listOf(scan))
        assertTrue(state.activeFood.isEmpty())
        assertEquals(1, homeSummary(state.activeFood, state.scanHistory).recentScan?.let { 1 })
        assertEquals(1, activitySummary(state.activeFood, state.scanHistory, emptyList(), today, InsightPeriod.All).scans)
    }
    @Test fun demoOutcomesAreDistinctAndDoNotEraseHistory() {
        val state = DemoInventory(sampleFood(today))
        val historyIds = state.history.map { it.id }
        state.markUsed("demo-avocado")
        state.markDiscarded("demo-spinach")
        state.remove("demo-apple")
        assertEquals(listOf(FoodOutcome.Used, FoodOutcome.Discarded), state.outcomes.map { it.outcome })
        assertEquals(historyIds, state.history.map { it.id })
        assertTrue(state.outcomes.all { it.isDemo })
        val summary = activitySummary(state.items, state.history, state.outcomes,
            LocalDate.now(), InsightPeriod.All)
        assertEquals(1, summary.used); assertEquals(1, summary.discarded)
    }
    @Test fun operationsHaveSeparateCallbacksAndDefaultsNeverConfirm() {
        val calls = mutableListOf<String>()
        val actions = FoodMutationActions(
            markUsed = { id, done -> calls += "used:$id"; done(UiOperationResult.Confirmed) },
            markDiscarded = { id, done -> calls += "discarded:$id"; done(UiOperationResult.Confirmed) },
            delete = { id, done -> calls += "delete:$id"; done(UiOperationResult.Confirmed) })
        actions.markUsed("a") {}; actions.markDiscarded("b") {}; actions.delete("c") {}
        assertEquals(listOf("used:a", "discarded:b", "delete:c"), calls)
        FoodMutationActions().delete("a") { assertTrue(it is UiOperationResult.Failed) }
        AppIntegrationActions().saveScan(ScanSubmission(ScanRequest("x", ScanMode.AfterBuying), DemoResults.create(FreshnessCondition.Fresh))) {
            assertTrue(it is UiOperationResult.Failed)
        }
    }
    @Test fun demoSnapshotCanRestoreHistoryAndOutcomeState() {
        val state = DemoInventory(sampleFood(today))
        state.markUsed("demo-avocado")
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(DemoInventorySnapshot(state.items, state.history, state.outcomes)) }
        val snapshot = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as DemoInventorySnapshot }
        assertEquals(state.history, snapshot.history)
        assertEquals(state.outcomes, snapshot.outcomes)
    }
}
