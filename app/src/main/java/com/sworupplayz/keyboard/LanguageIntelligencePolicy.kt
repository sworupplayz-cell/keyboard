package com.sworupplayz.keyboard

/**
 * Mode stays where the user put it. Suggestions may mix languages only when
 * the current mode already allows it (Roman / explicit phrase seeds).
 */
object LanguageIntelligencePolicy {
    fun keepManualMode(current: KeyboardLanguage, typedWord: String): KeyboardLanguage {
        typedWord.trim()
        return current
    }

    fun prefersEnglish(language: KeyboardLanguage): Boolean =
        language == KeyboardLanguage.ENGLISH

    fun prefersNepali(language: KeyboardLanguage): Boolean =
        language == KeyboardLanguage.NEPALI

    fun prefersRomanConversion(language: KeyboardLanguage): Boolean =
        language == KeyboardLanguage.ROMAN

    fun allowMixedEnglish(language: KeyboardLanguage): Boolean =
        language == KeyboardLanguage.ROMAN || language == KeyboardLanguage.ENGLISH

    fun staysInSelectedMode(current: KeyboardLanguage, typed: String): Boolean =
        keepManualMode(current, typed) == current
}
