package com.sworupplayz.keyboard

data class LanguageOption(
    val language: KeyboardLanguage,
    val label: String,
    val description: String
)

/** Shared cycle + picker labels. Does not touch Android's system IME switcher. */
object LanguageSwitcher {
    fun cycle(current: KeyboardLanguage): KeyboardLanguage = current.next()

    fun options(): List<LanguageOption> = listOf(
        LanguageOption(KeyboardLanguage.ENGLISH, "English", "English"),
        LanguageOption(KeyboardLanguage.NEPALI, "नेपाली", "Nepali"),
        LanguageOption(KeyboardLanguage.ROMAN, "Roman Nepali", "Romanized Nepali")
    )

    fun pickerLabels(): List<String> = options().map { it.label }

    fun fromPickerLabel(label: String): KeyboardLanguage? =
        options().firstOrNull { it.label == label || it.description == label }?.language

    fun toolbarLabel(language: KeyboardLanguage): String = when (language) {
        KeyboardLanguage.ENGLISH -> "EN"
        KeyboardLanguage.NEPALI -> "ने"
        KeyboardLanguage.ROMAN -> "Ro"
    }

    fun toolbarDescription(language: KeyboardLanguage): String =
        options().first { it.language == language }.description
}
