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
    fun heightChoicesAreBoundedOrderedAndApplyAcrossPanels() {
        val small = KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.SMALL)
        val normal = KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.NORMAL)
        val large = KeyboardUiMetrics.keyHeightDp(360, 800, false, KeyboardHeight.LARGE)

        assertEquals(44, small)
        assertEquals(48, normal)
        assertEquals(52, large)
        assertTrue(small < normal && normal < large)
        assertEquals(34, KeyboardUiMetrics.compactNumberRowHeightDp(KeyboardHeight.SMALL))
        assertEquals(38, KeyboardUiMetrics.compactNumberRowHeightDp(KeyboardHeight.LARGE))
        assertEquals(144, KeyboardUiMetrics.handwritingCanvasHeightDp(800, false, KeyboardHeight.SMALL))
        assertEquals(176, KeyboardUiMetrics.handwritingCanvasHeightDp(800, false, KeyboardHeight.LARGE))
        assertTrue(
            KeyboardUiMetrics.estimatedStandardHeightDp(
                320,
                568,
                landscape = false,
                rowCount = 5,
                hasSuggestion = true,
                includeNavigation = false,
                height = KeyboardHeight.LARGE,
                hasNumberRow = true
            ) <= 340
        )
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
    fun toolbarDoesNotDominateTheKeyboardHeight() {
        assertTrue(KeyboardUiMetrics.toolbarHeightDp(320, false) <= 32)
        assertTrue(KeyboardUiMetrics.toolbarHeightDp(360, false) <= 34)
        val withToolbar = KeyboardUiMetrics.estimatedStandardHeightDp(
            360, 800, landscape = false, rowCount = 4, hasSuggestion = true, hasToolbar = true
        )
        val withoutToolbar = KeyboardUiMetrics.estimatedStandardHeightDp(
            360, 800, landscape = false, rowCount = 4, hasSuggestion = true, hasToolbar = false
        )
        assertTrue(withToolbar > withoutToolbar)
        assertTrue(withToolbar - withoutToolbar <= 36)
        assertEquals(4, KeyboardUiMetrics.maxToolbarItems(320))
        assertEquals(5, KeyboardUiMetrics.maxToolbarItems(360))
        assertEquals(6, KeyboardUiMetrics.maxToolbarItems(412))
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
