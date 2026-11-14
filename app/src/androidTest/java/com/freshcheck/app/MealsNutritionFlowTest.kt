package com.freshcheck.app
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.freshcheck.app.ui.nutrition.NutritionScreen
import com.freshcheck.app.ui.insights.InsightsScreen
import com.freshcheck.app.ui.myfood.sampleFood
import com.freshcheck.app.ui.theme.FreshCheckTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MealsNutritionFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nutritionServingSelectionUpdatesLabelledBasis() {
        compose.setContent { FreshCheckTheme { Surface { NutritionScreen(onBack = {}) } } }
        compose.onNodeWithText("200 g").performScrollTo().performClick()
        compose.onNodeWithText("Serving basis · 200 g").assertExists()
        compose.onNodeWithText("Energy per 200 g · synthetic sample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DEMONSTRATION VALUES ONLY").assertExists()
    }
    @Test fun recipeSearchDetailsServingsAndPendingActionsWork() {
        compose.setContent { FreshCheckTheme { Surface {
            var topic by remember { mutableStateOf("Meals") }
            InsightsScreen(topic, { topic = it }, sampleFood(LocalDate.now()))
        } } }
        compose.onNodeWithText("Search recipes or ingredients").performScrollTo().performTextInput("avocado")
        compose.onNodeWithText("View Avocado garden toast").performScrollTo().performClick()
        compose.onNodeWithText("+ Serving").performScrollTo().performClick()
        compose.onNodeWithText("Servings · 3").assertExists()
        compose.onNodeWithText("Save Recipe · integration pending").performScrollTo().performClick()
        compose.onNodeWithText("Recipe persistence integration pending. No recipe was saved.").assertExists()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("← Meal recommendations").performScrollTo().performClick()
        compose.onNodeWithText("Search recipes or ingredients").assertExists()
    }
    @Test fun rescueWithNoInventoryHasHonestEmptyState() {
        compose.setContent { FreshCheckTheme { Surface {
            InsightsScreen("Rescue", {}, emptyList())
        } } }
        compose.onNodeWithText("Nothing eligible needs attention").performScrollTo().assertIsDisplayed()
    }
}
