package com.sworupplayz.keyboard

/**
 * Nepali / Devanagari handwriting inference contract.
 *
 * Input tensor is the same reused 1×224×224×1 float32 packer as English.
 * Output tokens: 0 is CTC blank/pad; 1..N map only to Devanagari glyphs.
 * Unknown tokens are dropped, never replaced with Latin or placeholders.
 */
object NepaliTfliteContract {
    const val BATCH = EnglishTfliteContract.BATCH
    const val HEIGHT = EnglishTfliteContract.HEIGHT
    const val WIDTH = EnglishTfliteContract.WIDTH
    const val CHANNELS = EnglishTfliteContract.CHANNELS
    const val INPUT_SIZE = EnglishTfliteContract.INPUT_SIZE
    const val BLANK = 0

    /**
     * Token 1 maps to the first glyph. Covers consonants, independent vowels,
     * matras, halant, anusvara, chandrabindu, visarga, nukta, danda, and digits.
     */
    val GLYPHS: String = buildString {
        append('\u0901') // chandrabindu ँ
        append('\u0902') // anusvara ं
        append('\u0903') // visarga ः
        append("अआइईउऊऋऌऍऎएऐऑऒओऔ")
        append("कखगघङचछजझञटठडढणतथदधनपफबभमयरलळवशषसह")
        append('\u093C') // nukta ़
        append("ािीुूृॄॅॆेैॉॊोौ")
        append('\u094D') // virama / halant ्
        append("ॐऽ।॥०१२३४५६७८९")
        append(' ')
    }

    fun packRaster(raster224: FloatArray): FloatArray =
        EnglishTfliteContract.packRaster(raster224)

    fun inputShape(): IntArray = EnglishTfliteContract.inputShape()

    fun formatSize(bytes: Long): String = EnglishTfliteContract.formatSize(bytes)

    fun tokenFor(character: Char): Int {
        val index = GLYPHS.indexOf(character)
        return if (index < 0) BLANK else index + 1
    }

    fun glyphFor(token: Int): Char? {
        val index = token - 1
        if (index !in GLYPHS.indices) return null
        return GLYPHS[index]
    }

    fun isSupportedGlyph(character: Char): Boolean =
        character == ' ' || GLYPHS.indexOf(character) >= 0
}

data class NepaliInkOutput(
    val tokenIds: IntArray,
    val confidences: FloatArray
)

fun interface NepaliInkInterpreter {
    fun infer(input1x224x224x1: FloatArray): NepaliInkOutput?
}

object NepaliTokenDecoder {
    fun decode(output: NepaliInkOutput?): List<HandwritingCandidate> {
        if (output == null || output.tokenIds.isEmpty()) return emptyList()
        val collapsed = ArrayList<Int>()
        val scores = ArrayList<Float>()
        var previous = NepaliTfliteContract.BLANK
        output.tokenIds.forEachIndexed { index, token ->
            if (token == NepaliTfliteContract.BLANK || token == previous) {
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
                val glyph = NepaliTfliteContract.glyphFor(token) ?: return emptyList()
                append(glyph)
            }
        }
        val normalized = HandwritingUnicode.normalize(text)
        if (normalized.isEmpty()) return emptyList()
        if (HandwritingUnicode.looksLatin(normalized)) return emptyList()
        if (!normalized.all { it.isWhitespace() || it.code in 0x0900..0x097F }) {
            return emptyList()
        }
        val confidence = if (scores.isEmpty()) 0f else scores.average().toFloat()
        return listOf(HandwritingCandidate(normalized, confidence, InkLanguage.DEVANAGARI))
    }

    fun tokensFor(text: String): IntArray =
        text.map { character -> NepaliTfliteContract.tokenFor(character) }.toIntArray()
}

/**
 * One Nepali interpreter for the IME process. Reuses the English singleton
 * holder and the shared 224×224 packer. Without a bundled model or TFLite
 * runtime this stays unloaded and never invents Devanagari.
 */
object NepaliTfliteRuntime {
    private val runtime = ReusableInkRuntime<NepaliInkInterpreter>()

    fun isLoaded(): Boolean = runtime.isLoaded()

    fun loadedAsset(): String? = runtime.loadedAsset()

    fun loadNepaliModel(
        assetName: String,
        factory: () -> NepaliInkInterpreter?
    ): Boolean = runtime.load(assetName, factory)

    fun unloadNepaliModel() {
        runtime.unload()
    }

    fun recognizeNepali(raster224: FloatArray): HandwritingResult {
        val active = runtime.get() ?: return HandwritingResult.UNAVAILABLE
        val packed = NepaliTfliteContract.packRaster(raster224)
        val decoded = NepaliTokenDecoder.decode(active.infer(packed))
        val merged = HandwritingCandidateMerger.merge(decoded)
        if (merged.isEmpty()) return HandwritingResult.UNAVAILABLE
        return HandwritingResult.Success(merged, InkLanguage.DEVANAGARI)
    }
}
