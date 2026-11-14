package com.freshcheck.app
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InventoryUiTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val items = sampleFood(today)
    @Test fun searchAndEveryFilterSelectOnlyMatchingItems() {
        fun show(q: String = "", f: FoodFilter = FoodFilter.All) = visibleFood(items, q, f, FoodSort.Name, today)
        assertEquals(listOf("Avocado"), show(" AVOCADO ").map { it.name })
        assertEquals(2, show("vegetable").size)
        assertEquals(3, show(f = FoodFilter.Fruits).size)
        assertEquals(2, show(f = FoodFilter.Vegetables).size)
        assertEquals(2, show(f = FoodFilter.Fresh).size)
        assertEquals(1, show(f = FoodFilter.UseSoon).size)
        assertEquals(1, show(f = FoodFilter.Spoiled).size)
        assertEquals(4, show(f = FoodFilter.Refrigerated).size)
        assertEquals(1, show(f = FoodFilter.RoomTemperature).size)
        assertTrue(show("missing").isEmpty())
    }
    @Test fun sortingHandlesMissingDatesAndNeverReclassifiesOverdueFood() {
        fun sorted(s: FoodSort) = visibleFood(items, "", FoodFilter.All, s, today)
        assertEquals("demo-avocado", sorted(FoodSort.Recheck).first().id)
        assertEquals("Apple", sorted(FoodSort.Name).first().name)
        assertEquals("demo-tomato", sorted(FoodSort.Priority).first().id)
        val newest = items.first().copy(id = "demo-new", added = today)
        assertEquals("demo-new", visibleFood(items + newest, "", FoodFilter.All, FoodSort.Recent, today).first().id)
        assertEquals("Re-check overdue", priorityLabel(items.first(), today))
        assertEquals(FreshnessCondition.UseSoon, items.first().condition)
        assertEquals("Re-check today", priorityLabel(items[1], today))
        assertEquals("Check soon", priorityLabel(items[2], today))
        assertEquals("No reminder set", priorityLabel(items.last(), today))
    }
    @Test fun validationRejectsInvalidQuantitiesAndImpossibleDates() {
        val valid = FoodEditValues("2.5", "pieces", today.toString(), today.toString(), "")
        assertTrue(validateFoodEdit(valid, today).isEmpty())
        listOf("0", "-1", "NaN", "Infinity", "abc").forEach {
            assertTrue(validateFoodEdit(valid.copy(quantity = it), today).containsKey("quantity"))
        }
        assertTrue(validateFoodEdit(valid.copy(added = "2026-02-30"), today).containsKey("added"))
        assertTrue(validateFoodEdit(valid.copy(added = today.plusDays(1).toString()), today).containsKey("added"))
        assertTrue(validateFoodEdit(valid.copy(recheck = today.minusDays(1).toString()), today).containsKey("recheck"))
        assertTrue(validateFoodEdit(valid.copy(unit = ""), today).containsKey("unit"))
    }
    @Test fun localChangesUseStableIdsAndPreserveHistory() {
        val state = DemoInventory(items)
        val edited = items[0].copy(quantity = 4.0)
        state.update(edited)
        assertEquals(4.0, state.items.first { it.id == edited.id }.quantity, 0.0)
        assertEquals(items[0].scans, state.items.first().scans)
        state.remove(edited.id)
        assertFalse(state.items.any { it.id == edited.id })
        state.clear()
        assertTrue(state.items.isEmpty())
    }
}
