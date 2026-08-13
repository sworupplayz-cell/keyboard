package com.sworupplayz.keyboard

import java.util.Locale

/**
 * Lightweight offline word families. Used only to generate and rank
 * candidates. It never rewrites committed text.
 */
object Morphology {
    fun englishRelatives(word: String): List<String> {
        val key = word.trim().lowercase(Locale.ENGLISH)
        if (key.isEmpty()) return emptyList()
        ENGLISH_FAMILIES[key]?.let { return it }
        ENGLISH_FAMILIES.values.firstOrNull { key in it }?.let { return it }
        return inferredEnglish(key)
    }

    fun nepaliRelatives(word: String): List<String> {
        val key = word.trim()
        if (key.isEmpty()) return emptyList()
        NEPALI_FAMILIES[key]?.let { return it }
        NEPALI_FAMILIES.values.firstOrNull { key in it }?.let { return it }
        return emptyList()
    }

    fun matchesEnglishFamily(prefix: String, candidate: String): Boolean {
        val relatives = englishRelatives(prefix)
        if (relatives.isEmpty()) return false
        val lower = candidate.trim().lowercase(Locale.ENGLISH)
        return lower in relatives || relatives.any { it.startsWith(prefix.trim().lowercase(Locale.ENGLISH)) && lower == it }
    }

    fun isInflectedEnglish(word: String): Boolean {
        val key = word.trim().lowercase(Locale.ENGLISH)
        val family = ENGLISH_FAMILIES[key] ?: ENGLISH_FAMILIES.values.firstOrNull { key in it } ?: return false
        return family.firstOrNull() != key
    }

    fun isInflectedNepali(word: String): Boolean {
        val key = word.trim()
        val family = NEPALI_FAMILIES[key] ?: NEPALI_FAMILIES.values.firstOrNull { key in it } ?: return false
        return family.firstOrNull() != key
    }

    private fun inferredEnglish(key: String): List<String> {
        val forms = linkedSetOf(key)
        if (key.endsWith("ing") && key.length > 5) forms += key.dropLast(3)
        if (key.endsWith("ed") && key.length > 4) forms += key.dropLast(2)
        if (key.endsWith("es") && key.length > 4) forms += key.dropLast(2)
        if (key.endsWith("s") && !key.endsWith("ss") && key.length > 3) forms += key.dropLast(1)
        return if (forms.size == 1) emptyList() else forms.toList()
    }

    private val ENGLISH_FAMILIES = mapOf(
        "play" to listOf("play", "plays", "played", "playing"),
        "work" to listOf("work", "works", "worked", "working"),
        "go" to listOf("go", "goes", "going", "gone", "went"),
        "run" to listOf("run", "runs", "running", "ran"),
        "make" to listOf("make", "makes", "making", "made"),
        "look" to listOf("look", "looks", "looked", "looking"),
        "call" to listOf("call", "calls", "called", "calling"),
        "need" to listOf("need", "needs", "needed", "needing"),
        "use" to listOf("use", "uses", "used", "using"),
        "try" to listOf("try", "tries", "tried", "trying"),
        "start" to listOf("start", "starts", "started", "starting"),
        "stop" to listOf("stop", "stops", "stopped", "stopping"),
        "wait" to listOf("wait", "waits", "waited", "waiting"),
        "help" to listOf("help", "helps", "helped", "helping"),
        "get" to listOf("get", "gets", "getting", "got"),
        "give" to listOf("give", "gives", "giving", "gave", "given"),
        "take" to listOf("take", "takes", "taking", "took", "taken"),
        "come" to listOf("come", "comes", "coming", "came"),
        "see" to listOf("see", "sees", "seeing", "saw", "seen"),
        "know" to listOf("know", "knows", "knowing", "knew", "known"),
        "think" to listOf("think", "thinks", "thinking", "thought"),
        "want" to listOf("want", "wants", "wanted", "wanting"),
        "say" to listOf("say", "says", "saying", "said"),
        "tell" to listOf("tell", "tells", "telling", "told")
    ).let { families ->
        val expanded = linkedMapOf<String, List<String>>()
        families.forEach { (_, forms) ->
            forms.forEach { form -> expanded[form] = forms }
        }
        expanded
    }

    private val NEPALI_FAMILIES = mapOf(
        "घर" to listOf("घर", "घरमा", "घरको", "घरबाट", "घरलाई"),
        "जानु" to listOf("जानु", "जान", "जान्छ", "जान्छु", "जान्छन्", "जान्छौ", "गयो", "गए", "जाने"),
        "मन" to listOf("मन", "मनपर्छ", "मनपर्ने", "मनमा"),
        "मलाई" to listOf("मलाई", "मलाईको", "मलाईले"),
        "तिमी" to listOf("तिमी", "तिम्रो", "तिमीलाई", "तिमीले"),
        "गर्नु" to listOf("गर्नु", "गर्न", "गर्छु", "गर्छ", "गर्छन्", "गरेको", "गर्दै"),
        "आउनु" to listOf("आउनु", "आउँछु", "आउँछ", "आयो", "आएँ"),
        "खानु" to listOf("खानु", "खाना", "खान्छु", "खायो", "खाएँ")
    ).let { families ->
        val expanded = linkedMapOf<String, List<String>>()
        families.forEach { (_, forms) ->
            forms.forEach { form -> expanded[form] = forms }
        }
        expanded
    }
}
