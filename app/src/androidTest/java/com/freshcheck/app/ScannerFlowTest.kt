package com.freshcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.freshcheck.app.navigation.FreshCheckApp
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.ScanMode
import com.freshcheck.app.ui.theme.FreshCheckTheme
import org.junit.Rule
import org.junit.Test

class ScannerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun demoCanChangeModesReachResultsAndScanAgain() {
        compose.setContent { FreshCheckTheme { FreshCheckApp() } }
        compose.onNodeWithText("Scan", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Camera integration pending").assertExists()
        compose.onNodeWithText("After Buying").performClick()
        compose.onNodeWithText("Use Soon").performScrollTo().performClick()
        compose.onNodeWithText("Run demo analysis").performScrollTo().performClick()
        compose.waitUntil(8000) {
            compose.onAllNodesWithText("DEMONSTRATION · SAMPLE DATA").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("DEMONSTRATION · SAMPLE DATA").assertExists()
        compose.onNodeWithText("Save to My Food · pending").performScrollTo().performClick()
        compose.onNodeWithText("Inventory integration pending. This sample has not been saved.").assertExists()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Scan Again").performScrollTo().performClick()
        compose.onNodeWithText("Camera integration pending").assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("FRESHCHECK  /  YOUR DAILY HARVEST").assertExists()
    }

    @Test fun allResultStatesHaveExplicitLabelsAndSafetyNotice() {
        val condition = androidx.compose.runtime.mutableStateOf(FreshnessCondition.Fresh)
        compose.setContent {
            FreshCheckTheme {
                AnalysisResultScreen(DemoResults.create(condition.value), ScanMode.AfterBuying,
                    ResultActions({}, {}, {}, {}, {}))
            }
        }
        FreshnessCondition.entries.forEach {
            compose.runOnIdle { condition.value = it }
            compose.onNodeWithText("${it.symbol}  ${it.label}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(VisualAssessmentNotice).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("DEMONSTRATION · SAMPLE DATA").assertExists()
        }
    }
}
