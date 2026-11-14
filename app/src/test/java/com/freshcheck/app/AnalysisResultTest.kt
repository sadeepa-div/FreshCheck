package com.freshcheck.app

import com.freshcheck.app.ui.result.*
import org.junit.Assert.*
import org.junit.Test

class AnalysisResultTest {
    @Test fun demoFixturesAreExplicitAndDoNotInventDates() {
        FreshnessCondition.entries.forEach {
            val result = DemoResults.create(it)
            assertTrue(result.isDemo)
            assertEquals(it, result.condition)
            assertNull(result.analyzedAtLabel)
            assertNull(result.imageUri)
        }
    }
    @Test fun unsupportedAndSpoiledInputsHaveNoStorageAdvice() {
        assertNull(DemoResults.create(FreshnessCondition.Uncertain).guidance)
        assertNull(DemoResults.create(FreshnessCondition.Uncertain).confidence)
        assertNull(DemoResults.create(FreshnessCondition.VisiblySpoiled).guidance)
    }
    @Test fun confidenceRejectsInvalidModelValues() {
        listOf(-.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                AnalysisResult("Produce", ProduceCategory.Unknown, FreshnessCondition.Uncertain, value)
            }
        }
    }
    @Test fun visualLabelsStayDistinctFromUncertainState() {
        assertEquals(listOf("Fresh", "Use Soon", "Visibly Spoiled", "Uncertain / Unable to Assess"),
            FreshnessCondition.entries.map { it.label })
    }
}
