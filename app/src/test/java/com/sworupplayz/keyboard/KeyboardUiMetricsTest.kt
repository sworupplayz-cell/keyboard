package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardUiMetricsTest {
    @Test
    fun keyHeightsStayComfortableAcrossCommonPhoneSizes() {
        assertEquals(46, KeyboardUiMetrics.keyHeightDp(320, 568, landscape = false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, landscape = false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(412, 915, landscape = false))
        assertEquals(42, KeyboardUiMetrics.keyHeightDp(640, 360, landscape = true))
    }

    @Test
    fun denseLayoutsRemainWithinReasonablePortraitHeight() {
        val smallNepali = KeyboardUiMetrics.estimatedStandardHeightDp(
            320, 568, landscape = false, rowCount = 5, hasSuggestion = false
        )
        val smallRoman = KeyboardUiMetrics.estimatedStandardHeightDp(
            320, 568, landscape = false, rowCount = 4, hasSuggestion = true
        )
        val commonNepali = KeyboardUiMetrics.estimatedStandardHeightDp(
            360, 800, landscape = false, rowCount = 5, hasSuggestion = false
        )
        val smallNepaliWithSuggestions = KeyboardUiMetrics.estimatedStandardHeightDp(
            320,
            568,
            landscape = false,
            rowCount = 5,
            hasSuggestion = true,
            includeNavigation = false
        )

        assertTrue(smallNepali <= 280)
        assertTrue(smallRoman <= 275)
        assertTrue(commonNepali <= 290)
        assertTrue(smallNepaliWithSuggestions <= 280)
    }

    @Test
    fun tenKeyRowsDoNotCollapseOnSmallScreens() {
        assertTrue(KeyboardUiMetrics.equalKeyWidthDp(320, 10) >= 29f)
        assertTrue(KeyboardUiMetrics.equalKeyWidthDp(360, 10) >= 30f)
    }

    @Test
    fun handwritingCanvasAdaptsWithoutBecomingTiny() {
        assertEquals(140, KeyboardUiMetrics.handwritingCanvasHeightDp(568, landscape = false))
        assertEquals(160, KeyboardUiMetrics.handwritingCanvasHeightDp(800, landscape = false))
        assertEquals(104, KeyboardUiMetrics.handwritingCanvasHeightDp(360, landscape = true))
    }

    @Test
    fun primaryNavigationUsesReadableModeLabels() {
        val labels = KeyboardLayouts.navigationControls().map { it.label }
        assertEquals(listOf("EN", "नेपाली", "Roman", "123", "😊", "✍"), labels)
        assertEquals("#+=", KeyboardLayouts.navigationControls("#+=")[3].label)
    }
}
