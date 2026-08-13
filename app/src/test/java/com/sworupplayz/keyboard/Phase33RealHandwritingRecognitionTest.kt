package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase33RealHandwritingRecognitionTest {
    private val stroke = listOf(
        InkPoint(0.12f, 0.20f, 0L),
        InkPoint(0.48f, 0.55f, 16L),
        InkPoint(0.82f, 0.28f, 32L)
    )

    @Test
    fun researchFindsNoShippableDevanagariHandwritingModel() {
        assertEquals("RECOGNITION_NOT_SHIPPABLE", HandwritingModelResearch.STATUS)
        assertEquals(33, HandwritingModelResearch.PHASE)
        assertTrue(HandwritingModelResearch.candidates.size >= 7)
        assertFalse(HandwritingModelResearch.actuallyShippable())
        assertTrue(HandwritingModelResearch.shippableCandidates().isEmpty())
        assertTrue(HandwritingModelResearch.requiredArtifact().contains("Devanagari"))
        val paddle = HandwritingModelResearch.candidates.first { it.name.contains("PaddleOCR") }
        assertTrue(paddle.supportsDevanagari)
        assertTrue(paddle.redistributable)
        assertTrue(HandwritingModelRejection.WRONG_TASK in paddle.rejection)
        val mlkit = HandwritingModelResearch.candidates.first { it.name.contains("ML Kit") }
        assertTrue(HandwritingModelRejection.RUNTIME_DOWNLOAD in mlkit.rejection)
        val unlicensed = HandwritingModelResearch.candidates.first { it.name.contains("kaushu42") }
        assertTrue(HandwritingModelRejection.UNCLEAR_OR_MISSING_LICENSE in unlicensed.rejection)
    }

    @Test
    fun modelDiscoveryAndLoadingStayUnavailable() {
        val loader = HandwritingModelLoader()
        assertFalse(loader.isAvailable)
        assertNull(loader.load(KeyboardLanguage.NEPALI))
        assertEquals("No bundled offline handwriting model", loader.unavailableReason())
        assertFalse(HandwritingModelSpec.isBundled(emptyList()))
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertFalse(ProductionIntegrationPolicy.handwritingModelBundled())
        assertTrue(ProductionIntegrationPolicy.handwritingPipelineReady())
        val weights = listOf(
            File("app/src/main/assets/handwriting/handwriting_devanagari.tflite"),
            File("src/main/assets/handwriting/handwriting_devanagari.tflite"),
            File("app/src/main/assets/handwriting/handwriting_devanagari.onnx"),
            File("src/main/assets/handwriting/handwriting_devanagari.onnx")
        )
        assertTrue(weights.none { it.isFile })
    }

    @Test
    fun preprocessingAndTensorContractRemainStable() {
        val prepared = HandwritingPreprocessor.prepare(HandwritingInk(listOf(InkStroke(stroke))))
        assertFalse(prepared.empty)
        assertEquals(32, prepared.rasterWidth)
        assertEquals(32, prepared.rasterHeight)
        assertEquals(32 * 32, prepared.raster.size)
        assertEquals(HandwritingModelSpec.INPUT_WIDTH, prepared.rasterWidth)
        assertTrue(InkRasterizer.occupiedCount(prepared.raster) > 0)
        assertTrue(HandwritingPreprocessor.prepare(HandwritingInk(emptyList())).empty)
    }

    @Test
    fun inferenceWithoutModelDoesNotDecodeUnicodeOrInventConfidence() {
        val result = BundledHandwritingInterpreter.infer(
            null,
            HandwritingPreprocessor.prepare(HandwritingInk(listOf(InkStroke(stroke))))
        )
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, result.status)
        assertTrue(result.candidates.isEmpty())
        val offline = OfflineHandwritingRecognizer(HandwritingModelLoader())
        val prepared = offline.recognizePrepared(
            HandwritingInk(listOf(InkStroke(stroke))),
            KeyboardLanguage.NEPALI,
            generation = 4
        )
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, prepared.status)
        assertTrue(prepared.candidates.isEmpty())
        assertEquals(4, prepared.generation)
        assertEquals(5, HandwritingCandidatePolicy.limit(99))
        assertTrue(
            HandwritingCandidatePolicy.keep(
                listOf(RecognitionCandidate("नमस्ते", 0.01f)),
                KeyboardLanguage.NEPALI
            ).isEmpty()
        )
    }

    @Test
    fun cancellationStaleResultsPasswordsAndPrivacyHold() {
        val jobs = HandwritingJobController()
        val seen = jobs.current()
        jobs.cancel()
        assertTrue(HandwritingLifecyclePolicy.shouldIgnoreStale(seen, jobs.current()))
        assertTrue(HandwritingLifecyclePolicy.shouldCancelOnHide())
        assertTrue(HandwritingLifecyclePolicy.shouldCancelOnFieldChange())
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        val pin = EditorFieldPolicy.TYPE_CLASS_NUMBER or EditorFieldPolicy.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(HandwritingPrivacyPolicy.allowsRecognition(password))
        assertFalse(HandwritingPrivacyPolicy.allowsRecognition(pin))
        val state = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
        state.addStroke(stroke)
        state.blockSensitiveField()
        assertEquals(HandwritingStatus.BLOCKED, state.status)
        assertNull(state.confirm())
        state.clear()
        assertEquals(HandwritingStatus.EMPTY, state.status)
        assertFalse(HandwritingPrivacyPolicy.persistStrokes())
        assertFalse(HandwritingPrivacyPolicy.logsStrokes())
        assertFalse(HandwritingPrivacyPolicy.allowsNetwork())
        assertFalse(HandwritingSuggestionBridge.shouldAutoCommit(emptyList()))
        assertFalse(InputConnectionCommitter.commit(null, "नमस्ते"))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }
        assertFalse(gradle.readText().contains("tensorflow"))
        assertFalse(gradle.readText().contains("onnxruntime"))
        assertFalse(gradle.readText().contains("mlkit"))
    }
}
