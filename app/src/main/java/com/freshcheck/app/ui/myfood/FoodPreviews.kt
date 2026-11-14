package com.freshcheck.app.ui.myfood
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.theme.FreshCheckTheme
import java.time.LocalDate

private val previewActions = FoodActions({}, {}, {}, {}, { _, _ -> })
@Preview(name = "Sample food details · 320dp", widthDp = 320, heightDp = 760)
@Composable
private fun DetailsPreview() { FreshCheckTheme { Surface {
    FoodDetailsScreen(sampleFood(LocalDate.now()).first(), previewActions, {})
} } }
@Preview(name = "Sample journey · dark", widthDp = 360, heightDp = 900)
@Composable
private fun JourneyPreview() { FreshCheckTheme(darkTheme = true) { Surface {
    FreshnessJourneyScreen(sampleFood(LocalDate.now()).first(), {}, {})
} } }
@Preview(name = "Sample edit · 320dp", widthDp = 320, heightDp = 760)
@Composable
private fun EditPreview() { FreshCheckTheme { Surface {
    EditFoodItemScreen(sampleFood(LocalDate.now()).first(), {}, {})
} } }
@Preview(name = "No stored food", widthDp = 320, heightDp = 760)
@Composable
private fun EmptyInventoryPreview() { FreshCheckTheme { Surface {
    MyFoodScreen(emptyList(), previewActions, {}, {}, {})
} } }
