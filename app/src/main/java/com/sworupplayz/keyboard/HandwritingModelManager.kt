package com.sworupplayz.keyboard

/**
 * Looks only for locally bundled English and Nepali TFLite files.
 * Missing files are Missing, never downloaded, never invented.
 */
class HandwritingModelManager(
    private val assetNames: Collection<String> = emptyList(),
    private val assetSizes: Map<String, Long> = emptyMap(),
    private val englishFactory: () -> EnglishInkInterpreter? = { null },
    private val nepaliFactory: () -> NepaliInkInterpreter? = { null }
) {
    val englishInstalled: Boolean = isPresent(ENGLISH_ASSET)
    val nepaliInstalled: Boolean = isPresent(NEPALI_ASSET)
    val englishBytes: Long = sizeOf(ENGLISH_ASSET)
    val nepaliBytes: Long = sizeOf(NEPALI_ASSET)

    fun englishStatus(): String = if (englishInstalled) INSTALLED else MISSING

    fun nepaliStatus(): String = if (nepaliInstalled) INSTALLED else MISSING

    fun englishDetailStatus(): String = detailStatus(englishInstalled, englishBytes)

    fun nepaliDetailStatus(): String = detailStatus(nepaliInstalled, nepaliBytes)

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

    fun loadNepaliModel(): Boolean {
        if (!nepaliInstalled) return false
        return NepaliTfliteRuntime.loadNepaliModel(NEPALI_ASSET, nepaliFactory)
    }

    fun unloadNepaliModel() {
        NepaliTfliteRuntime.unloadNepaliModel()
    }

    fun recognizeNepali(raster224: FloatArray): HandwritingResult {
        if (!nepaliInstalled) return HandwritingResult.UNAVAILABLE
        if (!NepaliTfliteRuntime.isLoaded() && !loadNepaliModel()) {
            return HandwritingResult.UNAVAILABLE
        }
        return NepaliTfliteRuntime.recognizeNepali(raster224)
    }

    fun recognize(ink: HandwritingInk, language: InkLanguage): HandwritingResult {
        when (language) {
            InkLanguage.DEVANAGARI -> {
                if (!nepaliInstalled) return HandwritingResult.UNAVAILABLE
                val raster = InkRasterizer.rasterizeFramework(ink)
                return keepSingleton(recognizeNepali(raster), language)
            }
            InkLanguage.ENGLISH -> {
                if (!englishInstalled) return HandwritingResult.UNAVAILABLE
                val raster = InkRasterizer.rasterizeFramework(ink)
                return keepSingleton(recognizeEnglish(raster), language)
            }
            InkLanguage.UNKNOWN -> {
                if (!englishInstalled && !nepaliInstalled) return HandwritingResult.UNAVAILABLE
                val raster = InkRasterizer.rasterizeFramework(ink)
                if (englishInstalled) {
                    val english = recognizeEnglish(raster)
                    if (hasVisibleResults(english)) {
                        return keepSingleton(english, language)
                    }
                }
                if (nepaliInstalled) {
                    return keepSingleton(recognizeNepali(raster), language)
                }
                return HandwritingResult.UNAVAILABLE
            }
        }
    }

    private fun hasVisibleResults(result: HandwritingResult): Boolean =
        result.status == HandwritingStatus.RESULTS && result.candidates.isNotEmpty()

    private fun keepSingleton(result: HandwritingResult, language: InkLanguage): HandwritingResult {
        if (result === HandwritingResult.UNAVAILABLE ||
            result === HandwritingResult.BLOCKED ||
            result === HandwritingResult.EMPTY ||
            result === HandwritingResult.RECOGNIZING
        ) {
            return result
        }
        if (result.status == HandwritingStatus.RECOGNIZER_UNAVAILABLE && result.candidates.isEmpty()) {
            return HandwritingResult.UNAVAILABLE
        }
        return result.copy(detectedLanguage = language)
    }

    private fun detailStatus(installed: Boolean, bytes: Long): String {
        if (!installed) return MISSING
        val formatted = EnglishTfliteContract.formatSize(bytes)
        return if (formatted.isEmpty()) INSTALLED else "$INSTALLED ($formatted)"
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
