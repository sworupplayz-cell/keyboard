package com.sworupplayz.keyboard

/**
 * English handwriting inference contract.
 *
 * Input tensor: float32 [1, 224, 224, 1], values in 0..1.
 * Output tokens: 0 is CTC blank/pad; 1..95 map to printable ASCII 32..126.
 * This decoder never invents text from empty or missing model output.
 */
object EnglishTfliteContract {
    const val BATCH = 1
    const val HEIGHT = InkRasterizer.FRAMEWORK_SIZE
    const val WIDTH = InkRasterizer.FRAMEWORK_SIZE
    const val CHANNELS = 1
    const val INPUT_SIZE = BATCH * HEIGHT * WIDTH * CHANNELS
    const val BLANK = 0
    const val FIRST_PRINTABLE = 32
    const val PRINTABLE_COUNT = 95

    private val inputBuffer = FloatArray(INPUT_SIZE)

    fun packRaster(raster224: FloatArray): FloatArray {
        java.util.Arrays.fill(inputBuffer, 0f)
        val limit = minOf(raster224.size, INPUT_SIZE)
        for (index in 0 until limit) {
            inputBuffer[index] = raster224[index].coerceIn(0f, 1f)
        }
        return inputBuffer
    }

    fun inputShape(): IntArray = intArrayOf(BATCH, HEIGHT, WIDTH, CHANNELS)

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        if (bytes < 1024L) return "${bytes} B"
        if (bytes < 1024L * 1024L) return "${bytes / 1024L} KB"
        return "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}

data class EnglishInkOutput(
    val tokenIds: IntArray,
    val confidences: FloatArray
)

fun interface EnglishInkInterpreter {
    fun infer(input1x224x224x1: FloatArray): EnglishInkOutput?
}

object EnglishTokenDecoder {
    fun decode(output: EnglishInkOutput?): List<HandwritingCandidate> {
        if (output == null || output.tokenIds.isEmpty()) return emptyList()
        val collapsed = ArrayList<Int>()
        val scores = ArrayList<Float>()
        var previous = EnglishTfliteContract.BLANK
        output.tokenIds.forEachIndexed { index, token ->
            if (token == EnglishTfliteContract.BLANK || token == previous) {
                previous = token
                return@forEachIndexed
            }
            previous = token
            collapsed += token
            scores += output.confidences.getOrElse(index) { 0f }
        }
        if (collapsed.isEmpty()) return emptyList()
        val text = buildString {
            collapsed.forEach { token ->
                val printable = token - 1 + EnglishTfliteContract.FIRST_PRINTABLE
                if (printable in EnglishTfliteContract.FIRST_PRINTABLE until
                    (EnglishTfliteContract.FIRST_PRINTABLE + EnglishTfliteContract.PRINTABLE_COUNT)
                ) {
                    append(printable.toChar())
                }
            }
        }.trim()
        if (text.isEmpty()) return emptyList()
        val confidence = if (scores.isEmpty()) 0f else scores.average().toFloat()
        return listOf(HandwritingCandidate(text, confidence, InkLanguage.ENGLISH))
    }

    fun tokensFor(text: String): IntArray =
        text.map { character ->
            val code = character.code
            if (code in EnglishTfliteContract.FIRST_PRINTABLE until
                (EnglishTfliteContract.FIRST_PRINTABLE + EnglishTfliteContract.PRINTABLE_COUNT)
            ) {
                code - EnglishTfliteContract.FIRST_PRINTABLE + 1
            } else {
                EnglishTfliteContract.BLANK
            }
        }.toIntArray()
}

/**
 * One English interpreter for the IME process. Lazy, reusable, closed on destroy.
 * Without a bundled model or TFLite runtime this stays unloaded.
 */
object EnglishTfliteRuntime {
    private val runtime = ReusableInkRuntime<EnglishInkInterpreter>()

    fun isLoaded(): Boolean = runtime.isLoaded()

    fun loadedAsset(): String? = runtime.loadedAsset()

    fun loadEnglishModel(
        assetName: String,
        factory: () -> EnglishInkInterpreter?
    ): Boolean = runtime.load(assetName, factory)

    fun unloadEnglishModel() {
        runtime.unload()
    }

    fun recognizeEnglish(raster224: FloatArray): HandwritingResult {
        val active = runtime.get() ?: return HandwritingResult.UNAVAILABLE
        val packed = EnglishTfliteContract.packRaster(raster224)
        val decoded = EnglishTokenDecoder.decode(active.infer(packed))
        val merged = HandwritingCandidateMerger.merge(decoded)
        if (merged.isEmpty()) return HandwritingResult.UNAVAILABLE
        return HandwritingResult.Success(merged)
    }
}
