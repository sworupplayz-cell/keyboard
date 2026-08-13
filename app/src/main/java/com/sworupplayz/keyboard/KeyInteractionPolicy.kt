package com.sworupplayz.keyboard

/**
 * Pure press / preview / long-press rules. KeyboardService only asks whether a
 * key should preview or offer alternates; it does not animate the whole board.
 */
object KeyInteractionPolicy {
    const val PRESS_FADE_MS = 50
    const val RELEASE_FADE_MS = 70
    const val DOUBLE_TAP_GUARD_MS = 220L

    fun showsPreview(key: KeySpec): Boolean {
        if (!KeyVisuals.showsPreview(key)) return false
        return !looksLikeEmoji(key.label) && !looksLikeEmoji(key.output)
    }

    fun previewLabel(key: KeySpec): String = key.output.ifEmpty { key.label }

    fun previewTextSizeSp(label: String, compactScreen: Boolean): Float {
        val units = Character.codePointCount(label, 0, label.length)
        return when {
            label.length >= 3 || units > 1 -> if (compactScreen) 18f else 20f
            else -> if (compactScreen) 20f else 22f
        }
    }

    fun allowsLongPressAlternates(key: KeySpec): Boolean =
        key.action == KeyAction.TEXT && KeyVisuals.alternates(key.label).isNotEmpty()

    fun looksLikeEmoji(value: String): Boolean {
        if (value.isEmpty()) return false
        var index = 0
        while (index < value.length) {
            val code = value.codePointAt(index)
            if (isEmojiCodePoint(code)) return true
            index += Character.charCount(code)
        }
        return false
    }

    private fun isEmojiCodePoint(code: Int): Boolean =
        code in 0x1F300..0x1FAFF ||
            code in 0x1F1E6..0x1F1FF ||
            code in 0x2600..0x27BF ||
            code in 0xFE00..0xFE0F ||
            code == 0x200D
}

/** Ignores a second tap on the same control within a short window. */
class ActivationGuard(
    private val intervalMs: Long = KeyInteractionPolicy.DOUBLE_TAP_GUARD_MS
) {
    private var lastToken: String = ""
    private var lastAt: Long = 0L

    fun allow(token: String, now: Long): Boolean {
        if (token == lastToken && now - lastAt in 0 until intervalMs) return false
        lastToken = token
        lastAt = now
        return true
    }

    fun reset() {
        lastToken = ""
        lastAt = 0L
    }
}
