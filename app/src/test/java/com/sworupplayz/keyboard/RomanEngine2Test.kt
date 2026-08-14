package com.sworupplayz.keyboard

import java.io.File
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanEngine2Test {
    private val englishWords by lazy { VocabularyLoader.english(resource("english_vocabulary.txt").reader()) }
    private val converter by lazy {
        RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader(), englishWords.toSet())
    }
    private val phoneticOnly by lazy { RomanNepaliConverter.from(StringReader("")) }

    @Test
    fun independentVowelsConvertWithoutADictionary() {
        val expected = mapOf(
            "a" to "अ",
            "aa" to "आ",
            "i" to "इ",
            "ii" to "ई",
            "ee" to "ई",
            "u" to "उ",
            "uu" to "ऊ",
            "oo" to "ऊ",
            "e" to "ए",
            "ai" to "ऐ",
            "o" to "ओ",
            "au" to "औ",
            "ri" to "ऋ"
        )
        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, RomanPhoneticEngine.transliterate(roman))
        }
    }

    @Test
    fun consonantsAndAspiratesUseEverydayNepaliValues() {
        val expected = mapOf(
            "ka" to "क",
            "kha" to "ख",
            "ga" to "ग",
            "gha" to "घ",
            "cha" to "च",
            "chha" to "छ",
            "ja" to "ज",
            "jha" to "झ",
            "ta" to "त",
            "tha" to "थ",
            "da" to "द",
            "dha" to "ध",
            "pa" to "प",
            "pha" to "फ",
            "ba" to "ब",
            "bha" to "भ",
            "sa" to "स",
            "sha" to "श",
            "va" to "व",
            "wa" to "व"
        )
        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, RomanPhoneticEngine.transliterate(roman))
        }
        assertTrue("ष" in RomanPhoneticEngine.candidates("sha"))
        assertTrue("ब" in RomanPhoneticEngine.candidates("va"))
    }

    @Test
    fun matrasAttachToThePreviousConsonant() {
        val expected = mapOf(
            "ka" to "क",
            "kaa" to "का",
            "ki" to "कि",
            "kii" to "की",
            "kee" to "की",
            "ku" to "कु",
            "kuu" to "कू",
            "ke" to "के",
            "kai" to "कै",
            "ko" to "को",
            "kau" to "कौ"
        )
        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, RomanPhoneticEngine.transliterate(roman))
        }
        assertTrue("कृ" in RomanPhoneticEngine.candidates("kri"))
    }

    @Test
    fun conjunctsAndCommonClustersKeepVirama() {
        val expected = mapOf(
            "kya" to "क्य",
            "kra" to "क्र",
            "pra" to "प्र",
            "tra" to "त्र",
            "shra" to "श्र",
            "gya" to "ज्ञ",
            "ksha" to "क्ष",
            "ghar" to "घर",
            "jaanchu" to "जान्छु"
        )
        expected.forEach { (roman, nepali) ->
            assertEquals(nepali, RomanPhoneticEngine.transliterate(roman))
        }
        assertTrue(RomanPhoneticEngine.transliterate("ramro")!!.contains('्'))
    }

    @Test
    fun unknownWordsStillGetDevanagariFromThePhoneticEngineAlone() {
        listOf("zorpa", "zorpaa", "blorvik", "qompa").forEach { madeUp ->
            val generated = phoneticOnly.bestConversion(madeUp)
            assertNotEquals(madeUp, generated)
            assertTrue(madeUp, RomanNepaliConverter.isNepali(generated))
            assertEquals(generated, RomanPhoneticEngine.transliterate(madeUp))
        }
        assertEquals("जोर्पा", phoneticOnly.bestConversion("zorpaa"))
        assertEquals("मनपर्छ", phoneticOnly.bestConversion("manparcha"))
        assertEquals("मनपर्छ", RomanPhoneticEngine.transliterate("manparcha"))
    }

    @Test
    fun alternateSpellingsShareReusableNormalizationRules() {
        val pairs = listOf(
            "cha" to "chha",
            "aja" to "aaja",
            "bhaneko" to "vaneko",
            "ramro" to "ramrooo",
            "timi" to "timii",
            "mero" to "meroo"
        )
        pairs.forEach { (first, second) ->
            assertEquals(converter.bestConversion(first), converter.bestConversion(second))
            assertTrue(
                RomanSpellingNormalizer.lookupForms(second).any {
                    it == RomanSpellingNormalizer.normalize(first) || it == second
                }
            )
        }
        assertEquals("छ", converter.bestConversion("cha"))
        assertEquals("छ", converter.bestConversion("chha"))
        assertEquals("आज", converter.bestConversion("aja"))
        assertEquals("भनेको", converter.bestConversion("vaneko"))
        assertEquals("राम्रो", converter.bestConversion("ramrooo"))
        assertEquals("तिमी", converter.bestConversion("timii"))
        assertEquals("मेरो", converter.bestConversion("meroo"))
    }

    @Test
    fun ambiguousSpellingsStayWithinThreeRankedSuggestions() {
        val cha = converter.suggestions("cha")
        assertEquals("छ", cha.first())
        assertTrue("च" in cha)
        assertTrue("cha" in cha)
        assertTrue(cha.size <= 3)

        val chh = converter.suggestions("chha")
        assertEquals("छ", chh.first())
        assertTrue(chh.size <= 3)
    }

    @Test
    fun englishWordsAndMixedSentencesStayNatural() {
        assertEquals("school", converter.bestConversion("school"))
        assertEquals("unknown", converter.bestConversion("unknown"))
        assertEquals("hello", converter.bestConversion("hello"))

        assertEquals("म school जान्छु", converter.convertText("ma school jaanchu"))
        assertEquals("म घर जान्छु", converter.convertText("ma ghar jaanchu"))
        assertEquals("तिमी कहाँ छौ", converter.convertText("timi kaha chau"))
        assertEquals("मलाई नेपाली मनपर्छ", converter.convertText("malai nepali manparcha"))
    }

    @Test
    fun punctuationNumbersEmojiAndLinksArePreserved() {
        assertEquals("म घर जान्छु!", converter.convertText("ma ghar jaanchu!"))
        assertEquals("तिमी, कहाँ छौ?", converter.convertText("timi, kaha chau?"))
        assertEquals("मलाई नेपाली मनपर्छ 123", converter.convertText("malai nepali manparcha 123"))
        assertEquals("म 2 घर", converter.convertText("ma 2 ghar"))
        assertEquals("hello 😀", converter.convertText("hello 😀"))
        assertEquals(
            "visit https://example.com now",
            converter.convertText("visit https://example.com now")
        )
        assertEquals("hello @ram #nepali", converter.convertText("hello @ram #nepali"))
        assertEquals("write user@example.com later", converter.convertText("write user@example.com later"))
        assertEquals("ok.", converter.convertText("ok."))
    }

    @Test
    fun backspaceAndCursorPoliciesStayWordSafe() {
        val composer = RomanInputComposer(converter)
        "manparcha".forEach { composer.type(it.toString()) }
        assertEquals(RomanEdit.SetComposing("manparch"), composer.backspace())
        composer.type("a")
        assertEquals(RomanEdit.Commit("मनपर्छ "), composer.finishWord(" "))

        assertFalse(RomanSelectionState.movedAwayFromComposition("ghar", 4, 4, 4))
        assertTrue(RomanSelectionState.movedAwayFromComposition("ghar", 1, 1, 4))
        assertFalse(RomanSelectionState.movedAwayFromComposition("", 1, 1, 4))
    }

    @Test
    fun learnedWordsOutrankPhoneticGuessesAndSurviveSerialization() {
        val learned = LearnedRomanWords()
        assertTrue(learned.learn("zorpak", "जोर्पाक"))
        val restored = LearnedRomanWords.fromSerialized(learned.serialize())
        assertEquals("जोर्पाक", restored.lookup("zorpak"))
        assertEquals("जोर्पाक", converter.suggestions("zorpak", restored.lookup("zorpak")).first())
        assertEquals("जोर्पाक", converter.bestConversion("zorpak", restored.lookup("zorpak")))
        assertTrue(converter.suggestions("zorpak", restored.lookup("zorpak")).size <= 3)
    }

    @Test
    fun contextAndFrequencyHelpRankAmbiguousRomanInput() {
        val suggestions = converter.suggestions(
            "gh",
            recent = listOf("घर"),
            previousWord = "mero",
            contextPredictions = listOf("ghar")
        )
        assertTrue("घर" in suggestions)
        assertTrue(suggestions.size <= 3)
        assertFalse(CorrectionPolicy.shouldAutoReplace("gh", "घर"))
    }

    @Test
    fun dictionaryIsNeverTheOnlyConversionPath() {
        assertEquals("मनपर्छ", phoneticOnly.convertText("manparcha"))
        assertEquals("जान्छु", phoneticOnly.bestConversion("jaanchu"))
        assertEquals("school", phoneticOnly.bestConversion("school"))
        assertNotEquals("qompa", phoneticOnly.bestConversion("qompa"))
    }

    @Test
    fun nasalAndHalantMarksRemainAvailable() {
        val (anusvara, candrabindu) = RomanPhoneticEngine.nasalMarks
        assertEquals('ं', anusvara)
        assertEquals('ँ', candrabindu)
        assertTrue(RomanPhoneticEngine.candidates("anka").any { 'ं' in it || 'न' in it })
        assertTrue(RomanPhoneticEngine.transliterate("pra")!!.contains('्') || RomanPhoneticEngine.transliterate("pra") == "प्र")
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
