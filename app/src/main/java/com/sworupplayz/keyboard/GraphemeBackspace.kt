package com.sworupplayz.keyboard

import java.text.BreakIterator
import java.util.Locale

/**
 * Deletes one visible grapheme: Devanagari clusters, combining marks,
 * emoji ZWJ sequences, flags, and skin-tone modifiers.
 */
object GraphemeBackspace {
    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
    private val DEVANAGARI_MARKS = setOf(
        0x093A, 0x093B, 0x093C, 0x093E, 0x093F, 0x0940, 0x0941, 0x0942, 0x0943,
        0x0944, 0x0945, 0x0946, 0x0947, 0x0948, 0x0949, 0x094A, 0x094B, 0x094C,
        0x094D, 0x094E, 0x094F, 0x0951, 0x0952, 0x0953, 0x0954, 0x0955, 0x0956,
        0x0957, 0x0962, 0x0963
    )

    fun codeUnitsToDelete(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 0
        specialSequenceUnits(textBeforeCursor)?.let { return it }
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(textBeforeCursor)
        val end = iterator.last()
        val start = iterator.previous()
        return if (start == BreakIterator.DONE) lastCodePointUnits(textBeforeCursor) else (end - start).coerceAtLeast(1)
    }

    fun apply(text: String): String {
        val count = codeUnitsToDelete(text)
        return if (count <= 0) text else text.substring(0, text.length - count)
    }

    private fun specialSequenceUnits(text: String): Int? {
        val points = ArrayList<Int>()
        var offset = 0
        while (offset < text.length) {
            val code = text.codePointAt(offset)
            points += code
            offset += Character.charCount(code)
        }
        if (points.isEmpty()) return null
        var index = points.lastIndex
        var consumed = 0

        fun take(): Boolean {
            if (index < 0) return false
            consumed += Character.charCount(points[index])
            index--
            return true
        }

        // Variation selector or skin tone rides with the previous emoji.
        val attachedModifier = points[index] == VS15 || points[index] == VS16 || points[index] in SKIN_TONES
        if (attachedModifier) take()
        if (index >= 0 && points[index] in REGIONAL_INDICATORS) {
            take()
            if (index >= 0 && points[index] in REGIONAL_INDICATORS) take()
            return consumed.takeIf { it > 0 }
        }
        if (attachedModifier && index >= 0) {
            take()
            return consumed.takeIf { it > 0 }
        }
        if (index < 0) return consumed.takeIf { it > 0 }

        // Walk back ZWJ-joined emoji.
        var sawZwj = false
        while (index >= 0) {
            val current = points[index]
            if (current == ZWJ) {
                sawZwj = true
                take()
                continue
            }
            if (current == VS15 || current == VS16 || current in SKIN_TONES) {
                take()
                continue
            }
            take()
            if (index >= 0 && points[index] == ZWJ) {
                sawZwj = true
                continue
            }
            break
        }
        return if (sawZwj && consumed > 0) consumed else null
    }

    private fun lastCodePointUnits(text: String): Int {
        val start = text.offsetByCodePoints(text.length, -1)
        var extra = 0
        if (start > 0) {
            val previous = text.codePointBefore(start)
            if (previous in DEVANAGARI_MARKS || previous == 0x094D) {
                extra = start - text.offsetByCodePoints(start, -1)
            }
        }
        return text.length - start + extra
    }
}
