package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase34EnglishHandwritingTest {
    @Test
    fun missingModelDoesNotLoadOrInventText() {
        EnglishTfliteRuntime.unloadEnglishModel()
        val manager = HandwritingModelManager()
        assertFalse(manager.englishInstalled)
        assertEquals("Missing", manager.englishStatus())
        assertEquals("Missing", manager.englishDetailStatus())
        assertFalse(manager.loadEnglishModel())
        assertFalse(EnglishTfliteRuntime.isLoaded())
        val raster = FloatArray(EnglishTfliteContract.INPUT_SIZE) { 0.2f }
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, manager.recognizeEnglish(raster).status)
        assertTrue(manager.recognizeEnglish(raster).candidates.isEmpty())
        assertTrue(
            listOf(
                File("app/src/main/assets/handwriting/english.tflite"),
                File("src/main/assets/handwriting/english.tflite")
            ).none { it.isFile }
        )
    }

    @Test
    fun singletonInterpreterLoadsOnceAndUnloadsOnDestroy() {
        EnglishTfliteRuntime.unloadEnglishModel()
        var created = 0
        val interpreter = EnglishInkInterpreter { input ->
            assertEquals(EnglishTfliteContract.INPUT_SIZE, input.size)
            EnglishInkOutput(EnglishTokenDecoder.tokensFor("hi"), floatArrayOf(0.9f, 0.8f))
        }
        val manager = HandwritingModelManager(
            assetNames = listOf("english.tflite"),
            assetSizes = mapOf("english.tflite" to 2048L),
            englishFactory = {
                created += 1
                interpreter
            }
        )
        assertTrue(manager.loadEnglishModel())
        assertTrue(manager.loadEnglishModel())
        assertEquals(1, created)
        assertTrue(EnglishTfliteRuntime.isLoaded())
        assertEquals("Installed (2 KB)", manager.englishDetailStatus())
        manager.unloadEnglishModel()
        assertFalse(EnglishTfliteRuntime.isLoaded())
    }

    @Test
    fun rasterIs224AndSuccessfulRecognitionDecodesTokens() {
        EnglishTfliteRuntime.unloadEnglishModel()
        val ink = HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.8f, 0.3f)))))
        val raster = InkRasterizer.rasterizeFramework(StrokeNormalizer.normalize(ink))
        assertEquals(224 * 224, raster.size)
        assertEquals(1, EnglishTfliteContract.inputShape()[0])
        assertEquals(224, EnglishTfliteContract.inputShape()[1])
        val packed = EnglishTfliteContract.packRaster(raster)
        assertEquals(EnglishTfliteContract.INPUT_SIZE, packed.size)
        assertTrue(packed.all { it in 0f..1f })

        val manager = HandwritingModelManager(
            assetNames = listOf("english.tflite"),
            englishFactory = {
                EnglishInkInterpreter {
                    EnglishInkOutput(EnglishTokenDecoder.tokensFor("Cat!"), FloatArray(4) { 0.95f })
                }
            }
        )
        assertTrue(manager.loadEnglishModel())
        val result = manager.recognizeEnglish(raster)
        assertEquals(HandwritingStatus.RESULTS, result.status)
        assertEquals(listOf("Cat!"), result.suggestionTexts())
        EnglishTfliteRuntime.unloadEnglishModel()
    }

    @Test
    fun confidenceOrdersAndRemovesDuplicates() {
        val merged = HandwritingCandidateMerger.merge(
            listOf(
                HandwritingCandidate("hello", 0.4f, InkLanguage.ENGLISH),
                HandwritingCandidate("hello", 0.9f, InkLanguage.ENGLISH),
                HandwritingCandidate("help", 0.7f, InkLanguage.ENGLISH)
            )
        )
        assertEquals(listOf("hello", "help"), merged.map { it.text })
        assertEquals(0.9f, merged.first().confidence, 0.001f)
        assertEquals(listOf("hello", "help"), HandwritingCandidateMerger.forSuggestionBar(merged))
    }

    @Test
    fun confirmCancelUndoPasswordAndLifecycle() {
        val session = HandwritingSession(
            HandwritingModelManager(
                assetNames = listOf("english.tflite"),
                englishFactory = {
                    EnglishInkInterpreter {
                        EnglishInkOutput(EnglishTokenDecoder.tokensFor("ok"), floatArrayOf(0.8f, 0.8f))
                    }
                }
            )
        )
        assertTrue(session.addStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.4f, 0.5f), InkPoint(0.7f, 0.2f))))
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(HandwritingStatus.BLOCKED, session.recognize(password).status)
        val recognized = session.recognize(EditorFieldPolicy.TYPE_CLASS_TEXT)
        assertEquals(HandwritingStatus.RESULTS, recognized.status)
        assertEquals("ok", recognized.candidates.first().text)
        assertTrue(session.undo())
        session.clear()
        assertTrue(session.collector.isEmpty)
        session.addStroke(listOf(InkPoint(0.2f, 0.2f), InkPoint(0.3f, 0.4f)))
        session.reset()
        assertEquals(HandwritingStatus.EMPTY, session.lastResult.status)
        session.unload()
        assertFalse(EnglishTfliteRuntime.isLoaded())
        assertFalse(InputConnectionCommitter.commit(null, "ok"))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }
        assertFalse(gradle.readText().contains("tensorflow"))
        assertFalse(gradle.readText().contains("mlkit"))
        assertEquals("Recognizing handwriting", AccessibilityLabels.handwritingLoading())
    }
}
