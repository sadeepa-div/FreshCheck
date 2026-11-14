package com.freshcheck.app
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.theme.FreshCheckTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class InventoryFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedItemJourneyEditAndDeleteWorkLocally() {
        compose.setContent { FreshCheckTheme { Surface {
            MyFoodFlow(remember { DemoInventory() }, {})
        } } }
        compose.onAllNodesWithText("Explore sample inventory")[0].performScrollTo().performClick()
        compose.onNodeWithText("Search produce name or type").performScrollTo().performTextInput("Avocado")
        compose.onNodeWithText("Avocado").performScrollTo().performClick()
        compose.onNodeWithText("View Full Freshness Journey").performScrollTo().performClick()
        compose.onNodeWithText("Avocado's journey").assertExists()
        compose.onNodeWithText("Two recorded moments").performScrollTo().assertExists()
        compose.onNodeWithText("← Food Details").performScrollTo().performClick()
        compose.onNodeWithText("Edit Item").performScrollTo().performClick()
        compose.onNodeWithText("Quantity").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Apply sample changes").performScrollTo().performClick()
        compose.onNodeWithText("Enter a quantity greater than 0, up to 100,000.").assertExists()
        compose.onNodeWithText("Quantity").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Apply sample changes").performScrollTo().performClick()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Delete Item").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete Item").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Delete").performClick()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("No matches").assertExists()
    }
}
