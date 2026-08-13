package com.sworupplayz.keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase33HandwritingFrameworkTest {
    @Test
    fun strokeCollectorSupportsBeginEndUndoAndClear() {
        val collector = HandwritingStrokeCollector()
        collector.beginStroke(InkPoint(0f, 0f, 1L, 0.4f))
        collector.append(0.4f, 0.2f, 2L, 0.8f)
        collector.append(0.8f, 0.1f, 3L)
        assertEquals(3, collector.endStroke()?.points?.size)
        assertEquals(1, collector.strokeCount)
        assertTrue(collector.addFinished(listOf(InkPoint(0.1f, 0.8f), InkPoint(0.2f, 0.9f))))
        assertEquals(2, collector.strokeCount)
        assertTrue(collector.undo())
        assertEquals(1, collector.strokeCount)
        assertTrue(collector.boundingBox() != null)
        collector.clear()
        assertTrue(collector.isEmpty)
        assertFalse(collector.undo())
    }

    @Test
    fun normalizerResamplesAndRasterizerIs224AndPreservesAspect() {
        val wide = HandwritingInk(
            listOf(
                InkStroke(
                    listOf(
                        InkPoint(10f, 40f),
                        InkPoint(80f, 42f),
                        InkPoint(160f, 41f),
                        InkPoint(240f, 43f)
                    )
                )
            )
        )
        val normalized = StrokeNormalizer.normalize(wide)
        assertEquals(HandwritingPreprocessor.TARGET_POINTS, normalized.strokes.single().points.size)
        val raster = InkRasterizer.rasterizeFramework(normalized)
        assertEquals(224, InkRasterizer.FRAMEWORK_SIZE)
        assertEquals(224 * 224, raster.size)
        val box = InkRasterizer.occupiedBounds(raster, 224, 224)!!
        assertTrue(box.width > box.height)
        val again = InkRasterizer.rasterizeFramework(normalized)
        assertSame(raster, again)
        val small = HandwritingPreprocessor.prepare(wide)
        assertEquals(32, small.rasterWidth)
    }

    @Test
    fun languageDetectorUsesGeometryNotOcr() {
        val shirorekha = ArrayList<InkPoint>()
        for (step in 0..20) {
            shirorekha += InkPoint(0.08f + step * 0.04f, 0.16f, step.toLong())
        }
        val leftStem = listOf(InkPoint(0.18f, 0.16f), InkPoint(0.18f, 0.82f))
        val rightStem = listOf(InkPoint(0.78f, 0.16f), InkPoint(0.78f, 0.84f))
        val devanagari = HandwritingInk(
            listOf(InkStroke(shirorekha), InkStroke(leftStem), InkStroke(rightStem))
        )
        assertEquals(InkLanguage.DEVANAGARI, HandwritingLanguageDetector.detect(devanagari))

        val loop = ArrayList<InkPoint>()
        for (step in 0..16) {
            val angle = (Math.PI * 2.0 * step / 16.0)
            loop += InkPoint(
                x = (0.52f + 0.22f * kotlin.math.cos(angle)).toFloat(),
                y = (0.54f + 0.22f * kotlin.math.sin(angle)).toFloat(),
                timeMillis = step.toLong()
            )
        }
        assertEquals(InkLanguage.ENGLISH, HandwritingLanguageDetector.detect(HandwritingInk(listOf(InkStroke(loop)))))
        assertEquals(InkLanguage.UNKNOWN, HandwritingLanguageDetector.detect(HandwritingInk(emptyList())))
    }

    @Test
    fun missingModelsReturnUnavailableAndPasswordsAreBlocked() {
        val models = HandwritingModelManager()
        assertEquals("Missing", models.englishStatus())
        assertEquals("Missing", models.nepaliStatus())
        assertEquals("Auto", models.recognitionLanguage())
        assertFalse(models.englishInstalled)
        assertFalse(models.nepaliInstalled)
        val ink = HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.4f, 0.3f)))))
        assertSame(HandwritingResult.UNAVAILABLE, models.recognize(ink, InkLanguage.DEVANAGARI))
        assertTrue(HandwritingResult.UNAVAILABLE.candidates.isEmpty())

        val session = HandwritingSession()
        session.addStroke(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.5f, 0.6f), InkPoint(0.8f, 0.3f)))
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(HandwritingStatus.BLOCKED, session.recognize(password).status)
        session.reset()
        assertTrue(session.collector.isEmpty)
        assertEquals(HandwritingStatus.EMPTY, session.lastResult.status)
        assertEquals(HandwritingStatus.EMPTY, session.recognize(EditorFieldPolicy.TYPE_CLASS_TEXT).status)
    }

    @Test
    fun candidateMergeCapsAtFiveAndSuggestionBarTakesThree() {
        val merged = HandwritingCandidateMerger.merge(
            listOf(
                HandwritingCandidate("hello", 0.9f, InkLanguage.ENGLISH),
                HandwritingCandidate("hello", 0.8f, InkLanguage.ENGLISH),
                HandwritingCandidate("help", 0.7f, InkLanguage.ENGLISH),
                HandwritingCandidate("hell", 0.6f, InkLanguage.ENGLISH),
                HandwritingCandidate("held", 0.5f, InkLanguage.ENGLISH),
                HandwritingCandidate("helm", 0.4f, InkLanguage.ENGLISH),
                HandwritingCandidate("heat", 0.05f, InkLanguage.ENGLISH)
            )
        )
        assertEquals(5, merged.size)
        assertEquals(listOf("hello", "help", "hell"), HandwritingCandidateMerger.forSuggestionBar(merged))
        assertEquals(3, HandwritingCandidateMerger.forSuggestionBar(merged).size)
    }

    @Test
    fun privacyForbidsNetworkAndDoesNotTouchLearnedData() {
        assertFalse(HandwritingPrivacyPolicy.allowsNetwork())
        assertFalse(HandwritingPrivacyPolicy.persistStrokes())
        assertFalse(HandwritingPrivacyPolicy.logsStrokes())
        val storage = object : SettingsStorage {
            private val values = linkedMapOf<String, Any>("learned_english_words" to "keep\t1")
            override fun contains(key: String) = key in values
            override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
            override fun getString(key: String) = values[key] as? String
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putString(key: String, value: String) { values[key] = value }
            override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
        }
        HandwritingSession().reset()
        assertEquals("keep\t1", storage.getString("learned_english_words"))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertFalse(ProductionIntegrationPolicy.handwritingModelBundled())
    }
}
