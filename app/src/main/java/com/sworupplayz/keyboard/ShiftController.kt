package com.sworupplayz.keyboard

enum class ShiftLockState {
    OFF,
    ONE_SHOT,
    CAPS_LOCK
}

/**
 * Gboard-like shift: one tap capitalizes the next English letter, two quick
 * taps lock caps. Nepali never capitalizes. Roman may use one-shot only.
 */
object ShiftPolicy {
    const val DOUBLE_TAP_MS = 400L

    fun tap(
        current: ShiftLockState,
        now: Long,
        lastTapAt: Long,
        language: KeyboardLanguage
    ): ShiftLockState {
        if (language == KeyboardLanguage.NEPALI) return ShiftLockState.OFF
        if (language == KeyboardLanguage.ROMAN) {
            return if (current == ShiftLockState.OFF) ShiftLockState.ONE_SHOT else ShiftLockState.OFF
        }
        if (current == ShiftLockState.CAPS_LOCK) return ShiftLockState.OFF
        if (current == ShiftLockState.ONE_SHOT && now - lastTapAt in 0..DOUBLE_TAP_MS) {
            return ShiftLockState.CAPS_LOCK
        }
        return if (current == ShiftLockState.OFF) ShiftLockState.ONE_SHOT else ShiftLockState.OFF
    }

    fun afterLetter(current: ShiftLockState): ShiftLockState =
        if (current == ShiftLockState.ONE_SHOT) ShiftLockState.OFF else current

    fun afterNonLetter(current: ShiftLockState): ShiftLockState = current

    fun consumesOneShot(text: String): Boolean =
        text.isNotEmpty() && text.first().isLetter()

    fun isActive(state: ShiftLockState): Boolean = state != ShiftLockState.OFF

    fun lettersUppercase(state: ShiftLockState): Boolean = state != ShiftLockState.OFF

    fun isCapsLock(state: ShiftLockState): Boolean = state == ShiftLockState.CAPS_LOCK

    fun allowsAutoCapitalization(state: ShiftLockState, language: KeyboardLanguage): Boolean =
        language == KeyboardLanguage.ENGLISH && state == ShiftLockState.OFF

    fun label(state: ShiftLockState): String =
        if (state == ShiftLockState.CAPS_LOCK) "⇪" else "⇧"
}
