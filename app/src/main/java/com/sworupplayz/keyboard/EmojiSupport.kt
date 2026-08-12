package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

enum class EmojiCategory(val title: String, val storageKey: String) {
    SEARCH("Search", "search"),
    RECENT("Recent", "recent"),
    SMILEYS("Smileys", "smileys"),
    PEOPLE("People", "people"),
    ANIMALS("Animals", "animals"),
    FOOD("Food", "food"),
    TRAVEL("Travel", "travel"),
    ACTIVITIES("Activities", "activities"),
    OBJECTS("Objects", "objects"),
    SYMBOLS("Symbols", "symbols"),
    FLAGS("Flags", "flags");

    companion object {
        fun fromStorageKey(value: String): EmojiCategory? =
            entries.firstOrNull { it.storageKey == value }
    }
}

data class EmojiEntry(
    val glyph: String,
    val category: EmojiCategory,
    val keywords: List<String>,
    val variants: List<String> = emptyList()
) {
    fun allGlyphs(): List<String> = listOf(glyph) + variants
}

/** Offline Unicode catalog. Large tables live in raw resources and load once. */
object EmojiCatalog {
    @Volatile
    private var repository: EmojiRepository = EmojiRepository.builtin()

    fun load(reader: Reader) {
        repository = EmojiRepository.from(reader)
    }

    fun resetToBuiltin() {
        repository = EmojiRepository.builtin()
    }

    fun repository(): EmojiRepository = repository

    fun emojis(category: EmojiCategory, recent: List<String> = emptyList()): List<String> =
        repository.emojis(category, recent)

    fun contains(emoji: String): Boolean = repository.contains(emoji)

    fun variants(emoji: String): List<String> = repository.variants(emoji)

    fun search(query: String, limit: Int = 40, frequencyOf: (String) -> Int = { 0 }): List<String> =
        repository.search(query, limit, frequencyOf)
}

class EmojiRepository internal constructor(
    private val entries: List<EmojiEntry>
) {
    private val byCategory: Map<EmojiCategory, List<EmojiEntry>> =
        entries.groupBy { it.category }.mapValues { (_, values) -> values.distinctBy { it.glyph } }
    private val known = HashSet<String>(entries.size * 2).apply {
        entries.forEach { entry -> entry.allGlyphs().forEach(::add) }
    }
    private val variantMap: Map<String, List<String>> = buildMap {
        entries.forEach { entry ->
            if (entry.variants.isNotEmpty()) {
                put(entry.glyph, entry.variants)
                entry.variants.forEach { variant -> put(variant, entry.variants) }
            }
        }
    }
    private val searchIndex: EmojiSearchIndex = EmojiSearchIndex(entries)

    fun emojis(
        category: EmojiCategory,
        recent: List<String> = emptyList(),
        frequencyOf: (String) -> Int = { 0 }
    ): List<String> {
        if (category == EmojiCategory.RECENT || category == EmojiCategory.SEARCH) return recent
        val glyphs = byCategory[category].orEmpty().map { it.glyph }
        if (glyphs.isEmpty()) return emptyList()
        return glyphs.sortedWith(
            compareByDescending<String> { frequencyOf(it) }.thenBy { glyphs.indexOf(it) }
        )
    }

    fun entries(category: EmojiCategory): List<EmojiEntry> = byCategory[category].orEmpty()

    fun allEntries(): List<EmojiEntry> = entries

    fun contains(emoji: String): Boolean = emoji in known

    fun variants(emoji: String): List<String> = variantMap[emoji].orEmpty()

    fun search(query: String, limit: Int = 40, frequencyOf: (String) -> Int = { 0 }): List<String> =
        searchIndex.search(query, limit, frequencyOf)

    fun popularKeywords(limit: Int = 12): List<String> = searchIndex.popularKeywords(limit)

    fun size(): Int = entries.size

    companion object {
        fun from(reader: Reader): EmojiRepository {
            val parsed = ArrayList<EmojiEntry>()
            reader.buffered().useLines { lines ->
                lines.forEach { line ->
                    parseLine(line)?.let(parsed::add)
                }
            }
            return if (parsed.isEmpty()) builtin() else EmojiRepository(parsed)
        }

        fun builtin(): EmojiRepository = EmojiRepository(BUILTIN)

        private fun parseLine(line: String): EmojiEntry? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) return null
            val parts = trimmed.split('\t')
            if (parts.size < 3) return null
            val category = EmojiCategory.fromStorageKey(parts[1].trim()) ?: return null
            if (category == EmojiCategory.SEARCH || category == EmojiCategory.RECENT) return null
            val glyph = parts[0].trim()
            if (glyph.isEmpty()) return null
            val keywords = parts[2].split(' ').map { it.trim().lowercase(Locale.ENGLISH) }.filter { it.isNotEmpty() }
            val variants = parts.getOrNull(3).orEmpty().split('|').map(String::trim).filter { it.isNotEmpty() }
            return EmojiEntry(glyph, category, keywords, variants)
        }

        private val BUILTIN = listOf(
            EmojiEntry("😀", EmojiCategory.SMILEYS, listOf("grinning", "happy", "smile", "laugh")),
            EmojiEntry("😃", EmojiCategory.SMILEYS, listOf("smiley", "happy", "smile")),
            EmojiEntry("😄", EmojiCategory.SMILEYS, listOf("smile", "happy", "laugh")),
            EmojiEntry("😁", EmojiCategory.SMILEYS, listOf("beaming", "grin")),
            EmojiEntry("😆", EmojiCategory.SMILEYS, listOf("laughing", "happy")),
            EmojiEntry("😅", EmojiCategory.SMILEYS, listOf("sweat", "smile")),
            EmojiEntry("😂", EmojiCategory.SMILEYS, listOf("joy", "laugh", "lol")),
            EmojiEntry("😊", EmojiCategory.SMILEYS, listOf("blush", "smile", "happy")),
            EmojiEntry("🙂", EmojiCategory.SMILEYS, listOf("smile")),
            EmojiEntry("😍", EmojiCategory.SMILEYS, listOf("heart", "love")),
            EmojiEntry("😘", EmojiCategory.SMILEYS, listOf("kiss", "love")),
            EmojiEntry("😎", EmojiCategory.SMILEYS, listOf("cool")),
            EmojiEntry("🤔", EmojiCategory.SMILEYS, listOf("thinking")),
            EmojiEntry("😢", EmojiCategory.SMILEYS, listOf("cry", "sad")),
            EmojiEntry("😭", EmojiCategory.SMILEYS, listOf("sob", "sad")),
            EmojiEntry("😡", EmojiCategory.SMILEYS, listOf("angry", "mad")),
            EmojiEntry("👋", EmojiCategory.PEOPLE, listOf("wave", "hello"), listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿")),
            EmojiEntry("👍", EmojiCategory.PEOPLE, listOf("thumbs", "up", "like", "yes"), listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")),
            EmojiEntry("👎", EmojiCategory.PEOPLE, listOf("thumbs", "down")),
            EmojiEntry("🙏", EmojiCategory.PEOPLE, listOf("pray", "thanks", "namaste")),
            EmojiEntry("👏", EmojiCategory.PEOPLE, listOf("clap")),
            EmojiEntry("🙌", EmojiCategory.PEOPLE, listOf("hands")),
            EmojiEntry("👌", EmojiCategory.PEOPLE, listOf("ok")),
            EmojiEntry("✌", EmojiCategory.PEOPLE, listOf("peace")),
            EmojiEntry("🤞", EmojiCategory.PEOPLE, listOf("luck")),
            EmojiEntry("💪", EmojiCategory.PEOPLE, listOf("muscle", "strong")),
            EmojiEntry("🤝", EmojiCategory.PEOPLE, listOf("handshake")),
            EmojiEntry("👀", EmojiCategory.PEOPLE, listOf("eyes")),
            EmojiEntry("🧑", EmojiCategory.PEOPLE, listOf("person")),
            EmojiEntry("👩", EmojiCategory.PEOPLE, listOf("woman")),
            EmojiEntry("👨", EmojiCategory.PEOPLE, listOf("man")),
            EmojiEntry("👶", EmojiCategory.PEOPLE, listOf("baby")),
            EmojiEntry("👨‍👩‍👧", EmojiCategory.PEOPLE, listOf("family")),
            EmojiEntry("🐶", EmojiCategory.ANIMALS, listOf("dog", "puppy", "pet")),
            EmojiEntry("🐱", EmojiCategory.ANIMALS, listOf("cat", "kitten", "pet")),
            EmojiEntry("🐭", EmojiCategory.ANIMALS, listOf("mouse")),
            EmojiEntry("🐹", EmojiCategory.ANIMALS, listOf("hamster")),
            EmojiEntry("🐰", EmojiCategory.ANIMALS, listOf("rabbit")),
            EmojiEntry("🦊", EmojiCategory.ANIMALS, listOf("fox")),
            EmojiEntry("🐻", EmojiCategory.ANIMALS, listOf("bear")),
            EmojiEntry("🐼", EmojiCategory.ANIMALS, listOf("panda")),
            EmojiEntry("🐨", EmojiCategory.ANIMALS, listOf("koala")),
            EmojiEntry("🐯", EmojiCategory.ANIMALS, listOf("tiger")),
            EmojiEntry("🦁", EmojiCategory.ANIMALS, listOf("lion")),
            EmojiEntry("🐮", EmojiCategory.ANIMALS, listOf("cow")),
            EmojiEntry("🐷", EmojiCategory.ANIMALS, listOf("pig")),
            EmojiEntry("🐸", EmojiCategory.ANIMALS, listOf("frog")),
            EmojiEntry("🐵", EmojiCategory.ANIMALS, listOf("monkey")),
            EmojiEntry("🐦", EmojiCategory.ANIMALS, listOf("bird")),
            EmojiEntry("☀️", EmojiCategory.ANIMALS, listOf("sun", "sunny")),
            EmojiEntry("🌧️", EmojiCategory.ANIMALS, listOf("rain", "weather")),
            EmojiEntry("🍎", EmojiCategory.FOOD, listOf("apple", "fruit", "food")),
            EmojiEntry("🍌", EmojiCategory.FOOD, listOf("banana", "fruit")),
            EmojiEntry("🍇", EmojiCategory.FOOD, listOf("grapes")),
            EmojiEntry("🍉", EmojiCategory.FOOD, listOf("watermelon")),
            EmojiEntry("🍓", EmojiCategory.FOOD, listOf("strawberry")),
            EmojiEntry("🍒", EmojiCategory.FOOD, listOf("cherries")),
            EmojiEntry("🥭", EmojiCategory.FOOD, listOf("mango")),
            EmojiEntry("🍍", EmojiCategory.FOOD, listOf("pineapple")),
            EmojiEntry("🍔", EmojiCategory.FOOD, listOf("burger", "food")),
            EmojiEntry("🍕", EmojiCategory.FOOD, listOf("pizza", "food")),
            EmojiEntry("🍟", EmojiCategory.FOOD, listOf("fries")),
            EmojiEntry("🍚", EmojiCategory.FOOD, listOf("rice", "food")),
            EmojiEntry("🍜", EmojiCategory.FOOD, listOf("noodles", "ramen")),
            EmojiEntry("🍰", EmojiCategory.FOOD, listOf("cake")),
            EmojiEntry("☕", EmojiCategory.FOOD, listOf("coffee")),
            EmojiEntry("🍺", EmojiCategory.FOOD, listOf("beer")),
            EmojiEntry("🚗", EmojiCategory.TRAVEL, listOf("car", "travel")),
            EmojiEntry("✈", EmojiCategory.TRAVEL, listOf("plane", "flight")),
            EmojiEntry("🚲", EmojiCategory.TRAVEL, listOf("bike")),
            EmojiEntry("🏠", EmojiCategory.TRAVEL, listOf("house", "home")),
            EmojiEntry("⚽", EmojiCategory.ACTIVITIES, listOf("football", "soccer", "sport")),
            EmojiEntry("🎮", EmojiCategory.ACTIVITIES, listOf("game")),
            EmojiEntry("🎵", EmojiCategory.ACTIVITIES, listOf("music")),
            EmojiEntry("💡", EmojiCategory.OBJECTS, listOf("idea", "light")),
            EmojiEntry("📱", EmojiCategory.OBJECTS, listOf("phone", "mobile")),
            EmojiEntry("💻", EmojiCategory.OBJECTS, listOf("computer", "laptop")),
            EmojiEntry("⌚", EmojiCategory.OBJECTS, listOf("watch")),
            EmojiEntry("📷", EmojiCategory.OBJECTS, listOf("camera", "photo")),
            EmojiEntry("🎧", EmojiCategory.OBJECTS, listOf("music")),
            EmojiEntry("🎁", EmojiCategory.OBJECTS, listOf("gift")),
            EmojiEntry("📌", EmojiCategory.OBJECTS, listOf("pin")),
            EmojiEntry("✏", EmojiCategory.OBJECTS, listOf("pencil")),
            EmojiEntry("🔑", EmojiCategory.OBJECTS, listOf("key")),
            EmojiEntry("❤", EmojiCategory.SYMBOLS, listOf("heart", "love")),
            EmojiEntry("💛", EmojiCategory.SYMBOLS, listOf("yellow", "heart", "love")),
            EmojiEntry("💚", EmojiCategory.SYMBOLS, listOf("green", "heart")),
            EmojiEntry("💙", EmojiCategory.SYMBOLS, listOf("blue", "heart")),
            EmojiEntry("💜", EmojiCategory.SYMBOLS, listOf("purple", "heart")),
            EmojiEntry("💔", EmojiCategory.SYMBOLS, listOf("broken", "heart", "sad")),
            EmojiEntry("✨", EmojiCategory.SYMBOLS, listOf("sparkles")),
            EmojiEntry("⭐", EmojiCategory.SYMBOLS, listOf("star")),
            EmojiEntry("🔥", EmojiCategory.SYMBOLS, listOf("fire", "hot")),
            EmojiEntry("💯", EmojiCategory.SYMBOLS, listOf("hundred")),
            EmojiEntry("✅", EmojiCategory.SYMBOLS, listOf("check")),
            EmojiEntry("❌", EmojiCategory.SYMBOLS, listOf("cross")),
            EmojiEntry("⚠", EmojiCategory.SYMBOLS, listOf("warning")),
            EmojiEntry("❓", EmojiCategory.SYMBOLS, listOf("question")),
            EmojiEntry("❗", EmojiCategory.SYMBOLS, listOf("exclamation")),
            EmojiEntry("♻", EmojiCategory.SYMBOLS, listOf("recycle")),
            EmojiEntry("🇳🇵", EmojiCategory.FLAGS, listOf("nepal", "flag")),
            EmojiEntry("🇮🇳", EmojiCategory.FLAGS, listOf("india", "flag")),
            EmojiEntry("🇺🇸", EmojiCategory.FLAGS, listOf("usa", "flag")),
            EmojiEntry("🏳️‍🌈", EmojiCategory.FLAGS, listOf("rainbow", "pride", "flag"))
        )
    }
}

class EmojiSearchIndex(entries: List<EmojiEntry>) {
    private val keywordIndex = hashMapOf<String, LinkedHashSet<String>>()

    init {
        entries.forEach { entry ->
            entry.keywords.forEach { keyword ->
                val key = keyword.lowercase(Locale.ENGLISH)
                if (key.isEmpty()) return@forEach
                for (length in 1..key.length) {
                    val prefix = key.substring(0, length)
                    keywordIndex.getOrPut(prefix) { LinkedHashSet() }.add(entry.glyph)
                }
            }
        }
    }

    fun search(query: String, limit: Int = 40, frequencyOf: (String) -> Int = { 0 }): List<String> {
        val tokens = query.trim().lowercase(Locale.ENGLISH).split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || limit <= 0) return emptyList()
        var matches: Set<String>? = null
        tokens.forEach { token ->
            val found = keywordIndex[token].orEmpty()
            matches = matches?.intersect(found) ?: found
        }
        return (matches ?: emptySet())
            .sortedWith(
                compareByDescending<String> { frequencyOf(it) }
                    .thenByDescending { exactKeywordBonus(it, tokens) }
            )
            .take(limit)
    }

    fun popularKeywords(limit: Int = 12): List<String> =
        listOf("heart", "love", "fire", "dog", "cat", "car", "food", "happy", "sad", "laugh", "sun", "rain", "football")
            .filter { keywordIndex[it].orEmpty().isNotEmpty() }
            .take(limit)

    private fun exactKeywordBonus(glyph: String, tokens: List<String>): Int =
        tokens.count { token -> keywordIndex[token].orEmpty().contains(glyph) }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

class RecentEmojiList(
    initial: List<String> = emptyList(),
    private val limit: Int = DEFAULT_LIMIT,
    private val known: (String) -> Boolean = EmojiCatalog::contains
) {
    private val recent = ArrayList<String>(limit)

    init {
        initial.asSequence()
            .filter(known)
            .distinct()
            .take(limit)
            .forEach(recent::add)
    }

    fun record(emoji: String): String {
        if (!known(emoji)) return emoji
        recent.remove(emoji)
        recent.add(0, emoji)
        while (recent.size > limit) recent.removeAt(recent.lastIndex)
        return emoji
    }

    fun clear() {
        recent.clear()
    }

    fun values(): List<String> = recent.toList()

    fun serialize(): String = recent.joinToString("\n")

    companion object {
        const val DEFAULT_LIMIT = 40

        fun fromSerialized(value: String?, limit: Int = DEFAULT_LIMIT): RecentEmojiList =
            RecentEmojiList(
                value.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
                limit
            )
    }
}

class EmojiUsageStore(
    initial: Map<String, Int> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val scores = linkedMapOf<String, Int>()

    init {
        initial.forEach { (emoji, score) ->
            if (emoji.isNotEmpty() && '\n' !in emoji && '\t' !in emoji) {
                scores[emoji] = score.coerceAtLeast(1)
            }
        }
        trim()
    }

    fun record(emoji: String): Boolean {
        val clean = emoji.trim()
        if (clean.isEmpty() || '\n' in clean || '\t' in clean) return false
        scores[clean] = (scores.remove(clean) ?: 0) + 1
        trim()
        return true
    }

    fun score(emoji: String): Int = scores[emoji] ?: 0

    fun clear() {
        scores.clear()
    }

    fun serialize(): String = scores.entries.joinToString("\n") { (emoji, score) -> "$emoji\t$score" }

    private fun trim() {
        while (scores.size > limit) scores.remove(scores.keys.first())
    }

    companion object {
        const val DEFAULT_LIMIT = 80

        fun fromSerialized(value: String?, limit: Int = DEFAULT_LIMIT): EmojiUsageStore {
            val entries = linkedMapOf<String, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) entries[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
            return EmojiUsageStore(entries, limit)
        }
    }
}

/** Policy for inserting panel characters without corrupting composing text. */
object PanelInsertionPolicy {
    fun shouldFinishComposing(hasComposingText: Boolean): Boolean = hasComposingText || true

    fun romanEditBeforeInsert(composer: RomanInputComposer): RomanEdit {
        if (composer.currentWord.isEmpty()) return RomanEdit.NoOp
        return composer.finishWord()
    }
}
