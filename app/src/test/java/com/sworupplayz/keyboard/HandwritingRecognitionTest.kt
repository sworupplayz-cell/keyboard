package com.sworupplayz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingRecognitionTest {
    private val stroke = listOf(
        InkPoint(0.1f, 0.2f),
        InkPoint(0.5f, 0.6f),
        InkPoint(0.8f, 0.3f)
    )

    @Test
    fun strokeStateSupportsUndoClearAndCancel() {
        val state = HandwritingInputState(FakeRecognizer(listOf("क")))
        assertFalse(state.addStroke(listOf(InkPoint(0f, 0f))))
        assertTrue(state.addStroke(stroke))
        assertTrue(state.addStroke(stroke.reversed()))
        assertEquals(2, state.strokeCount)

        assertTrue(state.undo())
        assertEquals(1, state.strokeCount)
        state.clear()
        assertEquals(0, state.strokeCount)
        assertEquals(HandwritingStatus.EMPTY, state.status)

        state.addStroke(stroke)
        state.cancel()
        assertEquals(0, state.strokeCount)
        assertEquals(HandwritingStatus.EMPTY, state.status)
    }

    @Test
    fun recognitionKeepsUpToThreeDistinctNepaliCandidates() {
        val state = HandwritingInputState(
            FakeRecognizer(listOf("क", "ख", "क", " latin ", " घर ", "ग"))
        )
        state.addStroke(stroke)

        val result = state.recognize()

        assertEquals(HandwritingStatus.RESULTS, result.status)
        assertEquals(listOf("क", "ख", "घर"), result.candidates)
    }

    @Test
    fun confirmReturnsUnicodeForExternalFieldInsertionAndClearsInk() {
        val state = HandwritingInputState(FakeRecognizer(listOf("नेपाल")))
        val externalField = StringBuilder("मेरो ")
        state.addStroke(stroke)
        state.recognize()

        state.confirm()?.let(externalField::append)

        assertEquals("मेरो नेपाल", externalField.toString())
        assertEquals(0, state.strokeCount)
        assertEquals(HandwritingStatus.EMPTY, state.status)
        assertNull(state.confirm())
    }

    @Test
    fun unavailableRecognizerReportsLimitationWithoutChangingText() {
        val state = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
        val externalField = StringBuilder("safe")
        state.addStroke(stroke)

        val result = state.recognize()
        state.confirm()?.let(externalField::append)

        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, result.status)
        assertTrue(result.candidates.isEmpty())
        assertEquals("safe", externalField.toString())
    }

    @Test
    fun emptyInkDoesNotInvokeRecognizerOrProduceText() {
        val recognizer = FakeRecognizer(listOf("क"))
        val state = HandwritingInputState(recognizer)

        assertEquals(HandwritingStatus.EMPTY, state.recognize().status)
        assertEquals(0, recognizer.callCount)
        assertNull(state.confirm())
    }

    private class FakeRecognizer(
        private val results: List<String>
    ) : NepaliHandwritingRecognizer {
        override val isAvailable: Boolean = true
        var callCount: Int = 0
            private set

        override fun recognize(ink: HandwritingInk): List<String> {
            callCount++
            return results
        }
    }
}
