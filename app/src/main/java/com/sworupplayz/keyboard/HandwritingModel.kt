package com.sworupplayz.keyboard

/**
 * Loading boundary for a future bundled offline model.
 *
 * This build does not ship a TFLite/ONNX weight file. The catalog exists so a
 * legally licensed model can be dropped into assets later without inventing a
 * second recognition architecture.
 */
object HandwritingModelSpec {
    const val DEVANAGARI_ASSET = "handwriting_devanagari.tflite"
    const val ENGLISH_ASSET = "handwriting_english.tflite"
    const val FORMAT = "tflite-int8"
    const val INPUT_WIDTH = HandwritingPreprocessor.RASTER_SIZE
    const val INPUT_HEIGHT = HandwritingPreprocessor.RASTER_SIZE
    const val MAX_CANDIDATES = 5
    const val MIN_CONFIDENCE = 0.18f

    fun expectedAssets(): Set<String> = setOf(DEVANAGARI_ASSET, ENGLISH_ASSET)

    fun isBundled(assetNames: Collection<String>): Boolean =
        assetNames.any { name ->
            val file = name.substringAfterLast('/')
            file == DEVANAGARI_ASSET || file == ENGLISH_ASSET
        }

    fun assetFor(language: KeyboardLanguage): String = when (language) {
        KeyboardLanguage.ENGLISH -> ENGLISH_ASSET
        KeyboardLanguage.NEPALI, KeyboardLanguage.ROMAN -> DEVANAGARI_ASSET
    }

    fun reasonUnavailable(assetNames: Collection<String>): String {
        if (isBundled(assetNames)) return ""
        return "No bundled offline handwriting model"
    }
}

data class RecognitionCandidate(
    val text: String,
    val confidence: Float
)

data class RecognitionResult(
    val candidates: List<RecognitionCandidate>,
    val status: HandwritingStatus,
    val generation: Int = 0
)

/**
 * Reads only local asset names. Never downloads. A missing file is unavailable,
 * not a fake character map.
 */
class HandwritingModelLoader(
    private val assetNames: Collection<String> = emptyList()
) {
    val isAvailable: Boolean = HandwritingModelSpec.isBundled(assetNames)

    fun unavailableReason(): String = HandwritingModelSpec.reasonUnavailable(assetNames)

    fun load(language: KeyboardLanguage): HandwritingModelHandle? {
        if (!isAvailable) return null
        val name = HandwritingModelSpec.assetFor(language)
        if (assetNames.none { it.substringAfterLast('/') == name }) return null
        return HandwritingModelHandle(name, language)
    }
}

/**
 * Opaque handle for a bundled model file. Inference is not implemented until a
 * real weight file is present; constructing this without [HandwritingModelLoader]
 * is not a working recognizer.
 */
data class HandwritingModelHandle(
    val assetName: String,
    val language: KeyboardLanguage
)

object BundledHandwritingInterpreter {
    fun infer(
        handle: HandwritingModelHandle?,
        prepared: PreparedHandwriting
    ): RecognitionResult {
        handle
        prepared
        return RecognitionResult(emptyList(), HandwritingStatus.RECOGNIZER_UNAVAILABLE)
    }
}
