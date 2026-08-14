package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase35NepaliHandwritingTest {
    @Test
    fun missingModelDoesNotLoadOrInventText() {
        NepaliTfliteRuntime.unloadNepaliModel()
        val manager = HandwritingModelManager()
        assertFalse(manager.nepaliInstalled)
        assertEquals("Missing", manager.nepaliStatus())
        assertEquals("Missing", manager.nepaliDetailStatus())
        assertFalse(manager.loadNepaliModel())
        assertFalse(NepaliTfliteRuntime.isLoaded())
        val raster = FloatArray(NepaliTfliteContract.INPUT_SIZE) { 0.2f }
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, manager.recognizeNepali(raster).status)
        assertTrue(manager.recognizeNepali(raster).candidates.isEmpty())
        val ink = HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.4f, 0.3f)))))
        assertSame(HandwritingResult.UNAVAILABLE, manager.recognize(ink, InkLanguage.DEVANAGARI))
        assertTrue(
            listOf(
                File("app/src/main/assets/handwriting/nepali.tflite"),
                File("src/main/assets/handwriting/nepali.tflite")
            ).none { it.isFile }
        )
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertFalse(ProductionIntegrationPolicy.handwritingModelBundled())
    }

    @Test
    fun singletonInterpreterLoadsOnceAndUnloadsOnDestroy() {
        NepaliTfliteRuntime.unloadNepaliModel()
        var created = 0
        val interpreter = NepaliInkInterpreter { input ->
            assertEquals(NepaliTfliteContract.INPUT_SIZE, input.size)
            NepaliInkOutput(NepaliTokenDecoder.tokensFor("नमस्ते"), FloatArray(6) { 0.94f })
        }
        val manager = HandwritingModelManager(
            assetNames = listOf("nepali.tflite"),
            assetSizes = mapOf("nepali.tflite" to 4096L),
            nepaliFactory = {
                created += 1
                interpreter
            }
        )
        assertTrue(manager.loadNepaliModel())
        assertTrue(manager.loadNepaliModel())
        assertEquals(1, created)
        assertTrue(NepaliTfliteRuntime.isLoaded())
        assertEquals("Installed (4 KB)", manager.nepaliDetailStatus())
        manager.unloadNepaliModel()
        assertFalse(NepaliTfliteRuntime.isLoaded())
    }

    @Test
    fun unicodeDecoderHandlesMatrasHalantAndMarks() {
        assertEquals("नमस्ते", decodeText("नमस्ते"))
        assertEquals("का", decodeText("का"))
        assertEquals("विद्यालय", decodeText("विद्यालय"))
        assertEquals("सं", decodeText("सं"))
        assertEquals("माँ", decodeText("माँ"))
        assertEquals("दुःख", decodeText("दुःख"))
        assertTrue(NepaliTokenDecoder.tokensFor("नमस्ते").contains(NepaliTfliteContract.tokenFor('्')))
        assertTrue(NepaliTokenDecoder.tokensFor("का").contains(NepaliTfliteContract.tokenFor('ा')))
        assertTrue(NepaliTokenDecoder.tokensFor("सं").contains(NepaliTfliteContract.tokenFor('ं')))
        assertTrue(NepaliTokenDecoder.tokensFor("माँ").contains(NepaliTfliteContract.tokenFor('ँ')))
        assertTrue(NepaliTokenDecoder.tokensFor("दुःख").contains(NepaliTfliteContract.tokenFor('ः')))
        assertTrue(NepaliTokenDecoder.decode(NepaliInkOutput(intArrayOf(1, 99_999), floatArrayOf(0.9f, 0.9f))).isEmpty())
        assertTrue(NepaliTokenDecoder.decode(NepaliInkOutput(NepaliTokenDecoder.tokensFor("hello"), FloatArray(5) { 0.9f })).isEmpty())
        assertFalse(HandwritingUnicode.looksLatin("नमस्ते"))
        assertTrue(HandwritingUnicode.looksDevanagari("नमस्ते"))
    }

    @Test
    fun rasterIs224AndSuccessfulRecognitionDecodesDevanagari() {
        NepaliTfliteRuntime.unloadNepaliModel()
        val ink = HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.8f, 0.3f)))))
        val raster = InkRasterizer.rasterizeFramework(StrokeNormalizer.normalize(ink))
        assertEquals(224 * 224, raster.size)
        assertEquals(1, NepaliTfliteContract.inputShape()[0])
        assertEquals(224, NepaliTfliteContract.inputShape()[1])
        val packed = NepaliTfliteContract.packRaster(raster)
        assertEquals(NepaliTfliteContract.INPUT_SIZE, packed.size)
        assertTrue(packed.all { it in 0f..1f })

        val manager = HandwritingModelManager(
            assetNames = listOf("nepali.tflite"),
            nepaliFactory = {
                NepaliInkInterpreter {
                    NepaliInkOutput(NepaliTokenDecoder.tokensFor("नमस्ते"), FloatArray(6) { 0.94f })
                }
            }
        )
        assertTrue(manager.loadNepaliModel())
        val result = manager.recognizeNepali(raster)
        assertEquals(HandwritingStatus.RESULTS, result.status)
        assertEquals(InkLanguage.DEVANAGARI, result.detectedLanguage)
        assertEquals(listOf("नमस्ते"), result.suggestionTexts())
        assertEquals(0.94f, result.candidates.single().confidence, 0.001f)
        NepaliTfliteRuntime.unloadNepaliModel()
    }

    @Test
    fun confidenceOrdersAndRemovesDuplicates() {
        val merged = HandwritingCandidateMerger.merge(
            listOf(
                HandwritingCandidate("नमस्ते", 0.40f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नमस्ते", 0.94f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नमस्कार", 0.70f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नाम", 0.55f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नगर", 0.50f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नयाँ", 0.45f, InkLanguage.DEVANAGARI),
                HandwritingCandidate("नदी", 0.05f, InkLanguage.DEVANAGARI)
            )
        )
        assertEquals(5, merged.size)
        assertEquals(listOf("नमस्ते", "नमस्कार", "नाम", "नगर", "नयाँ"), merged.map { it.text })
        assertEquals(0.94f, merged.first().confidence, 0.001f)
        assertEquals(listOf("नमस्ते", "नमस्कार", "नाम"), HandwritingCandidateMerger.forSuggestionBar(merged))
        assertEquals(3, HandwritingCandidateMerger.SUGGESTION_LIMIT)
        assertEquals(5, HandwritingCandidateMerger.INTERNAL_LIMIT)
    }

    @Test
    fun unknownTriesEnglishFirstThenNepali() {
        EnglishTfliteRuntime.unloadEnglishModel()
        NepaliTfliteRuntime.unloadNepaliModel()
        val ink = HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.8f, 0.3f)))))
        val both = HandwritingModelManager(
            assetNames = listOf("english.tflite", "nepali.tflite"),
            englishFactory = {
                EnglishInkInterpreter {
                    EnglishInkOutput(intArrayOf(EnglishTfliteContract.BLANK), floatArrayOf(0f))
                }
            },
            nepaliFactory = {
                NepaliInkInterpreter {
                    NepaliInkOutput(NepaliTokenDecoder.tokensFor("घर"), floatArrayOf(0.9f, 0.9f))
                }
            }
        )
        val fallback = both.recognize(ink, InkLanguage.UNKNOWN)
        assertEquals(HandwritingStatus.RESULTS, fallback.status)
        assertEquals("घर", fallback.candidates.single().text)

        val englishFirst = HandwritingModelManager(
            assetNames = listOf("english.tflite", "nepali.tflite"),
            englishFactory = {
                EnglishInkInterpreter {
                    EnglishInkOutput(EnglishTokenDecoder.tokensFor("hi"), floatArrayOf(0.9f, 0.8f))
                }
            },
            nepaliFactory = {
                NepaliInkInterpreter {
                    NepaliInkOutput(NepaliTokenDecoder.tokensFor("घर"), floatArrayOf(0.99f, 0.99f))
                }
            }
        )
        val preferred = englishFirst.recognize(ink, InkLanguage.UNKNOWN)
        assertEquals(listOf("hi"), preferred.suggestionTexts())
        EnglishTfliteRuntime.unloadEnglishModel()
        NepaliTfliteRuntime.unloadNepaliModel()
    }

    @Test
    fun confirmCancelUndoPasswordAndLifecycle() {
        val session = HandwritingSession(
            HandwritingModelManager(
                assetNames = listOf("nepali.tflite"),
                nepaliFactory = {
                    NepaliInkInterpreter {
                        NepaliInkOutput(NepaliTokenDecoder.tokensFor("ठीक"), FloatArray(3) { 0.8f })
                    }
                }
            )
        )
        val shirorekha = (0..20).map { step -> InkPoint(0.08f + step * 0.04f, 0.16f, step.toLong()) }
        assertTrue(session.addStroke(shirorekha))
        assertTrue(session.addStroke(listOf(InkPoint(0.18f, 0.16f), InkPoint(0.18f, 0.82f))))
        assertTrue(session.addStroke(listOf(InkPoint(0.78f, 0.16f), InkPoint(0.78f, 0.84f))))
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(HandwritingStatus.BLOCKED, session.recognize(password).status)
        val recognized = session.recognize(EditorFieldPolicy.TYPE_CLASS_TEXT)
        assertEquals(HandwritingStatus.RESULTS, recognized.status)
        assertEquals("ठीक", recognized.candidates.first().text)
        assertEquals(InkLanguage.DEVANAGARI, recognized.detectedLanguage)
        val accepted = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
        accepted.acceptExternal(recognized.suggestionTexts(), HandwritingStatus.RESULTS)
        assertEquals("ठीक", accepted.confirm(0))
        assertFalse(InputConnectionCommitter.commit(null, "ठीक"))
        assertTrue(session.undo())
        session.clear()
        assertTrue(session.collector.isEmpty)
        session.addStroke(listOf(InkPoint(0.2f, 0.2f), InkPoint(0.3f, 0.4f)))
        session.reset()
        assertEquals(HandwritingStatus.EMPTY, session.lastResult.status)
        session.unload()
        assertFalse(NepaliTfliteRuntime.isLoaded())
        assertEquals("Recognizing handwriting", AccessibilityLabels.handwritingLoading())
        assertEquals("Handwriting ठीक", AccessibilityLabels.handwritingCandidate("ठीक"))
        assertEquals("Confirm handwriting", AccessibilityLabels.key(KeySpec("OK", KeyAction.HANDWRITING_CONFIRM)))
        assertEquals("Cancel handwriting", AccessibilityLabels.key(KeySpec("✕", KeyAction.HANDWRITING_CANCEL)))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }
        assertFalse(gradle.readText().contains("tensorflow"))
        assertFalse(gradle.readText().contains("mlkit"))
        assertFalse(gradle.readText().contains("onnxruntime"))
    }

    private fun decodeText(text: String): String {
        val tokens = NepaliTokenDecoder.tokensFor(text)
        val scores = FloatArray(tokens.size) { 0.9f }
        return NepaliTokenDecoder.decode(NepaliInkOutput(tokens, scores)).single().text
    }
}
