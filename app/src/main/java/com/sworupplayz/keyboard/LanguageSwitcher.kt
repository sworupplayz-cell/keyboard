package com.sworupplayz.keyboard

data class LanguageOption(
    val language: KeyboardLanguage,
    val label: String,
    val description: String
)

data class LanguagePickerItem(
    val option: LanguageOption,
    val selected: Boolean
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

    fun pickerLabel(language: KeyboardLanguage): String =
        options().first { it.language == language }.label

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

/** Open/closed picker state. Selecting a label dismisses the overlay. */
class LanguagePickerState {
    var isOpen: Boolean = false
        private set
    var current: KeyboardLanguage = KeyboardLanguage.ENGLISH
        private set

    fun open(current: KeyboardLanguage) {
        this.current = current
        isOpen = true
    }

    fun dismiss() {
        isOpen = false
    }

    fun items(): List<LanguagePickerItem> = LanguageSwitcher.options().map { option ->
        LanguagePickerItem(option, selected = option.language == current)
    }

    fun select(label: String): KeyboardLanguage? {
        val language = LanguageSwitcher.fromPickerLabel(label) ?: return null
        current = language
        dismiss()
        return language
    }
}
