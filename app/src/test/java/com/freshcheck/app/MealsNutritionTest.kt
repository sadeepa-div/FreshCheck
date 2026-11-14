package com.freshcheck.app
import com.freshcheck.app.ui.meals.*
import com.freshcheck.app.ui.nutrition.*
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MealsNutritionTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val food = sampleFood(today)
    @Test fun nutritionScalesWithServingBasisAndKeepsDemoSource() {
        assertEquals(75.0, scaledNutrient(150.0, 50), .001)
        assertEquals(300.0, scaledNutrient(150.0, 200), .001)
        assertTrue(DemoNutrition.isDemo)
        assertTrue(DemoNutrition.source.contains("synthetic"))
        assertThrows(IllegalArgumentException::class.java) { scaledNutrient(1.0, 0) }
    }
    @Test fun recipeQuantitiesScaleIndependentlyOfAvailability() {
        val recipe = DemoRecipes.first()
        assertEquals(2.0, scaledIngredient(recipe.ingredients.first(), recipe, 4), .001)
        assertThrows(IllegalArgumentException::class.java) { scaledIngredient(recipe.ingredients.first(), recipe, 9) }
    }
    @Test fun matchesExcludeSpoiledUncertainAndInvalidQuantities() {
        val soup = DemoRecipes.first { it.id == "demo-soup" }
        assertTrue(matchIngredients(soup, food).none { it.available })
        assertTrue(matchIngredients(soup, food).filter { it.ingredient.key in setOf("carrot", "tomato") }.all { it.needsReplacementOrCheck })
        assertFalse(eligibleIngredient(food.first().copy(quantity = 0.0)))
        assertFalse(eligibleIngredient(food.first().copy(quantity = Double.NaN)))
        assertTrue(eligibleIngredient(food.first())) // overdue never silently reclassifies food
    }
    @Test fun rescueOnlyUsesEligibleItemsNeedingAttention() {
        assertEquals(setOf("demo-avocado", "demo-spinach"), rescueItems(food, today).map { it.id }.toSet())
        val recipes = recommendRecipes(DemoRecipes, food, true, today)
        assertFalse(recipes.any { it.id == "demo-soup" })
        assertTrue(recipes.any { it.id == "demo-toast" })
        assertTrue(recommendRecipes(DemoRecipes, emptyList(), true, today).isEmpty())
        assertTrue(matchIngredients(DemoRecipes.first(), emptyList()).none { it.available })
    }
    @Test fun searchCategoryAndDietaryFiltersCombine() {
        assertEquals(listOf("demo-toast"), filterRecipes(DemoRecipes, "avocado", "All", "Vegan").map { it.id })
        assertTrue(filterRecipes(DemoRecipes, "", "Dinner", "Vegan").all { it.category == "Dinner" })
        assertTrue(filterRecipes(DemoRecipes, "", "All", "Vegan").none { it.id == "demo-snack" })
        assertTrue(filterRecipes(DemoRecipes, "not-present", "All", "Any").isEmpty())
        assertEquals(6, filterRecipes(DemoRecipes, "", "All", "Vegetarian").size)
    }
    @Test fun matchingIsDeterministicAndDoesNotUseConfidence() {
        val changed = food.map { item -> item.copy(scans = item.scans.map { it.copy(confidence = .01f) }) }
        assertEquals(recommendRecipes(DemoRecipes, food, false, today),
            recommendRecipes(DemoRecipes, changed, false, today))
        assertEquals("avocado", ingredientKey(" Avocados "))
    }
}
