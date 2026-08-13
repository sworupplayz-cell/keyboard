package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartKeyboardBehaviorTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }

    @Test
    fun punctuationRemovesTheExtraSpaceAndAddsOneAfterTheMark() {
        val comma = PunctuationSpacing.plan("hello ", ",")
        assertEquals(1, comma.deleteBefore)
        assertEquals(",", comma.text)
        assertTrue(comma.insertTrailingSpace)

        val danda = PunctuationSpacing.plan("ठिक छ ", "।")
        assertEquals(1, danda.deleteBefore)
        assertTrue(danda.insertTrailingSpace)

        val off = PunctuationSpacing.plan("hello ", ",", enabled = false)
        assertEquals(0, off.deleteBefore)
        assertFalse(off.insertTrailingSpace)
    }

    @Test
    fun punctuationDoesNotRewriteUrlsEmailsHashtagsOrDecimals() {
        assertFalse(PunctuationSpacing.plan("https://example.com", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("user@example.com", ".").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("#nepali", "!").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("@ram", ",").insertTrailingSpace)
        assertFalse(PunctuationSpacing.plan("3", ".").insertTrailingSpace)
        assertTrue(SpecialTokenPolicy.looksLikeUrl("https://example.com"))
        assertTrue(SpecialTokenPolicy.looksLikeEmail("user@example.com"))
        assertTrue(SpecialTokenPolicy.looksLikeMentionOrHashtag("#tag"))
        assertTrue(SpecialTokenPolicy.looksLikeNumber("3.14"))
    }

    @Test
    fun doubleSpacePeriodUsesTheCurrentLanguageAndSkipsProtectedTokens() {
        assertTrue(DoubleSpacePolicy.canTrigger("hello "))
        assertEquals(". ", DoubleSpacePolicy.replacement(KeyboardLanguage.ENGLISH))
        assertEquals(". ", DoubleSpacePolicy.replacement(KeyboardLanguage.ROMAN))
        assertEquals("। ", DoubleSpacePolicy.replacement(KeyboardLanguage.NEPALI))
        assertFalse(DoubleSpacePolicy.canTrigger("https://example.com "))
        assertFalse(DoubleSpacePolicy.canTrigger("3.14 "))
        assertFalse(DoubleSpacePolicy.canTrigger("hello. "))
        assertTrue(DoubleSpacePolicy.shouldReplace("done ", 200, enabled = true))
        assertFalse(DoubleSpacePolicy.shouldReplace("done ", 200, enabled = false))
        assertFalse(DoubleSpacePolicy.shouldReplace("done ", 2_000, enabled = true))
    }

    @Test
    fun capitalizationFollowsSentenceEndAndNewlineButSkipsProtectedTokens() {
        assertTrue(CapitalizationPolicy.shouldCapitalize(""))
        assertTrue(CapitalizationPolicy.shouldCapitalize("Hello. "))
        assertTrue(CapitalizationPolicy.shouldCapitalize("ठीक छ।"))
        assertTrue(CapitalizationPolicy.shouldCapitalize("line\n"))
        assertFalse(CapitalizationPolicy.shouldCapitalize("Hello "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("go to https://example.com "))
        assertFalse(CapitalizationPolicy.shouldCapitalize("see @ram "))
        assertEquals("H", CapitalizationPolicy.applyIncomingLetter("h", ""))
        assertEquals("h", CapitalizationPolicy.applyIncomingLetter("h", "say "))
        assertEquals("h", CapitalizationPolicy.applyIncomingLetter("h", "Hello. ", enabled = false))
        assertEquals("Hello", CapitalizationPolicy.applyToWord("hello", "Yes! "))
    }

    @Test
    fun graphemeBackspaceRemovesClustersEmojiFlagsAndZwjSequences() {
        assertEquals("hello", GraphemeBackspace.apply("hello!"))
        val afterNepali = GraphemeBackspace.apply("नेपाली")
        assertTrue(afterNepali.length < "नेपाली".length)
        assertTrue("नेपाली".startsWith(afterNepali))
        assertEquals("hi ", GraphemeBackspace.apply("hi 😊"))
        assertEquals("flag ", GraphemeBackspace.apply("flag 🇳🇵"))
        assertEquals("skin ", GraphemeBackspace.apply("skin 👍🏻"))
        assertEquals("family ", GraphemeBackspace.apply("family 👨‍👩‍👧"))
        assertTrue(GraphemeBackspace.codeUnitsToDelete("👨‍👩‍👧") == "👨‍👩‍👧".length)
        assertTrue(GraphemeBackspace.codeUnitsToDelete("🇳🇵") == "🇳🇵".length)
    }

    @Test
    fun suggestionReplacementTouchesOnlyTheCurrentWordAndPreservesCaps() {
        val plan = SuggestionSelectionPlan.create("he", "hello", "say he")
        assertEquals("hello", plan?.replacement)
        assertEquals(2, plan?.deleteCodeUnits)
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
        assertFalse(CorrectionPolicy.shouldAutoReplace("zorpa", "something"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("aa"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("@@@"))
        assertTrue(WordLearningPolicy.shouldLearnAccepted("Zorplet"))
    }

    @Test
    fun mixedEnglishAndNepaliStayNaturalAndUnknownWordsAreNotForced() {
        val composer = RomanInputComposer(converter)
        assertEquals("म school जान्छु", typeSentence(composer, listOf("ma", "school", "jaanchu")))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("zorpa".let { converter.bestConversion(it) }.let(RomanNepaliConverter::isNepali), true)
        assertEquals("unknown", converter.bestConversion("unknown"))
        assertEquals("school", converter.bestConversion("school", previousWord = "the"))
    }

    @Test
    fun emojiSearchAcceptsSimpleSingularPluralVariants() {
        EmojiCatalog.resetToBuiltin()
        assertTrue(EmojiCatalog.search("dog").isNotEmpty())
        assertTrue(EmojiCatalog.search("dogs").isNotEmpty())
        assertTrue(EmojiCatalog.search("hearts").isNotEmpty() || EmojiCatalog.search("heart").isNotEmpty())
        assertTrue(EmojiCatalog.search("").isEmpty())
    }

    @Test
    fun smartSettingsDefaultOnAndPersistIndependently() {
        val settings = KeyboardSettingsRepository(FakeSettingsStorage()).load()
        assertTrue(settings.smartPunctuation)
        assertTrue(settings.doubleSpacePeriod)
        assertTrue(settings.autoCapitalization)
        assertTrue(settings.emojiRecents)

        val storage = FakeSettingsStorage()
        KeyboardSettingsRepository(storage).apply {
            setSmartPunctuation(false)
            setDoubleSpacePeriod(false)
            setAutoCapitalization(false)
            setEmojiRecents(false)
        }
        val restored = KeyboardSettingsRepository(storage).load()
        assertFalse(restored.smartPunctuation)
        assertFalse(restored.doubleSpacePeriod)
        assertFalse(restored.autoCapitalization)
        assertFalse(restored.emojiRecents)
    }

    private fun typeSentence(composer: RomanInputComposer, words: List<String>): String {
        val output = StringBuilder()
        words.forEachIndexed { index, word ->
            word.forEach { composer.type(it.toString()) }
            val boundary = if (index == words.lastIndex) "" else " "
            output.append((composer.finishWord(boundary) as RomanEdit.Commit).text)
        }
        return output.toString()
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }

    private class FakeSettingsStorage : SettingsStorage {
        private val values = linkedMapOf<String, Any>()
        override fun contains(key: String): Boolean = key in values
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue
        override fun getString(key: String): String? = values[key] as? String
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }
}
