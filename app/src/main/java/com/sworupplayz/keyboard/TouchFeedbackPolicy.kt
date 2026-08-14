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

    fun vibrationDurationMs(
        kind: FeedbackKind,
        strength: HapticStrength = HapticStrength.MEDIUM
    ): Long {
        if (!shouldVibrate(true, kind)) return 0L
        return when (strength) {
            HapticStrength.LIGHT -> 12L
            HapticStrength.MEDIUM -> KEY_VIBRATION_MS
            HapticStrength.STRONG -> 28L
        }
    }

    fun vibrationAmplitude(strength: HapticStrength): Int = when (strength) {
        HapticStrength.LIGHT -> 70
        HapticStrength.MEDIUM -> -1
        HapticStrength.STRONG -> 255
    }

    fun soundVolume(volume: SoundVolume): Float = when (volume) {
        SoundVolume.LOW -> 0.35f
        SoundVolume.MEDIUM -> 0.7f
        SoundVolume.HIGH -> 1f
    }

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
