package com.freshcheck.app
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.freshcheck.app.navigation.FreshCheckApp
import com.freshcheck.app.ui.history.HistoryFlow
import com.freshcheck.app.ui.insights.sampleHistory
import com.freshcheck.app.ui.theme.FreshCheckTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PhaseFiveUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun profileSettingsThemeAboutAndPrivacyAreReachable() {
        compose.setContent { FreshCheckApp() }
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Dark mode").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Clear local history").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("About FreshCheck").performScrollTo().performClick()
        compose.onNodeWithText("EC5208 · Mobile Application Development\nUniversity project · 3 team members", substring = true).assertExists()
        compose.onNodeWithText("← Settings").performScrollTo().performClick()
        compose.onNodeWithText("Privacy and on-device AI").performScrollTo().performClick()
        compose.onNodeWithText("Privacy & responsible AI").assertExists()
    }
    @Test fun historySearchDetailsAndDeletionCapabilityAreHonest() {
        compose.setContent { FreshCheckTheme { Surface {
            HistoryFlow(sampleHistory(LocalDate.now()), {}, {})
        } } }
        compose.onNodeWithText("Search produce name").performScrollTo().performTextInput("avocado")
        compose.onNodeWithText("View scan · Avocado").performScrollTo().performClick()
        compose.onNodeWithText("DEMONSTRATION · NO REAL MODEL OUTPUT").assertExists()
        compose.onNodeWithText("Delete scan").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("← Scan History").performScrollTo().performClick()
        compose.onNodeWithText("Search produce name").performScrollTo().performTextReplacement("no-such-produce")
        compose.onNodeWithText("No matching scans").performScrollTo().assertIsDisplayed()
    }
}
