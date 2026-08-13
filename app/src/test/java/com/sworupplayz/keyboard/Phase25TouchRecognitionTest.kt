package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase25TouchRecognitionTest {
    private val left = TouchCandidate("q", TouchRect(0f, 0f, 80f, 80f), KeyEdge.START, 0)
    private val mid = TouchCandidate("w", TouchRect(80f, 0f, 160f, 80f), KeyEdge.MIDDLE, 0)
    private val right = TouchCandidate("e", TouchRect(160f, 0f, 240f, 80f), KeyEdge.END, 0)
    private val row = listOf(left, mid, right)

    @Test
    fun centerHitStaysOnTheVisibleKey() {
        assertEquals("w", TouchGeometryPolicy.resolve(row, 120f, 40f))
        assertTrue(TouchGeometryPolicy.isCenterHit(mid.rect, 120f, 40f))
        assertFalse(TouchGeometryPolicy.isEdgeHit(mid.rect, 120f, 40f))
    }

    @Test
    fun edgeHitStillResolvesToTheCloserKey() {
        assertTrue(TouchGeometryPolicy.isEdgeHit(mid.rect, 84f, 40f))
        assertEquals("w", TouchGeometryPolicy.resolve(row, 84f, 40f, preferredId = "w"))
        assertEquals("q", TouchGeometryPolicy.resolve(row, 8f, 40f))
    }

    @Test
    fun expandedLogicalHitboxIsLargerThanTheDrawnKey() {
        val grown = TouchGeometryPolicy.expanded(mid.rect, KeyEdge.MIDDLE, 360, false)
        assertTrue(grown.left < mid.rect.left)
        assertTrue(grown.right > mid.rect.right)
        assertTrue(grown.contains(78f, 40f))
        assertTrue(mid.rect.contains(120f, 40f))
    }

    @Test
    fun centerLockBeatsANeighborExpandedEdge() {
        assertEquals("w", TouchGeometryPolicy.resolve(row, 120f, 40f, preferredId = "q"))
        assertNotEquals("e", TouchGeometryPolicy.resolve(row, 100f, 40f, preferredId = "w"))
    }

    @Test
    fun existingStayOnKeyContractStillHolds() {
        assertTrue(KeyTouchPolicy.staysOnKey(20f, 20f, 24f, 22f, 80, 80))
        assertFalse(KeyTouchPolicy.staysOnKey(10f, 10f, 200f, 10f, 80, 80))
        assertTrue(TouchRecognition.shouldKeepCurrentKey(20f, 20f, 24f, 22f, 80, 80))
        assertTrue(KeyTouchPolicy.shouldCancelLongPress(10f, 10f, 200f, 10f, 80, 80))
    }

    @Test
    fun trajectoryClassifiesTapDriftSlideAndCancel() {
        val path = TouchTrajectory()
        path.start(20f, 20f, 1_000L, 0)
        path.move(22f, 21f, 1_030L)
        assertEquals(TouchGesture.TAP, path.classify(80, 80))
        path.move(36f, 22f, 1_060L)
        assertEquals(TouchGesture.DRIFT, path.classify(80, 80))
        path.move(70f, 20f, 1_090L)
        assertEquals(TouchGesture.SLIDE, path.classify(80, 80))
        assertEquals(TouchDirection.RIGHT, path.direction())
        assertTrue(path.distance() > 40f)
        assertTrue(path.velocity() > 0f)
        path.move(400f, 20f, 1_120L)
        assertEquals(TouchGesture.CANCEL, path.classify(80, 80))
    }

    @Test
    fun longPressRequiresAStableHold() {
        val path = TouchTrajectory()
        path.start(20f, 20f, 1_000L)
        path.move(22f, 21f, 1_430L)
        assertEquals(TouchGesture.LONG_PRESS, path.classify(80, 80))
        assertTrue(TouchRecognition.shouldOpenAlternates(path, stillOnKey = true, now = 1_430L))
        assertFalse(TouchRecognition.shouldOpenAlternates(path, stillOnKey = true, now = 1_200L))
        assertFalse(TouchRecognition.shouldOpenAlternates(path, stillOnKey = false, now = 1_500L))
        assertTrue(KeyTouchPolicy.shouldCancelLongPress(20f, 20f, 200f, 20f, 80, 80))
    }

    @Test
    fun slideCorrectionOnlyFromABoundaryTowardANeighbor() {
        assertTrue(TouchRecognition.shouldSlideCorrect(true, TouchGesture.SLIDE, "q", "w"))
        assertFalse(TouchRecognition.shouldSlideCorrect(false, TouchGesture.SLIDE, "q", "w"))
        assertFalse(TouchRecognition.shouldSlideCorrect(true, TouchGesture.TAP, "q", "w"))
        assertFalse(TouchRecognition.shouldSlideCorrect(true, TouchGesture.SLIDE, "q", "q"))
        assertFalse(TouchRecognition.shouldSlideCorrect(true, TouchGesture.SLIDE, "q", null))
    }

    @Test
    fun rapidTypingDebounceStillUsesTheExistingBounceWindow() {
        assertEquals(32L, KeyTouchPolicy.KEY_BOUNCE_MS)
        assertEquals(TouchCalibration.DEBOUNCE_MS, KeyTouchPolicy.KEY_BOUNCE_MS)
        assertTrue(KeyTouchPolicy.shouldAcceptTap("a", 0L, "a", 40L))
        assertFalse(KeyTouchPolicy.shouldAcceptTap("a", 100L, "a", 120L))
        assertTrue(KeyTouchPolicy.shouldAcceptTap("a", 100L, "b", 110L))
        val guard = ActivationGuard(KeyTouchPolicy.KEY_BOUNCE_MS)
        assertTrue(guard.allow("text:a", 1_000L))
        assertFalse(guard.allow("text:a", 1_020L))
        assertTrue(guard.allow("text:b", 1_025L))
    }

    @Test
    fun multiTouchKeepsOneOwnerAndIgnoresASecondFinger() {
        val state = TouchRecognitionState()
        assertTrue(state.tryAcquire(1, "a", 10f, 10f, 1_000L))
        assertTrue(state.isOwner(1))
        assertFalse(state.tryAcquire(2, "s", 40f, 10f, 1_010L))
        assertEquals("a", state.ownerId)
        state.release(2)
        assertTrue(state.isOwner(1))
        state.release(1)
        assertNull(state.ownerId)
        assertTrue(state.tryAcquire(2, "s", 40f, 10f, 1_040L))
    }

    @Test
    fun narrowAndOneHandedLayoutsExpandMoreTowardTheOuterEdge() {
        val phone = TouchGeometryPolicy.expansion(80f, KeyEdge.MIDDLE, 412, false)
        val narrow = TouchGeometryPolicy.expansion(80f, KeyEdge.MIDDLE, 320, false)
        val oneHanded = TouchGeometryPolicy.expansion(80f, KeyEdge.MIDDLE, 412, true)
        assertTrue(narrow.first > phone.first)
        assertTrue(oneHanded.first > phone.first)
        val start = TouchGeometryPolicy.expansion(80f, KeyEdge.START, 360, false)
        assertTrue(start.first >= start.second)
        val left = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.LEFT)
        val center = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.CENTER)
        val right = OneHandedLayoutPolicy.insets(412, OneHandedAlignment.RIGHT)
        assertEquals(0, left.startDp)
        assertTrue(center.startDp > 0 && center.endDp > 0)
        assertEquals(0, right.endDp)
        assertEquals(0, OneHandedLayoutPolicy.insets(320, OneHandedAlignment.LEFT).endDp)
        assertEquals(46, KeyboardUiMetrics.keyHeightDp(320, 568, false))
        assertEquals(48, KeyboardUiMetrics.keyHeightDp(360, 800, false))
        assertEquals(42, KeyboardUiMetrics.keyHeightDp(640, 360, true))
    }

    @Test
    fun adaptiveWeightsStayBoundedAndNeverStoreSecrets() {
        val model = AdaptiveHitboxPolicy(limit = 2)
        assertTrue(model.record("q", "w"))
        assertTrue(model.record("q", "w"))
        assertTrue(model.weight("q", "w") > 0f)
        assertTrue(model.weight("q", "w") <= TouchCalibration.ADAPTIVE_WEIGHT)
        assertFalse(model.record("https://evil", "w"))
        assertFalse(model.record("user@example.com", "q"))
        assertFalse(model.record("4111111111111111", "q"))
        model.record("a", "s")
        model.record("z", "x")
        assertEquals(2, model.size())
        val restored = AdaptiveHitboxPolicy.fromSerialized(model.serialize())
        assertEquals(model.size(), restored.size())
        assertTrue(restored.weight("z", "x") > 0f)
    }

    @Test
    fun accessibilityAndVisualContractsStayOnTheExistingKeys() {
        assertEquals("Space, Nepali", AccessibilityLabels.space(KeyboardLanguage.NEPALI))
        assertEquals("Space", AccessibilityLabels.key(KeySpec("English", KeyAction.SPACE, " ")))
        assertEquals("Backspace", AccessibilityLabels.key(KeySpec("⌫", KeyAction.BACKSPACE)))
        assertEquals(40, AccessibilityLabels.MIN_TOUCH_DP)
        assertEquals(0, KeyTouchPolicy.horizontalInsets(4, KeyEdge.START).first)
        assertEquals(0, KeyTouchPolicy.horizontalInsets(4, KeyEdge.END).second)
        assertTrue(KeyTouchPolicy.commitsOnDown(KeyAction.BACKSPACE))
        assertFalse(KeyTouchPolicy.commitsOnDown(KeyAction.TEXT))
        assertEquals(TouchCalibration.LONG_PRESS_MS, KeyTouchPolicy.LONG_PRESS_MS)
        assertEquals(
            KeyboardTheme.pressedColor(0xFFFFFFFF.toInt()),
            KeyboardThemeTokens.pressed(0xFFFFFFFF.toInt())
        )
    }

    @Test
    fun hitTestingIsDeterministicAndThemeIndependent() {
        val first = TouchGeometryPolicy.resolve(row, 118f, 41f)
        val second = TouchGeometryPolicy.resolve(row, 118f, 41f)
        assertEquals(first, second)
        assertEquals("w", first)
        assertEquals("q", TouchRecognition.keyId(KeySpec("q")))
        assertEquals("SHIFT", TouchRecognition.keyId(KeySpec("⇧", KeyAction.SHIFT)))
    }
}
