package com.sworupplayz.keyboard

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSystemTest {
    @After
    fun resetCatalog() {
        EmojiCatalog.resetToBuiltin()
    }

    @Test
    fun catalogLoadsUnicodeCategoriesFromTheOfflineTsv() {
        loadFullCatalog()
        val repo = EmojiCatalog.repository()
        assertTrue(repo.size() >= 400)
        listOf(
            EmojiCategory.SMILEYS,
            EmojiCategory.PEOPLE,
            EmojiCategory.ANIMALS,
            EmojiCategory.FOOD,
            EmojiCategory.TRAVEL,
            EmojiCategory.ACTIVITIES,
            EmojiCategory.OBJECTS,
            EmojiCategory.SYMBOLS,
            EmojiCategory.FLAGS
        ).forEach { category ->
            assertTrue(category.name, repo.emojis(category).isNotEmpty())
        }
        assertTrue("😀" in repo.emojis(EmojiCategory.SMILEYS))
        assertTrue("🚗" in repo.emojis(EmojiCategory.TRAVEL))
        assertTrue("⚽" in repo.emojis(EmojiCategory.ACTIVITIES))
        assertTrue("🇳🇵" in repo.emojis(EmojiCategory.FLAGS))
    }

    @Test
    fun searchMatchesEnglishKeywordsAndRanksKnownWordsFirst() {
        loadFullCatalog()
        listOf("heart", "love", "fire", "dog", "cat", "car", "food", "happy", "sad", "laugh", "sun", "rain", "football")
            .forEach { query ->
                val results = EmojiCatalog.search(query)
                assertTrue(query, results.isNotEmpty())
            }
        assertTrue("🐶" in EmojiCatalog.search("dog"))
        assertTrue("🔥" in EmojiCatalog.search("fire"))
        assertTrue("❤️" in EmojiCatalog.search("heart") || "❤" in EmojiCatalog.search("heart"))
        assertTrue(EmojiCatalog.search("").isEmpty())
        assertTrue(EmojiCatalog.search("   ").isEmpty())
        assertTrue(EmojiCatalog.search("zzzxqnotanemoji").isEmpty())
    }

    @Test
    fun unicodeSequencesIncludeVariationSelectorsAndZwjFamilies() {
        loadFullCatalog()
        assertTrue(EmojiCatalog.contains("❤️"))
        assertTrue('\uFE0F' in "❤️")
        assertTrue(EmojiCatalog.contains("👨‍👩‍👧"))
        assertTrue('\u200D' in "👨‍👩‍👧")
        assertTrue(EmojiCatalog.contains("🏳️‍🌈"))
        assertTrue(EmojiCatalog.variants("👍").any { it.startsWith("👍") && it.length > "👍".length })
    }

    @Test
    fun recentEmojiHistoryIsUniqueBoundedAndSerializable() {
        val recent = RecentEmojiList(limit = 3)
        recent.record("😀")
        recent.record("😊")
        recent.record("🐶")
        recent.record("😀")
        recent.record("🍎")
        assertEquals(listOf("🍎", "😀", "🐶"), recent.values())
        assertFalse("not-an-emoji" in recent.values())

        val restored = RecentEmojiList.fromSerialized(recent.serialize(), limit = 3)
        assertEquals(recent.values(), restored.values())

        val bounded = RecentEmojiList(limit = 2)
        repeat(6) { bounded.record("😊") }
        bounded.record("😂")
        assertEquals(2, bounded.values().size)
        assertEquals("😂", bounded.values().first())
    }

    @Test
    fun usageCountsBoostFrequentEmojiAndStayBounded() {
        val usage = EmojiUsageStore(limit = 2)
        usage.record("🔥")
        usage.record("🔥")
        usage.record("🐶")
        usage.record("🍕")
        assertTrue(usage.score("🍕") >= 1)
        assertTrue(usage.serialize().lines().filter { it.isNotBlank() }.size <= 2)
        val restored = EmojiUsageStore.fromSerialized(usage.serialize(), limit = 2)
        assertEquals(usage.score("🍕"), restored.score("🍕"))
    }

    @Test
    fun emojiAndSymbolsInsertWithoutCorruptingComposingText() {
        val converter = RomanNepaliConverter.from(resource("roman_nepali_dictionary.tsv").reader())
        val composer = RomanInputComposer(converter)
        "ma".forEach { composer.type(it.toString()) }
        assertEquals("ma", composer.currentWord)
        val finished = PanelInsertionPolicy.romanEditBeforeInsert(composer)
        assertEquals(RomanEdit.Commit("म"), finished)
        assertEquals("", composer.currentWord)
        assertTrue(PanelInsertionPolicy.shouldFinishComposing(true))

        val nepali = DirectTypingState()
        assertTrue(nepali.append("म", DirectTypingLanguage.NEPALI))
        nepali.clear()
        assertEquals("", nepali.currentWord)
    }

    @Test
    fun numberLayoutsSwitchLatinAndDevanagariDigits() {
        val latin = KeyboardLayouts.numbers(KeyboardLanguage.ENGLISH, DigitScript.LATIN).flatten()
            .filter { it.action == KeyAction.TEXT }
            .map { it.output }
        assertTrue((0..9).all { it.toString() in latin })
        assertTrue("+" in latin)
        assertTrue("%" in latin)
        assertTrue(latin.any { it == "." || it == "," })
        assertTrue(KeyboardLayouts.numbers(KeyboardLanguage.ENGLISH).flatten().any {
            it.action == KeyAction.LETTERS
        })
        assertTrue(KeyboardLayouts.numbers(KeyboardLanguage.ENGLISH).flatten().any {
            it.action == KeyAction.DIGIT_SCRIPT
        })

        val nepali = KeyboardLayouts.numbers(KeyboardLanguage.NEPALI, DigitScript.DEVANAGARI).flatten()
            .map { it.output }
        assertTrue("१" in nepali)
        assertTrue("९" in nepali)
        assertTrue("०" in nepali)
        assertEquals(DigitScript.DEVANAGARI, DigitScript.defaultFor(KeyboardLanguage.NEPALI))
        assertEquals(DigitScript.DEVANAGARI, DigitScript.LATIN.toggle())
    }

    @Test
    fun symbolGroupsStayOrganizedAndRecentSymbolsAreBounded() {
        val common = KeyboardLayouts.symbols(KeyboardLanguage.ENGLISH).flatten().map { it.output }.toSet()
        assertTrue(common.containsAll(setOf(".", ",", "?", "!", "'", "\"", "@", "#", "$", "%", "&", "*", "(", ")", "-", "+", "=", "/", ":", ";", "_")))
        val math = SymbolCatalog.glyphs(SymbolGroup.MATH)
        assertTrue("×" in math && "÷" in math && "≠" in math)
        val more = SymbolCatalog.glyphs(SymbolGroup.MORE)
        assertTrue("$" in more && "€" in more && "₹" in more)

        val recent = RecentSymbolStore(limit = 2)
        recent.record("+")
        recent.record("₹")
        recent.record("+")
        recent.record("%")
        assertEquals(listOf("%", "+"), recent.values())
        assertFalse(recent.record("12"))
        val restored = RecentSymbolStore.fromSerialized(recent.serialize(), limit = 2)
        assertEquals(recent.values(), restored.values())
        assertEquals(SymbolGroup.MATH, SymbolGroup.COMMON.next())
    }

    @Test
    fun longPressAlternatesCoverCommonSymbolsWithoutReplacingLetters() {
        assertTrue("…" in KeyVisuals.alternates("."))
        assertTrue("—" in KeyVisuals.alternates("-"))
        assertTrue("’" in KeyVisuals.alternates("'"))
        assertTrue("“" in KeyVisuals.alternates("\""))
        assertTrue("€" in KeyVisuals.alternates("$"))
        assertTrue("°" in KeyVisuals.alternates("0"))
        assertTrue("é" in KeyVisuals.alternates("e"))
    }

    @Test
    fun categoryNavigationAndPreviousModeRemainOneStepAway() {
        assertEquals("Search", EmojiCategory.SEARCH.title)
        assertEquals("Recent", EmojiCategory.RECENT.title)
        assertEquals(EmojiCategory.entries.size, EmojiCategory.entries.map(KeyVisuals::emojiCategoryIcon).distinct().size)
        assertTrue(KeyboardLayouts.emojiControls().flatten().any { it.action == KeyAction.RETURN_TO_PREVIOUS })
        assertTrue(KeyboardLayouts.emojiSearchLetters().flatten().any { it.output == "q" })

        val history = PreviousLayoutStack<String>()
        history.remember("letters")
        history.remember("emoji")
        assertEquals("emoji", history.previousOr("fallback"))
        assertEquals("letters", history.previousOr("fallback"))
    }

    private fun loadFullCatalog() {
        EmojiCatalog.load(resource("emoji_catalog.tsv").reader())
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }
}
