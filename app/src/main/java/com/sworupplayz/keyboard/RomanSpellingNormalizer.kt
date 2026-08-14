package com.sworupplayz.keyboard

import java.util.Locale

/**
 * Reusable Romanized-Nepali spelling variants.
 *
 * These rules are intentionally general: they collapse playful lengthening,
 * map common consonant alternates, and never hard-code whole example words.
 */
object RomanSpellingNormalizer {
    private val VOWEL_CHARACTERS = setOf('a', 'e', 'i', 'o', 'u')

    fun normalize(value: String): String = value.trim().lowercase(Locale.ENGLISH)

    fun collapseRepeatedVowels(value: String): String {
        val output = StringBuilder(value.length)
        value.forEach { character ->
            if (character !in VOWEL_CHARACTERS || output.lastOrNull() != character) {
                output.append(character)
            }
        }
        return output.toString()
    }

    fun collapseRepeatedLetters(value: String): String {
        val output = StringBuilder(value.length)
        value.forEach { character ->
            if (output.lastOrNull() != character) output.append(character)
        }
        return output.toString()
    }

    /** Dictionary lookup keys derived from one typed Roman word. */
    fun lookupForms(value: String): List<String> {
        val forms = LinkedHashSet<String>()
        val normalized = normalize(value)
        if (normalized.isEmpty()) return emptyList()

        fun add(form: String) {
            if (form.isNotEmpty()) forms += form
        }

        add(normalized)
        add(collapseRepeatedVowels(normalized))
        add(collapseRepeatedLetters(normalized))
        add(collapseRepeatedLetters(collapseRepeatedVowels(normalized)))

        val collapsed = collapseRepeatedLetters(collapseRepeatedVowels(normalized))
        addAllConsonantVariants(collapsed, ::add)

        if (normalized.startsWith("aa") && normalized.length > 2) add("a" + normalized.drop(2))
        if (collapsed.startsWith("aa") && collapsed.length > 2) add("a" + collapsed.drop(2))
        if (normalized.startsWith("a") && !normalized.startsWith("aa") && normalized.length > 1) {
            add("aa" + normalized.drop(1))
        }

        if (normalized.endsWith("ey")) {
            add(normalized.dropLast(1))
            add(normalized.dropLast(2) + "ai")
            add(normalized.dropLast(2) + "e")
        }
        if (normalized.endsWith('y') && normalized.length > 1) {
            add(normalized.dropLast(1) + "i")
        }
        if (normalized.endsWith('h') && normalized.length > 2 && normalized[normalized.lastIndex - 1] in VOWEL_CHARACTERS) {
            add(normalized.dropLast(1))
        }

        if ("ee" in collapsed) add(collapsed.replace("ee", "i"))
        if ("ii" in collapsed) add(collapsed.replace("ii", "ee"))
        if ("oo" in collapsed) add(collapsed.replace("oo", "u"))
        if ("uu" in collapsed) add(collapsed.replace("uu", "oo"))
        if ("ph" in collapsed) add(collapsed.replace("ph", "f"))
        if ('f' in collapsed) add(collapsed.replace("f", "ph"))
        if ("ny" in collapsed) {
            add(collapsed.replace("ny", "n"))
            add(collapsed.replace("ny", "ni"))
        }
        if ("ñ" in normalized) {
            add(normalized.replace("ñ", "n"))
            add(normalized.replace("ñ", "ny"))
        }
        if (collapsed.endsWith("chu") && !collapsed.endsWith("chhu")) {
            add(collapsed.dropLast(3) + "chhu")
        }
        if (collapsed.endsWith("chhu")) add(collapsed.dropLast(4) + "chu")
        if (collapsed.endsWith("xau")) add(collapsed.dropLast(3) + "chau")
        if (collapsed.endsWith("xa")) add(collapsed.dropLast(2) + "cha")
        if (collapsed.endsWith("xu")) add(collapsed.dropLast(2) + "chu")
        if ("xau" in collapsed) add(collapsed.replace("xau", "chau"))
        if ("xa" in collapsed) add(collapsed.replace("xa", "cha"))
        if ("xu" in collapsed) add(collapsed.replace("xu", "chu"))
        if ("aa" in collapsed) add(collapsed.replace("aa", "a"))

        return forms.toList()
    }

    fun phoneticForms(value: String): List<String> {
        val forms = LinkedHashSet<String>()
        val normalized = normalize(value)
        if (normalized.isEmpty()) return emptyList()
        forms += normalized
        forms += collapseRepeatedVowels(normalized)
        forms += collapseRepeatedLetters(normalized)
        forms += collapseRepeatedLetters(collapseRepeatedVowels(normalized))
        return forms.filter { it.isNotEmpty() && it.all(Char::isLetter) }
    }

    private fun addAllConsonantVariants(value: String, add: (String) -> Unit) {
        if ("chh" in value) add(value.replace("chh", "ch"))
        else if ("ch" in value) add(value.replace("ch", "chh"))

        if ("bh" in value) {
            add(value.replace("bh", "v"))
            add(value.replace("bh", "w"))
        }
        if ('v' in value) {
            add(value.replace("v", "bh"))
            add(value.replace("v", "w"))
            add(value.replace("v", "b"))
        }
        if ('w' in value) {
            add(value.replace('w', 'v'))
            add(value.replace("w", "bh"))
            add(value.replace("w", "b"))
        }
        if ("jh" in value) add(value.replace("jh", "j"))
        if ("kh" in value) add(value.replace("kh", "k"))
        if ("gh" in value) add(value.replace("gh", "g"))
        if ("th" in value) add(value.replace("th", "t"))
        if ("dh" in value) add(value.replace("dh", "d"))
        if ("ph" in value) add(value.replace("ph", "p"))
        if ("sh" in value) {
            add(value.replace("sh", "s"))
            add(value.replace("sh", "shh"))
        } else if ('s' in value && "ss" !in value && "sh" !in value) {
            add(value.replaceFirst("s", "sh"))
        }
        if ("bh" in value) add(value.replace("bh", "b"))
    }
}
