package com.sworupplayz.keyboard

import java.io.Reader

enum class VocabularyLanguage { ENGLISH, NEPALI, ROMAN, MIXED }

enum class VocabularyCategory {
    CORE,
    MORPHOLOGY,
    NAME,
    PLACE,
    SLANG,
    TECH,
    MIXED
}

/**
 * One offline dictionary row. Existing word lists stay valid: rank is file
 * order, and optional tab fields can add language, category, stem, and
 * alternates without a second competing dictionary.
 */
data class VocabularyEntry(
    val word: String,
    val rank: Int,
    val language: VocabularyLanguage,
    val alternates: List<String> = emptyList(),
    val category: VocabularyCategory = VocabularyCategory.CORE,
    val stem: String? = null
)

/**
 * Structured view of the bundled word lists. Callers still type against
 * [LocalWordSuggester]; this catalog is for ranking metadata and tests.
 */
object VocabularyCatalog {
    fun english(reader: Reader): List<VocabularyEntry> =
        VocabularyLoader.entries(reader, VocabularyLanguage.ENGLISH)

    fun nepali(reader: Reader): List<VocabularyEntry> =
        VocabularyLoader.entries(reader, VocabularyLanguage.NEPALI)

    fun classify(word: String, language: VocabularyLanguage): VocabularyCategory {
        val key = word.trim()
        if (key.isEmpty()) return VocabularyCategory.CORE
        val lower = key.lowercase()
        return when {
            language == VocabularyLanguage.ENGLISH && lower in SLANG -> VocabularyCategory.SLANG
            language == VocabularyLanguage.ENGLISH && lower in TECH -> VocabularyCategory.TECH
            language == VocabularyLanguage.ENGLISH && lower in PLACES -> VocabularyCategory.PLACE
            language == VocabularyLanguage.ENGLISH && lower in NAMES -> VocabularyCategory.NAME
            Morphology.isInflectedEnglish(key) -> VocabularyCategory.MORPHOLOGY
            Morphology.isInflectedNepali(key) -> VocabularyCategory.MORPHOLOGY
            else -> VocabularyCategory.CORE
        }
    }

    fun shouldStayBehindCore(category: VocabularyCategory): Boolean =
        category == VocabularyCategory.SLANG ||
            category == VocabularyCategory.NAME ||
            category == VocabularyCategory.PLACE

    private val SLANG = setOf(
        "lol", "lmao", "rofl", "omg", "btw", "idk", "imo", "brb", "tbh", "smh",
        "gonna", "wanna", "gotta", "kinda", "sorta", "yup", "nope", "nah", "pls",
        "plz", "thx", "nvm", "fyi", "bro", "okay"
    )
    private val TECH = setOf(
        "google", "youtube", "facebook", "instagram", "whatsapp", "wifi", "email",
        "login", "password", "download", "upload", "keyboard", "android", "iphone"
    )
    private val PLACES = setOf(
        "nepal", "kathmandu", "pokhara", "lalitpur", "bhaktapur", "chitwan",
        "lumbini", "everest", "himalaya"
    )
    private val NAMES = setOf(
        "ram", "sita", "hari", "gita", "krishna", "suman", "bikash", "prakash",
        "john", "mary", "david", "sarah", "alex", "sam"
    )
}
