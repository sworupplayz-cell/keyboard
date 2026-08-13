package com.sworupplayz.keyboard

/**
 * Looks only for locally bundled English and Nepali TFLite files.
 * Missing files are Missing, never downloaded, never invented.
 */
class HandwritingModelManager(
    private val assetNames: Collection<String> = emptyList()
) {
    val englishInstalled: Boolean = isPresent(ENGLISH_ASSET)
    val nepaliInstalled: Boolean = isPresent(NEPALI_ASSET)

    fun englishStatus(): String = if (englishInstalled) INSTALLED else MISSING

    fun nepaliStatus(): String = if (nepaliInstalled) INSTALLED else MISSING

    fun recognitionLanguage(): String = "Auto"

    fun canRecognize(language: InkLanguage): Boolean = when (language) {
        InkLanguage.ENGLISH -> englishInstalled
        InkLanguage.DEVANAGARI -> nepaliInstalled
        InkLanguage.UNKNOWN -> englishInstalled || nepaliInstalled
    }

    fun recognize(ink: HandwritingInk, language: InkLanguage): HandwritingResult {
        ink
        if (!canRecognize(language)) return HandwritingResult.UNAVAILABLE
        return HandwritingResult.UNAVAILABLE
    }

    private fun isPresent(fileName: String): Boolean =
        assetNames.any { it.substringAfterLast('/') == fileName }

    companion object {
        const val ENGLISH_ASSET = "english.tflite"
        const val NEPALI_ASSET = "nepali.tflite"
        const val INSTALLED = "Installed"
        const val MISSING = "Missing"

        fun fromNames(assetNames: Collection<String>): HandwritingModelManager =
            HandwritingModelManager(assetNames)
    }
}
