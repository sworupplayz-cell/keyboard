package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val nepaliWords by lazy {
        VocabularyLoader.mergeDistinct(
            VocabularyLoader.nepali(resource("nepali_vocabulary.txt").reader()),
            VocabularyLoader.nepaliFromRomanDictionary(resource("roman_nepali_dictionary.tsv").reader())
        )
    }
    private val english by lazy { LocalWordSuggester.fromWords(englishWords) }
    private val nepali by lazy { LocalWordSuggester.fromWords(nepaliWords) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }
    private val engine by lazy { SuggestionEngine(english, nepali, converter) }

    @Test
    fun englishPrefixesStayCompactAndUseful() {
        val hel = visible(englishQuery("hel"))
        assertTrue(hel.toString(), "hello" in hel)
        assertTrue(hel.toString(), "help" in hel)
        assertTrue(hel.toString(), "he'll" in hel)
        assertTrue(hel.size <= 3)

        val goo = visible(englishQuery("goo"))
        assertTrue(goo.toString(), "good" in goo)
        assertTrue(goo.toString(), "going" in goo)
        assertTrue(goo.toString(), goo.any { it.equals("google", ignoreCase = true) })
        assertTrue(goo.size <= 3)

        val tha = visible(englishQuery("tha"))
        assertTrue(tha.toString(), "that" in tha)
        assertTrue(tha.toString(), "thanks" in tha || "than" in tha)
        assertTrue(tha.size <= 3)
    }

    @Test
    fun nepaliPrefixesCoverEverydayForms() {
        val malai = visible(nepaliQuery("मला"))
        assertTrue(malai.toString(), "मलाई" in malai)
        assertTrue(malai.size <= 3)

        val ghar = visible(nepaliQuery("घर"))
        assertEquals("घर", ghar.first())
        assertTrue(ghar.toString(), "घरमा" in ghar)
        assertTrue(ghar.size <= 3)

        val tap = visible(nepaliQuery("तप"))
        assertTrue(tap.toString(), tap.any { it.startsWith("तपाई") })
        assertTrue(tap.size <= 3)
    }

    @Test
    fun romanizedPrefixesConvertWithoutRejectingUnknownWords() {
        val ma = visible(romanQuery("ma"))
        assertTrue(ma.toString(), "म" in ma)
        assertTrue(ma.toString(), "मा" in ma || "मलाई" in ma)
        assertTrue(ma.size <= 3)

        val mal = visible(romanQuery("mal"))
        assertTrue(mal.toString(), "मलाई" in mal)
        assertTrue(mal.toString(), "मैले" in mal || "मलाई" in mal)
        assertTrue(mal.size <= 3)

        val tim = visible(romanQuery("tim"))
        assertTrue(tim.toString(), "तिमी" in tim)
        assertTrue(tim.toString(), "तिम्रो" in tim || tim.size <= 3)

        val ghar = visible(romanQuery("ghar"))
        assertTrue(ghar.toString(), "घर" in ghar)
        assertTrue(ghar.toString(), "घरमा" in ghar || "ghar" in ghar)

        val jaan = visible(romanQuery("jaan"))
        assertTrue(jaan.toString(), jaan.any { it in setOf("जान्छु", "जान", "जानु") })

        val unknown = visible(romanQuery("zorpa"))
        assertTrue(RomanNepaliConverter.isNepali(unknown.first()))
        assertTrue("zorpa" in unknown)
        assertNotEquals("zorpa", converter.bestConversion("zorpa"))
        assertTrue(unknown.size <= 3)
    }

    @Test
    fun previousWordAndTwoWordPhrasesPredictTheNextWord() {
        val afterGood = visible(englishQuery("", previous = "good"))
        assertTrue(afterGood.toString(), "morning" in afterGood)
        assertTrue(afterGood.toString(), "night" in afterGood || "luck" in afterGood)
        assertTrue(afterGood.size <= 3)

        val howAre = visible(englishQuery("", previous = "are", previousTwo = "how are"))
        assertTrue(howAre.toString(), "you" in howAre)

        val thank = visible(englishQuery("", previous = "thank"))
        assertTrue(thank.toString(), "you" in thank)

        val malai = visible(nepaliQuery("", previous = "मलाई"))
        assertTrue(malai.toString(), malai.any { "मन" in it })

        val timilai = visible(nepaliQuery("", previous = "तिमीलाई"))
        assertTrue(timilai.toString(), timilai.any { "कस्तो" in it })
    }

    @Test
    fun mixedLanguageSuggestionsStaySplitAcrossLanguages() {
        val afterIAm = visible(englishQuery("", previous = "am", previousTwo = "i am"))
        assertTrue(afterIAm.toString(), "fine" in afterIAm || "going" in afterIAm)
        assertTrue(afterIAm.toString(), afterIAm.any { it == "घर" || it == "fine" || it == "going" })

        val afterMa = visible(romanQuery("", previous = "ma"))
        assertTrue(afterMa.toString(), afterMa.any { it in setOf("घर", "जान्छु", "school") })

        val afterMaSchool = visible(romanQuery("", previous = "school", previousTwo = "ma school"))
        assertTrue(afterMaSchool.toString(), afterMaSchool.any { it == "जान्छु" || it == "jaanchu" })

        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("I am घर", converter.convertText("I am ghar"))
        assertEquals("Nepali is awesome", converter.convertText("Nepali is awesome"))
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("unknown", converter.bestConversion("unknown"))
    }

    @Test
    fun typoSuggestionsAreOfferedButNeverAutoApplied() {
        val helo = visible(englishQuery("helo"))
        assertTrue(helo.toString(), "hello" in helo)
        assertFalse(CorrectionPolicy.shouldAutoReplace("helo", "hello"))
        assertFalse("hello" in english.suggestions("helo"))

        val teh = visible(englishQuery("teh"))
        assertTrue(teh.toString(), "the" in teh)

        val hllo = visible(englishQuery("hllo"))
        assertTrue(hllo.toString(), "hello" in hllo)

        assertTrue("नेपाली" in visible(romanQuery("nepali")))
        assertEquals("नेपाली", converter.bestConversion("nepali"))
        assertEquals("Nepali", converter.bestConversion("Nepali"))
    }

    @Test
    fun unknownNamesUrlsEmailsAndHashtagsStayExactlyAsTyped() {
        listOf("blablabla", "Sworup", "whatever123").forEach { word ->
            val suggestions = visible(englishQuery(word))
            assertTrue(word, word in suggestions || suggestions.isEmpty())
            assertFalse(CorrectionPolicy.shouldAutoReplace(word, "hello"))
        }
        assertEquals(listOf("https://example.com"), visible(englishQuery("https://example.com")))
        assertEquals(listOf("user@example.com"), visible(englishQuery("user@example.com")))
        assertEquals(listOf("#nepali"), visible(englishQuery("#nepali")))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("https://example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("user@example.com"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("#tag"))
        assertFalse(WordLearningPolicy.shouldLearnAccepted("whatever123"))
        assertFalse(WordLearningPolicy.shouldLearnUnknown("hi", english::contains))
        assertTrue(WordLearningPolicy.shouldLearnUnknown("Sworup", english::contains))
        assertFalse(WordLearningPolicy.shouldLearnRepeated("Sworup", 1, english::contains))
        assertTrue(WordLearningPolicy.shouldLearnRepeated("Sworup", 2, english::contains))
    }

    @Test
    fun capitalizationIsUnderstoodButNotRewrittenUnnecessarily() {
        val hello = visible(englishQuery("hello"))
        assertTrue(hello.toString(), "hello" in hello.map { it.lowercase() })
        val titled = visible(englishQuery("Hello"))
        assertTrue(titled.toString(), titled.any { it.startsWith("H") })
        val caps = visible(englishQuery("HELLO"))
        assertTrue(caps.toString(), caps.any { it == "HELLO" || it.startsWith("HE") })
        assertEquals("Hello", SuggestionSelectionPlan.preserveCapitalization("He", "hello"))
        assertEquals("HELLO", SuggestionSelectionPlan.preserveCapitalization("HE", "hello"))
        assertEquals("hello", SuggestionSelectionPlan.preserveCapitalization("he", "hello"))
    }

    @Test
    fun suggestionReplacementTouchesOnlyTheCurrentWord() {
        val field = StringBuilder("say he")
        val plan = SuggestionSelectionPlan.create("he", "hello", "say he")!!
        field.delete(field.length - plan.deleteCodeUnits, field.length)
        field.append(plan.replacement)
        assertEquals("say hello", field.toString())

        val punctuated = StringBuilder("say he!")
        assertNull(SuggestionSelectionPlan.create("he", "hello", "say he!"))
        assertTrue(SuggestionSelectionPlan.matchesCurrentWord("say he", "he"))
        assertFalse(SuggestionSelectionPlan.matchesCurrentWord("the", "he"))

        val afterAccept = DirectTypingState()
        "he".forEach { afterAccept.append(it.toString(), DirectTypingLanguage.ENGLISH) }
        afterAccept.replaceWith("hello")
        assertEquals("hello", afterAccept.currentWord)
        assertTrue(afterAccept.backspace())
        assertEquals("hell", afterAccept.currentWord)
    }

    @Test
    fun localLearningPrefersTapsAndRepeatedUnknownWords() {
        val learned = LearnedWordStore()
        assertTrue(learned.record("Sworup"))
        val suggestions = visible(
            SuggestionQuery(
                input = "Sw",
                language = SuggestionLanguage.ENGLISH,
                learned = learned.suggestions("Sw")
            )
        )
        assertEquals("Sworup", suggestions.first())
        assertTrue(suggestions.size <= 3)

        val repeats = RepeatFinishStore(limit = 2)
        assertEquals(1, repeats.record("Zorplet"))
        assertEquals(2, repeats.record("Zorplet"))
        assertTrue(WordLearningPolicy.shouldLearnRepeated("Zorplet", 2, english::contains))
        assertFalse(WordLearningPolicy.shouldLearnRepeated("aa", 4, english::contains))
    }

    @Test
    fun emojiSuggestionsAppearOnlyForClearKeywords() {
        EmojiCatalog.resetToBuiltin()
        val heart = engine.suggest(englishQuery("heart"))
        assertTrue(heart.emoji == "❤" || heart.emoji == "❤️" || "❤" in heart.visible() || "❤️" in heart.visible())
        val happy = engine.suggest(englishQuery("happy"))
        assertTrue(happy.emoji == "😊" || "😊" in happy.visible())
        val sad = engine.suggest(englishQuery("sad"))
        assertTrue(sad.emoji == "😢" || "😢" in sad.visible())
        val fire = engine.suggest(englishQuery("fire"))
        assertTrue(fire.emoji == "🔥" || "🔥" in fire.visible())
        val football = engine.suggest(englishQuery("football"))
        assertTrue(football.emoji == "⚽" || "⚽" in football.visible())
        assertNull(engine.suggest(englishQuery("he")).emoji)
        assertTrue(engine.suggest(englishQuery("he")).visible().none { EmojiCatalog.contains(it) })
    }

    @Test
    fun emptyPrefixWithoutContextReturnsNothingAndNeverExceedsThree() {
        assertTrue(visible(englishQuery("")).isEmpty())
        assertTrue(visible(nepaliQuery("")).isEmpty())
        assertTrue(visible(romanQuery("")).isEmpty())
        val many = visible(englishQuery("th"))
        assertTrue(many.size <= 3)
    }

    private fun visible(query: SuggestionQuery): List<String> =
        engine.suggest(query).visible()

    private fun englishQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(
        input = input,
        language = SuggestionLanguage.ENGLISH,
        previousWord = previous,
        previousTwoWords = previousTwo
    )

    private fun nepaliQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(
        input = input,
        language = SuggestionLanguage.NEPALI,
        previousWord = previous,
        previousTwoWords = previousTwo
    )

    private fun romanQuery(
        input: String,
        previous: String? = null,
        previousTwo: String? = null
    ) = SuggestionQuery(
        input = input,
        language = SuggestionLanguage.ROMAN,
        previousWord = previous,
        previousTwoWords = previousTwo
    )

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
