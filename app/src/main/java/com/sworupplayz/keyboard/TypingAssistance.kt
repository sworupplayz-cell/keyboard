package com.sworupplayz.keyboard

import java.io.Reader
import java.util.Locale

/** Small indexed prefix dictionary with conservative one-edit typo candidates. */
class LocalWordSuggester private constructor(words: List<String>) {
    private val vocabulary = words.map(String::trim).filter(String::isNotEmpty).distinct()
    private val knownWords = vocabulary.map(::normalize).toHashSet()
    private val frequencyRank = linkedMapOf<String, Int>().apply {
        vocabulary.forEachIndexed { index, word ->
            val normalized = normalize(word)
            if (normalized !in this) this[normalized] = index
        }
    }
    private val prefixIndex = linkedMapOf<String, List<String>>()
    private val deletionIndex = linkedMapOf<String, List<String>>()
    private val substitutionIndex = linkedMapOf<String, List<String>>()
    private val suggestionCache = object : LinkedHashMap<String, List<String>>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean =
            size > PREFIX_CACHE_LIMIT
    }

    init {
        val prefixBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        val deletionBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        val substitutionBuckets = linkedMapOf<String, LinkedHashSet<String>>()
        vocabulary.forEach { word ->
            val normalized = normalize(word)
            val prefixForms = linkedSetOf(normalized, normalized.filter { it != '\'' })
            prefixForms.forEach { form ->
                for (length in 1..form.length) {
                    prefixBuckets.getOrPut(form.substring(0, length)) { LinkedHashSet() }
                        .addBounded(word)
                }
            }
            if (normalized.length >= MIN_TYPO_LENGTH) {
                val isLatinWord = normalized.all { it in 'a'..'z' }
                normalized.indices.forEach { index ->
                    if (!isLatinWord || normalized[index] in LATIN_VOWELS) {
                        deletionBuckets.getOrPut(normalized.removeRange(index, index + 1)) { LinkedHashSet() }
                            .addBounded(word)
                    }
                    substitutionBuckets.getOrPut(normalized.replaceRange(index, index + 1, "*")) { LinkedHashSet() }
                        .addBounded(word)
                }
            }
        }
        prefixBuckets.forEach { (key, values) -> prefixIndex[key] = values.toList() }
        deletionBuckets.forEach { (key, values) -> deletionIndex[key] = values.toList() }
        substitutionBuckets.forEach { (key, values) -> substitutionIndex[key] = values.toList() }
    }

    fun contains(word: String): Boolean = normalize(word) in knownWords

    fun rankOf(word: String): Int = frequencyRank[normalize(word)] ?: Int.MAX_VALUE

    fun suggestions(
        input: String,
        learned: List<String> = emptyList(),
        limit: Int = 3,
        recent: List<String> = emptyList(),
        contextPredictions: List<String> = emptyList()
    ): List<String> {
        val normalized = normalize(input)
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val cacheKey = if (learned.isEmpty() && recent.isEmpty() && contextPredictions.isEmpty()) {
            "$normalized|$limit"
        } else {
            null
        }
        cacheKey?.let { key -> suggestionCache[key]?.let { return it } }

        val prefixMatches = prefixIndex[normalized].orEmpty()
        val typoMatches = ArrayList<String>()
        if (prefixMatches.size < 2 && normalized.length >= MIN_TYPO_LENGTH) {
            val collapsed = collapseRepeatedLetters(normalized)
            if (collapsed != normalized) typoMatches += prefixIndex[collapsed].orEmpty()
            typoMatches += deletionIndex[normalized].orEmpty()
            normalized.indices.forEach { index ->
                substitutionIndex[normalized.replaceRange(index, index + 1, "*")]
                    .orEmpty()
                    .filter { candidate -> hasSingleNearbyKeySubstitution(normalized, normalize(candidate)) }
                    .forEach(typoMatches::add)
            }
        }

        if (prefixMatches.size < 2 && normalized.length >= MIN_TYPO_LENGTH) {
            TypoCorrector.extraCandidates(normalized, ::contains).forEach(typoMatches::add)
        }

        val ranked = SuggestionRanker.rank(
            input = input,
            prefixMatches = prefixMatches,
            typoMatches = typoMatches,
            learned = learned,
            recent = recent,
            frequencyOf = { word -> frequencyRank[normalize(word)] ?: Int.MAX_VALUE },
            limit = limit,
            contextMatches = contextPredictions,
            morphologyMatches = if (normalized.any { it in 'a'..'z' }) {
                Morphology.englishRelatives(input).filter { contains(it) }
            } else {
                emptyList()
            }
        )
        cacheKey?.let { suggestionCache[it] = ranked }
        return ranked
    }

    companion object {
        fun fromWords(words: List<String>): LocalWordSuggester = LocalWordSuggester(words)

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

        private fun collapseRepeatedLetters(value: String): String {
            val output = StringBuilder(value.length)
            value.forEach { character -> if (output.lastOrNull() != character) output.append(character) }
            return output.toString()
        }

        private fun hasSingleNearbyKeySubstitution(first: String, second: String): Boolean {
            if (first.length != second.length) return false
            var mismatch = -1
            first.indices.forEach { index ->
                if (first[index] != second[index]) {
                    if (mismatch >= 0) return false
                    mismatch = index
                }
            }
            if (mismatch < 0) return false
            return second[mismatch] in QWERTY_NEIGHBORS[first[mismatch]].orEmpty()
        }

        private fun LinkedHashSet<String>.addBounded(value: String) {
            if (size < INDEX_CANDIDATE_LIMIT) add(value)
        }

        private const val LATIN_VOWELS = "aeiou"
        private val QWERTY_NEIGHBORS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
            'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "ersfxc", 'f' to "rtdgcv",
            'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uihknm", 'k' to "iojlm",
            'l' to "opk", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
            'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )
        const val PREFIX_CACHE_LIMIT = 64
        private const val INDEX_CANDIDATE_LIMIT = 8
        private const val MIN_TYPO_LENGTH = 3
    }

    fun cacheSize(): Int = suggestionCache.size
}

class LearnedWordStore(
    initial: Map<String, Int> = emptyMap(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val scores = linkedMapOf<String, Int>()

    init {
        initial.forEach { (word, score) -> if (isValid(word)) scores[word] = score.coerceAtLeast(1) }
        trimToLimit()
    }

    fun record(word: String): Boolean {
        val clean = word.trim()
        if (!isValid(clean) || WordLearningPolicy.isGarbage(clean)) return false
        val score = (scores.remove(clean) ?: 0) + 1
        scores[clean] = score
        trimToLimit()
        return true
    }

    fun scoreOf(word: String): Int = scores[word.trim()] ?: 0

    fun size(): Int = scores.size

    fun recencyRank(word: String): Int {
        val clean = word.trim()
        if (clean.isEmpty()) return Int.MAX_VALUE
        val keys = scores.keys.toList()
        val index = keys.indexOfLast { it.equals(clean, ignoreCase = true) }
        if (index < 0) return Int.MAX_VALUE
        return keys.lastIndex - index
    }

    fun decayedScore(word: String): Int {
        val usage = scoreOf(word)
        if (usage <= 0) return 0
        val recencyBonus = (24 - recencyRank(word)).coerceAtLeast(0)
        return (usage + recencyBonus / 4).coerceAtMost(PredictionScores.PERSONAL_USAGE_CAP)
    }

    fun frequencyMap(prefix: String, limit: Int = 6): Map<String, Int> =
        suggestions(prefix, limit).associateWith { decayedScore(it) }

    fun suggestions(prefix: String, limit: Int = 6): List<String> {
        val normalized = prefix.lowercase(Locale.ENGLISH)
        return scores.entries.toList().asReversed()
            .filter { it.key.lowercase(Locale.ENGLISH).startsWith(normalized) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun serialize(): String = scores.entries.joinToString("\n") { (word, score) -> "$word\t$score" }

    private fun trimToLimit() {
        while (scores.size > limit) scores.remove(scores.keys.first())
    }

    private fun isValid(value: String): Boolean = value.isNotEmpty() &&
        '\n' !in value && '\t' !in value && value.length <= MAX_WORD_LENGTH

    companion object {
        const val DEFAULT_LIMIT = 250
        private const val MAX_WORD_LENGTH = 48

        fun fromSerialized(value: String?): LearnedWordStore {
            val entries = linkedMapOf<String, Int>()
            value.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) entries[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
            return LearnedWordStore(entries)
        }
    }
}

enum class DirectTypingLanguage { ENGLISH, NEPALI }

class DirectTypingState {
    var currentWord: String = ""
        private set

    fun append(text: String, language: DirectTypingLanguage): Boolean {
        val isWordText = when (language) {
            DirectTypingLanguage.ENGLISH -> text.isNotEmpty() && text.all(WordBoundaryPolicy::isEnglishWordContinue)
            DirectTypingLanguage.NEPALI -> isNepaliWordText(text)
        }
        if (!isWordText) {
            clear()
            return false
        }
        currentWord += text
        return true
    }

    fun backspace(): Boolean {
        if (currentWord.isEmpty()) return false
        currentWord = GraphemeBackspace.apply(currentWord)
        return true
    }

    fun replaceWith(word: String) {
        currentWord = word
    }

    fun clear() {
        currentWord = ""
    }

    fun codePointCount(): Int = currentWord.codePointCount(0, currentWord.length)

    private fun isNepaliWordText(text: String): Boolean {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            if (codePoint !in DEVANAGARI_RANGE ||
                codePoint in NEPALI_BOUNDARIES ||
                codePoint in NEPALI_DIGITS
            ) return false
            offset += Character.charCount(codePoint)
        }
        return text.isNotEmpty()
    }

    companion object {
        private val DEVANAGARI_RANGE = 0x0900..0x097F
        private val NEPALI_DIGITS = 0x0966..0x096F
        private val NEPALI_BOUNDARIES = setOf(0x0964, 0x0965, 0x0970)
    }
}

data class SuggestionReplacement(
    val deleteCodePoints: Int,
    val deleteCodeUnits: Int,
    val replacement: String,
    val changesText: Boolean,
    val deleteAfterCodeUnits: Int = 0
)

object SuggestionSelectionPlan {
    fun matchesCurrentWord(textBeforeCursor: String, currentWord: String): Boolean {
        if (currentWord.isEmpty() || !textBeforeCursor.endsWith(currentWord)) return false
        if (textBeforeCursor.length == currentWord.length) return true
        val boundary = textBeforeCursor[textBeforeCursor.length - currentWord.length - 1]
        return !WordBoundaryPolicy.isWordChar(boundary)
    }

    fun create(
        currentWord: String,
        suggestion: String,
        textBeforeCursor: String,
        textAfterCursor: String = ""
    ): SuggestionReplacement? {
        val trackedMatches = matchesCurrentWord(textBeforeCursor, currentWord)
        val editorWord = WordBoundaryPolicy.wordBeforeCursor(textBeforeCursor)
        val word = when {
            trackedMatches -> currentWord
            currentWord.isEmpty() && editorWord.isNotEmpty() -> editorWord
            else -> return null
        }
        if (WordBoundaryPolicy.blocksSuggestionReplacement(textBeforeCursor, word)) return null
        val after = WordBoundaryPolicy.wordAfterCursor(textAfterCursor)
        val replacement = preserveCapitalization(word, suggestion)
        val typed = word + after
        return SuggestionReplacement(
            deleteCodePoints = word.codePointCount(0, word.length),
            deleteCodeUnits = word.length,
            replacement = replacement,
            changesText = typed != replacement,
            deleteAfterCodeUnits = after.length
        )
    }

    fun preserveCapitalization(typed: String, suggestion: String): String {
        if (typed.isEmpty() || suggestion.isEmpty()) return suggestion
        if (typed.all { it.isUpperCase() || !it.isLetter() }) return suggestion.uppercase(Locale.ENGLISH)
        if (typed.first().isUpperCase()) {
            return suggestion.replaceFirstChar { it.uppercaseChar() }
        }
        return suggestion
    }
}

object SuggestionRanker {
    fun rank(
        input: String,
        prefixMatches: List<String>,
        typoMatches: List<String>,
        learned: List<String>,
        recent: List<String>,
        frequencyOf: (String) -> Int,
        limit: Int,
        contextMatches: List<String> = emptyList(),
        morphologyMatches: List<String> = emptyList(),
        personalFrequencyOf: (String) -> Int = { 0 },
        trigramMatches: List<String> = emptyList(),
        bigramMatches: List<String> = emptyList(),
        contractionMatches: List<String> = emptyList(),
        phoneticMatches: List<String> = emptyList(),
        categoryOf: (String) -> VocabularyCategory = { VocabularyCategory.CORE }
    ): List<String> {
        val normalizedInput = input.trim().lowercase(Locale.ENGLISH)
        if (normalizedInput.isEmpty() || limit <= 0) return emptyList()

        val scored = linkedMapOf<String, Pair<String, Int>>()
        fun consider(display: String, score: Int) {
            val trimmed = display.trim()
            if (trimmed.isEmpty() || PredictionPipeline.isBlocked(trimmed)) return
            val normalized = trimmed.lowercase(Locale.ENGLISH)
            val existing = scored[normalized]
            if (existing == null || score > existing.second) {
                scored[normalized] = trimmed to score
            }
        }

        learned.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                val personal = personalFrequencyOf(word)
                    .coerceAtMost(PredictionScores.PERSONAL_USAGE_CAP) * 8
                consider(
                    word,
                    (PredictionScores.LEARNED - index * 20 + personal)
                        .coerceAtMost(PredictionScores.LEARNED_CAP)
                )
            }
        }
        trigramMatches.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                consider(
                    word,
                    (PredictionScores.TRIGRAM - index * 20).coerceAtLeast(PredictionScores.TRIGRAM_FLOOR)
                )
            }
        }
        recent.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                consider(
                    word,
                    (PredictionScores.RECENT - index * 10).coerceAtLeast(PredictionScores.RECENT_FLOOR)
                )
            }
        }
        bigramMatches.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                consider(
                    word,
                    (PredictionScores.BIGRAM - index * 15).coerceAtLeast(PredictionScores.BIGRAM_FLOOR)
                )
            }
        }
        contextMatches.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                consider(
                    word,
                    (PredictionScores.CONTEXT - index * 15).coerceAtLeast(PredictionScores.CONTEXT_FLOOR)
                )
            }
        }
        contractionMatches.forEach { word ->
            if (matchesRankedInput(word.replace("'", ""), normalizedInput) ||
                matchesRankedInput(word, normalizedInput)
            ) {
                consider(word, PredictionScores.CONTRACTION)
            }
        }
        prefixMatches.forEach { word ->
            val exact = if (word.trim().lowercase(Locale.ENGLISH) == normalizedInput) {
                PredictionScores.EXACT
            } else {
                0
            }
            val penalty = if (VocabularyCatalog.shouldStayBehindCore(categoryOf(word))) {
                PredictionScores.CATEGORY_PENALTY
            } else {
                0
            }
            consider(
                word,
                exact + PredictionScores.PREFIX - frequencyOf(word).coerceAtMost(PredictionScores.PREFIX_FREQ_CAP) - penalty
            )
        }
        morphologyMatches.forEachIndexed { index, word ->
            if (matchesRankedInput(word, normalizedInput)) {
                consider(
                    word,
                    (PredictionScores.MORPHOLOGY - index * 8).coerceAtLeast(PredictionScores.MORPHOLOGY_FLOOR)
                )
            }
        }
        typoMatches.forEach { word ->
            val common = if (TypoCorrector.commonCorrections(input).any { it.equals(word, ignoreCase = true) }) {
                PredictionScores.COMMON_TYPO
            } else {
                PredictionScores.TYPO
            }
            consider(word, (common - frequencyOf(word).coerceAtMost(50)).coerceAtLeast(PredictionScores.TYPO_FLOOR))
        }
        phoneticMatches.forEach { word ->
            consider(word, PredictionScores.PHONETIC)
        }
        if (scored.keys.none { it == normalizedInput }) {
            consider(input, PredictionScores.TYPED)
        }

        return PredictionPipeline.diversify(
            scored.values
                .sortedWith(
                    compareByDescending<Pair<String, Int>> { it.second }
                        .thenBy { frequencyOf(it.first) }
                )
                .map { formatLikeInput(input, it.first) },
            limit
        )
    }

    private fun formatLikeInput(input: String, candidate: String): String {
        if (candidate.any { it.code in DEVANAGARI_RANGE } || candidate.any { !it.isLetter() && it != '\'' }) {
            return candidate
        }
        if (CapitalizationPolicy.isProperNoun(candidate) && input.none { it.isUpperCase() }) {
            return candidate
        }
        val letters = input.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
            return candidate.uppercase(Locale.ENGLISH)
        }
        if (input.firstOrNull()?.isUpperCase() == true) {
            return candidate.replaceFirstChar { it.uppercaseChar() }
        }
        return candidate
    }

    private fun matchesRankedInput(word: String, normalizedInput: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.lowercase(Locale.ENGLISH).startsWith(normalizedInput)) return true
        return isDevanagariWord(trimmed) && normalizedInput.all { it in 'a'..'z' }
    }

    private fun isDevanagariWord(value: String): Boolean =
        value.any { it.code in DEVANAGARI_RANGE }

    private val DEVANAGARI_RANGE = 0x0900..0x097F
}

class RecentWordStore(
    initial: List<String> = emptyList(),
    private val limit: Int = DEFAULT_LIMIT
) {
    private val words = ArrayList<String>(limit)

    init {
        initial.forEach { record(it) }
    }

    fun record(word: String): Boolean {
        val clean = word.trim()
        if (clean.isEmpty() || '\n' in clean || '\t' in clean || clean.length > MAX_WORD_LENGTH) return false
        words.removeAll { it.equals(clean, ignoreCase = true) }
        words.add(0, clean)
        while (words.size > limit) words.removeAt(words.lastIndex)
        return true
    }

    fun matches(prefix: String, limit: Int = 6): List<String> {
        val normalized = prefix.trim().lowercase(Locale.ENGLISH)
        if (normalized.isEmpty()) return words.take(limit)
        return words.filter { it.lowercase(Locale.ENGLISH).startsWith(normalized) }.take(limit)
    }

    fun values(): List<String> = words.toList()

    fun serialize(): String = words.joinToString("\n")

    companion object {
        const val DEFAULT_LIMIT = 60
        private const val MAX_WORD_LENGTH = 48

        fun fromSerialized(value: String?): RecentWordStore =
            RecentWordStore(value.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
    }
}

object WordLearningPolicy {
    const val MIN_TEACHABLE_LENGTH = 3
    const val REPEATS_TO_LEARN = 2

    fun shouldLearnUnknown(word: String, known: (String) -> Boolean): Boolean {
        val clean = word.trim()
        return !isGarbage(clean) && !known(clean)
    }

    fun shouldLearnAccepted(word: String): Boolean = !isGarbage(word.trim())

    fun shouldLearnRepeated(word: String, finishCount: Int, known: (String) -> Boolean): Boolean =
        finishCount >= REPEATS_TO_LEARN && shouldLearnUnknown(word, known)

    fun isGarbage(word: String): Boolean {
        val clean = word.trim()
        if (clean.length < MIN_TEACHABLE_LENGTH) return true
        if (clean.all { it == clean.first() }) return true
        if (clean.none { it.isLetter() || it.code in 0x0900..0x097F }) return true
        if (SpecialTokenPolicy.looksLikeUrl(clean) || SpecialTokenPolicy.looksLikeEmail(clean)) return true
        if (SpecialTokenPolicy.looksLikeMentionOrHashtag(clean)) return true
        if (SpecialTokenPolicy.looksLikeNumber(clean)) return true
        if (ClipboardPolicy.looksSensitive(clean)) return true
        if (looksLikeRandomToken(clean)) return true
        if (looksLikeCreditCard(clean)) return true
        if (clean.takeLastWhile { it.isDigit() }.length >= 3) return true
        return false
    }

    private fun looksLikeRandomToken(word: String): Boolean {
        if (word.length < 16) return false
        if (word.any { it.isWhitespace() }) return false
        val letters = word.count { it.isLetter() }
        val digits = word.count { it.isDigit() }
        if (letters == 0 || digits < 4) return false
        val vowels = word.count { it.lowercaseChar() in "aeiou" }
        return vowels <= word.length / 8
    }

    private fun looksLikeCreditCard(word: String): Boolean {
        val digits = word.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        return word.none { it.isLetter() }
    }
}

object VocabularyLoader {
    fun english(reader: Reader): List<String> = loadWordList(reader)

    fun nepali(reader: Reader): List<String> = loadWordList(reader)

    fun entries(reader: Reader, language: VocabularyLanguage): List<VocabularyEntry> {
        var rank = 0
        return reader.buffered().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .mapNotNull { line ->
                    val columns = line.split('\t').map(String::trim)
                    val word = columns.firstOrNull().orEmpty()
                    if (word.isEmpty()) return@mapNotNull null
                    val category = columns.getOrNull(1)?.let { raw ->
                        VocabularyCategory.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                    } ?: VocabularyCatalog.classify(word, language)
                    val stem = columns.getOrNull(2)?.takeIf { it.isNotEmpty() }
                    val alternates = columns.getOrNull(3)
                        ?.split('|')
                        ?.map(String::trim)
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
                    VocabularyEntry(
                        word = word,
                        rank = rank++,
                        language = language,
                        alternates = alternates,
                        category = category,
                        stem = stem
                    )
                }
                .toList()
        }
    }

    fun mergeDistinct(first: List<String>, second: List<String>): List<String> {
        val words = LinkedHashSet<String>()
        first.forEach { words.add(it) }
        second.forEach { words.add(it) }
        return words.toList()
    }

    fun nepaliFromRomanDictionary(reader: Reader): List<String> {
        val words = LinkedHashSet<String>()
        reader.buffered().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEach
                val value = trimmed.split('\t', limit = 2).getOrNull(1) ?: return@forEach
                value.split('|').map(String::trim)
                    .filter { it.isNotEmpty() && ' ' !in it }
                    .forEach(words::add)
            }
        }
        return words.toList()
    }

    private fun loadWordList(reader: Reader): List<String> = reader.buffered().useLines { lines ->
        lines.map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .map { line -> line.split('\t', limit = 2).first().trim() }
            .filter(String::isNotEmpty)
            .toList()
    }
}
