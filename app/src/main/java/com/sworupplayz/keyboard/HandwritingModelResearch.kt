package com.sworupplayz.keyboard

/**
 * Phase 33 investigation record. This is documentation in code, not a recognizer.
 * A GitHub repo or Apache OCR model does not become shippable handwriting.
 */
enum class HandwritingModelRejection {
    RUNTIME_DOWNLOAD,
    NO_REDISTRIBUTABLE_WEIGHTS,
    UNCLEAR_OR_MISSING_LICENSE,
    WRONG_TASK,
    WRONG_SCRIPT,
    TOO_LARGE,
    CONVERSION_IMPOSSIBLE_HERE,
    CANNOT_VERIFY_INFERENCE
}

data class InvestigatedHandwritingModel(
    val name: String,
    val source: String,
    val license: String,
    val format: String,
    val sizeNote: String,
    val input: String,
    val output: String,
    val vocabulary: String,
    val supportsDevanagari: Boolean,
    val recognitionGrain: String,
    val runtime: String,
    val androidCompatible: Boolean,
    val conversionRequired: Boolean,
    val conversionPossibleHere: Boolean,
    val redistributable: Boolean,
    val offline: Boolean,
    val rejection: Set<HandwritingModelRejection>
)

object HandwritingModelResearch {
    const val PHASE = 33
    const val STATUS = "RECOGNITION_NOT_SHIPPABLE"

    val candidates: List<InvestigatedHandwritingModel> = listOf(
        InvestigatedHandwritingModel(
            name = "Google ML Kit Digital Ink",
            source = "https://developers.google.com/ml-kit/vision/digital-ink-recognition/android",
            license = "Google ML Kit terms; language packs downloaded separately",
            format = "proprietary native + downloaded language pack",
            sizeNote = "~20 MB per language pack plus native runtime",
            input = "stroke sequences",
            output = "Unicode candidates",
            vocabulary = "includes Devanagari in some catalogs",
            supportsDevanagari = true,
            recognitionGrain = "word / digital ink",
            runtime = "ML Kit native",
            androidCompatible = true,
            conversionRequired = false,
            conversionPossibleHere = false,
            redistributable = false,
            offline = false,
            rejection = setOf(
                HandwritingModelRejection.RUNTIME_DOWNLOAD,
                HandwritingModelRejection.NO_REDISTRIBUTABLE_WEIGHTS
            )
        ),
        InvestigatedHandwritingModel(
            name = "DHCD + custom TFLite CNN",
            source = "https://archive.ics.uci.edu/dataset/389/devanagari+handwritten+character+dataset",
            license = "Dataset CC BY 4.0; no official mobile weights",
            format = "32x32 grayscale images; would need trained TFLite",
            sizeNote = "dataset 76.7 MB; a CNN could be <2 MB after int8",
            input = "isolated character image",
            output = "46 character classes",
            vocabulary = "36 consonants + 10 digits",
            supportsDevanagari = true,
            recognitionGrain = "isolated character",
            runtime = "TensorFlow Lite",
            androidCompatible = true,
            conversionRequired = true,
            conversionPossibleHere = false,
            redistributable = false,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.NO_REDISTRIBUTABLE_WEIGHTS,
                HandwritingModelRejection.CONVERSION_IMPOSSIBLE_HERE,
                HandwritingModelRejection.WRONG_TASK
            )
        ),
        InvestigatedHandwritingModel(
            name = "kaushu42/nepali-ocr model.h5",
            source = "https://github.com/kaushu42/nepali-ocr",
            license = "none declared (all rights reserved)",
            format = "Keras HDF5",
            sizeNote = "unpublished here; isolated-character CNN",
            input = "character image",
            output = "Nepali letters and digits",
            vocabulary = "क-ज्ञ and ०-९",
            supportsDevanagari = true,
            recognitionGrain = "isolated character",
            runtime = "Keras / would need TFLite conversion",
            androidCompatible = false,
            conversionRequired = true,
            conversionPossibleHere = false,
            redistributable = false,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.UNCLEAR_OR_MISSING_LICENSE,
                HandwritingModelRejection.WRONG_TASK,
                HandwritingModelRejection.CONVERSION_IMPOSSIBLE_HERE
            )
        ),
        InvestigatedHandwritingModel(
            name = "tulasiram58827/ocr_tflite",
            source = "https://github.com/tulasiram58827/ocr_tflite",
            license = "Apache-2.0 for conversion repo; underlying OCR models vary",
            format = "TFLite (keras-ocr, captcha OCR, Clova STR)",
            sizeNote = "several MB each",
            input = "Latin / captcha line images",
            output = "Latin text",
            vocabulary = "English / captcha charset",
            supportsDevanagari = false,
            recognitionGrain = "printed / scene line",
            runtime = "TensorFlow Lite",
            androidCompatible = true,
            conversionRequired = false,
            conversionPossibleHere = false,
            redistributable = true,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.WRONG_SCRIPT,
                HandwritingModelRejection.WRONG_TASK
            )
        ),
        InvestigatedHandwritingModel(
            name = "PaddleOCR PP-OCRv3 Devanagari rec.onnx",
            source = "https://huggingface.co/monkt/paddleocr-onnx (PaddlePaddle, Apache-2.0)",
            license = "Apache-2.0",
            format = "ONNX recognition + separate detection",
            sizeNote = "8.6 MB rec + 2.3 MB det; onnxruntime-android is additional native MB",
            input = "cropped printed/scene text-line image",
            output = "Hindi/Marathi/Nepali/Sanskrit printed text",
            vocabulary = "Devanagari printed OCR charset",
            supportsDevanagari = true,
            recognitionGrain = "printed / scene line, not digital ink",
            runtime = "ONNX Runtime",
            androidCompatible = true,
            conversionRequired = false,
            conversionPossibleHere = false,
            redistributable = true,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.WRONG_TASK,
                HandwritingModelRejection.CANNOT_VERIFY_INFERENCE
            )
        ),
        InvestigatedHandwritingModel(
            name = "IIIT-HW-Dev / academic CRNN HTR",
            source = "research papers and IIIT-HW-Dev dataset references",
            license = "dataset/research terms; no mobile weight release found",
            format = "PyTorch / academic checkpoints when released at all",
            sizeNote = "typically tens of MB",
            input = "offline handwritten word images",
            output = "Devanagari words",
            vocabulary = "handwritten Devanagari words",
            supportsDevanagari = true,
            recognitionGrain = "word / line HTR",
            runtime = "PyTorch or custom CTC",
            androidCompatible = false,
            conversionRequired = true,
            conversionPossibleHere = false,
            redistributable = false,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.NO_REDISTRIBUTABLE_WEIGHTS,
                HandwritingModelRejection.CONVERSION_IMPOSSIBLE_HERE
            )
        ),
        InvestigatedHandwritingModel(
            name = "Tesseract tessdata Devanagari",
            source = "https://github.com/tesseract-ocr/tessdata",
            license = "Apache-2.0",
            format = "traineddata + native Tesseract",
            sizeNote = "language pack + native engine, large for this IME",
            input = "scanned page image",
            output = "printed Devanagari",
            vocabulary = "printed Devanagari",
            supportsDevanagari = true,
            recognitionGrain = "printed page",
            runtime = "Tesseract native",
            androidCompatible = true,
            conversionRequired = false,
            conversionPossibleHere = false,
            redistributable = true,
            offline = true,
            rejection = setOf(
                HandwritingModelRejection.WRONG_TASK,
                HandwritingModelRejection.TOO_LARGE
            )
        )
    )

    fun shippableCandidates(): List<InvestigatedHandwritingModel> =
        candidates.filter { model ->
            model.redistributable &&
                model.supportsDevanagari &&
                model.offline &&
                model.rejection.isEmpty() &&
                (model.recognitionGrain.contains("ink") ||
                    model.recognitionGrain.contains("handwritten"))
        }

    fun actuallyShippable(): Boolean = false

    fun requiredArtifact(): String =
        "A redistributable, offline Devanagari digital-ink or handwritten-word model " +
            "(TFLite or ONNX) with a documented input spec, plus a sandbox that can load " +
            "and verify at least one real handwritten word."
}
