package com.sworupplayz.keyboard

/**
 * Looks only for locally bundled English and Nepali TFLite files.
 * Missing files are Missing, never downloaded, never invented.
 */
class HandwritingModelManager(
    private val assetNames: Collection<String> = emptyList(),
    private val assetSizes: Map<String, Long> = emptyMap(),
    private val englishFactory: () -> EnglishInkInterpreter? = { null }
) {
    val englishInstalled: Boolean = isPresent(ENGLISH_ASSET)
    val nepaliInstalled: Boolean = isPresent(NEPALI_ASSET)
    val englishBytes: Long = sizeOf(ENGLISH_ASSET)

    fun englishStatus(): String = if (englishInstalled) INSTALLED else MISSING

    fun nepaliStatus(): String = if (nepaliInstalled) INSTALLED else MISSING

    fun englishDetailStatus(): String {
        if (!englishInstalled) return MISSING
        val formatted = EnglishTfliteContract.formatSize(englishBytes)
        return if (formatted.isEmpty()) INSTALLED else "$INSTALLED ($formatted)"
    }

    fun recognitionLanguage(): String = "Auto"

    fun canRecognize(language: InkLanguage): Boolean = when (language) {
        InkLanguage.ENGLISH -> englishInstalled
        InkLanguage.DEVANAGARI -> nepaliInstalled
        InkLanguage.UNKNOWN -> englishInstalled || nepaliInstalled
    }

    fun loadEnglishModel(): Boolean {
        if (!englishInstalled) return false
        return EnglishTfliteRuntime.loadEnglishModel(ENGLISH_ASSET, englishFactory)
    }

    fun unloadEnglishModel() {
        EnglishTfliteRuntime.unloadEnglishModel()
    }

    fun recognizeEnglish(raster224: FloatArray): HandwritingResult {
        if (!englishInstalled) return HandwritingResult.UNAVAILABLE
        if (!EnglishTfliteRuntime.isLoaded() && !loadEnglishModel()) {
            return HandwritingResult.UNAVAILABLE
        }
        return EnglishTfliteRuntime.recognizeEnglish(raster224)
    }

    fun recognize(ink: HandwritingInk, language: InkLanguage): HandwritingResult {
        if (language == InkLanguage.DEVANAGARI && !englishInstalled) {
            return HandwritingResult.UNAVAILABLE
        }
        if (!canRecognize(language) && language != InkLanguage.ENGLISH) {
            return HandwritingResult.UNAVAILABLE
        }
        if (language == InkLanguage.ENGLISH || language == InkLanguage.UNKNOWN) {
            val raster = InkRasterizer.rasterizeFramework(ink)
            return recognizeEnglish(raster).copy(detectedLanguage = language)
        }
        return HandwritingResult.UNAVAILABLE
    }

    private fun isPresent(fileName: String): Boolean =
        assetNames.any { it.substringAfterLast('/') == fileName }

    private fun sizeOf(fileName: String): Long =
        assetSizes[fileName] ?: assetSizes.entries.firstOrNull { (key, _) ->
            key.substringAfterLast('/') == fileName
        }?.value ?: 0L

    companion object {
        const val ENGLISH_ASSET = "english.tflite"
        const val NEPALI_ASSET = "nepali.tflite"
        const val INSTALLED = "Installed"
        const val MISSING = "Missing"

        fun fromNames(assetNames: Collection<String>): HandwritingModelManager =
            HandwritingModelManager(assetNames)
    }
}
