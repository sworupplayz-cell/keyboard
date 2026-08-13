package com.sworupplayz.keyboard

enum class FeedbackKind {
    KEY,
    CHROME,
    LONG_PRESS,
    NAVIGATION
}

enum class KeyClickSound {
    STANDARD,
    DELETE,
    RETURN,
    SPACE,
    NONE
}

/**
 * Sound and vibration rules. Disabled means completely off. Toolbar chrome and
 * panel navigation never play a keyboard-click or spam haptics.
 */
object TouchFeedbackPolicy {
    const val KEY_VIBRATION_MS = 18L

    fun shouldPlaySound(soundEnabled: Boolean, kind: FeedbackKind): Boolean =
        soundEnabled && kind == FeedbackKind.KEY

    fun shouldVibrate(vibrationEnabled: Boolean, kind: FeedbackKind): Boolean =
        vibrationEnabled && (kind == FeedbackKind.KEY || kind == FeedbackKind.LONG_PRESS)

    fun vibrationDurationMs(kind: FeedbackKind): Long =
        if (shouldVibrate(true, kind)) KEY_VIBRATION_MS else 0L

    fun soundFor(action: KeyAction): KeyClickSound = when (action) {
        KeyAction.BACKSPACE -> KeyClickSound.DELETE
        KeyAction.ENTER -> KeyClickSound.RETURN
        KeyAction.SPACE -> KeyClickSound.SPACE
        KeyAction.SETTINGS,
        KeyAction.TOOLBAR_MORE,
        KeyAction.TOOLBAR_COLLAPSE,
        KeyAction.RETURN_TO_PREVIOUS,
        KeyAction.LETTERS -> KeyClickSound.NONE
        else -> KeyClickSound.STANDARD
    }
}
