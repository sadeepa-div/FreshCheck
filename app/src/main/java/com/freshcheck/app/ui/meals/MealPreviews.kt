package com.freshcheck.app.ui.meals
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.myfood.sampleFood
import com.freshcheck.app.ui.nutrition.NutritionScreen
import com.freshcheck.app.ui.theme.FreshCheckTheme
import java.time.LocalDate

@Preview(name = "Synthetic nutrition · small phone", widthDp = 320, heightDp = 800)
@Composable
private fun NutritionPreview() { FreshCheckTheme { Surface { NutritionScreen(onBack = {}) } } }
@Preview(name = "Sample meals", widthDp = 360, heightDp = 800)
@Composable
private fun MealsPreview() { FreshCheckTheme { Surface {
    MealSuggestionsScreen(sampleFood(LocalDate.now()), {}, {}, {})
} } }
@Preview(name = "Sample rescue · dark", widthDp = 320, heightDp = 900)
@Composable
private fun RescuePreview() { FreshCheckTheme(darkTheme = true) { Surface {
    FoodRescueScreen(sampleFood(LocalDate.now()), {}, {}, {})
} } }
@Preview(name = "Sample recipe details", widthDp = 320, heightDp = 900)
@Composable
private fun RecipePreview() { FreshCheckTheme { Surface {
    RecipeDetailsScreen(DemoRecipes.first(), sampleFood(LocalDate.now()), RecipeActions({}, {}, {}))
} } }
