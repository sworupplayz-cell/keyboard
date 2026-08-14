package com.sworupplayz.keyboard

import java.io.File
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase32HandwritingRecognitionTest {
    private val diagonal = listOf(
        InkPoint(0.1f, 0.2f, 0L),
        InkPoint(0.5f, 0.6f, 12L),
        InkPoint(0.8f, 0.3f, 24L)
    )

    @Test
    fun strokeNormalizerScalesTranslatesAndResamples() {
        val raw = HandwritingInk(
            listOf(
                InkStroke(listOf(InkPoint(80f, 40f), InkPoint(120f, 90f), InkPoint(160f, 50f))),
                InkStroke(listOf(InkPoint(0f, 0f)))
            )
        )
        val filtered = StrokeNormalizer.filterNoise(raw)
        assertEquals(1, filtered.strokes.size)
        val normalized = StrokeNormalizer.normalize(raw)
        assertEquals(HandwritingPreprocessor.TARGET_POINTS, normalized.strokes.single().points.size)
        val bounds = StrokeNormalizer.bounds(normalized)!!
        assertTrue(bounds.left >= 0f)
        assertTrue(bounds.top >= 0f)
        assertTrue(bounds.right <= 1.0001f)
        assertTrue(bounds.bottom <= 1.0001f)
        val empty = StrokeNormalizer.normalize(HandwritingInk(emptyList()))
        assertTrue(empty.strokes.isEmpty())
        val tiny = StrokeNormalizer.filterNoise(
            HandwritingInk(listOf(InkStroke(listOf(InkPoint(0.4f, 0.4f), InkPoint(0.401f, 0.4f)))))
        )
        assertTrue(tiny.strokes.isEmpty())
    }

    @Test
    fun rasterizerMatchesExpectedGridAndSurvivesEmptyInk() {
        val prepared = HandwritingPreprocessor.prepare(HandwritingInk(listOf(InkStroke(diagonal))))
        assertFalse(prepared.empty)
        assertEquals(32 * 32, prepared.raster.size)
        assertEquals(HandwritingPreprocessor.RASTER_SIZE, prepared.rasterWidth)
        assertTrue(InkRasterizer.occupiedCount(prepared.raster) > 4)
        val blank = HandwritingPreprocessor.prepare(HandwritingInk(emptyList()))
        assertTrue(blank.empty)
        assertEquals(0, InkRasterizer.occupiedCount(blank.raster))
    }

    @Test
    fun modelBoundaryStaysUnavailableWithoutABundledFile() {
        assertFalse(HandwritingModelSpec.isBundled(emptyList()))
        assertFalse(HandwritingModelSpec.isBundled(listOf("emoji_catalog.tsv")))
        assertTrue(HandwritingModelSpec.isBundled(listOf("handwriting/handwriting_devanagari.tflite")))
        assertEquals("No bundled offline handwriting model", HandwritingModelLoader().unavailableReason())
        assertNull(HandwritingModelLoader().load(KeyboardLanguage.NEPALI))
        assertFalse(ProductionIntegrationPolicy.handwritingRecognitionImplemented())
        assertTrue(ProductionIntegrationPolicy.handwritingPipelineReady())
        assertFalse(ProductionIntegrationPolicy.handwritingModelBundled())
        val missing = File("app/src/main/assets/handwriting/handwriting_devanagari.tflite")
        assertFalse(missing.isFile)
    }

    @Test
    fun unavailableAndMalformedPathsNeverInventCharacters() {
        val offline = OfflineHandwritingRecognizer(HandwritingModelLoader())
        assertFalse(offline.isAvailable)
        assertTrue(offline.recognize(HandwritingInk(listOf(InkStroke(diagonal)))).isEmpty())
        val prepared = offline.recognizePrepared(
            HandwritingInk(listOf(InkStroke(diagonal))),
            KeyboardLanguage.NEPALI,
            generation = 3
        )
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, prepared.status)
        assertTrue(prepared.candidates.isEmpty())
        assertEquals(3, prepared.generation)
        val empty = offline.recognizePrepared(HandwritingInk(emptyList()), KeyboardLanguage.NEPALI, 1)
        assertEquals(HandwritingStatus.NO_MATCH, empty.status)
        val state = HandwritingInputState(UnavailableNepaliHandwritingRecognizer)
        state.addStroke(diagonal)
        assertEquals(HandwritingStatus.RECOGNIZER_UNAVAILABLE, state.recognize().status)
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun candidatePolicyRespectsConfidenceLanguageAndLimits() {
        val noisy = listOf(
            RecognitionCandidate("नमस्ते", 0.9f),
            RecognitionCandidate("नमस्ते", 0.8f),
            RecognitionCandidate("hello", 0.99f),
            RecognitionCandidate("नेपाल", 0.05f),
            RecognitionCandidate("काठमाडौं", 0.4f),
            RecognitionCandidate("मलाई", 0.3f),
            RecognitionCandidate("तपाईं", 0.25f),
            RecognitionCandidate("विद्यालय", 0.22f)
        )
        val nepali = HandwritingCandidatePolicy.keep(noisy, KeyboardLanguage.NEPALI)
        assertEquals(listOf("नमस्ते", "काठमाडौं", "मलाई", "तपाईं", "विद्यालय"), nepali.map { it.text })
        assertEquals(5, nepali.size)
        val english = HandwritingCandidatePolicy.keep(noisy, KeyboardLanguage.ENGLISH)
        assertEquals(listOf("hello"), english.map { it.text })
        assertFalse(HandwritingSuggestionBridge.shouldAutoCommit(nepali))
        assertEquals(
            SuggestionLanguage.NEPALI,
            HandwritingSuggestionBridge.queryForCommitted("नमस्ते", KeyboardLanguage.NEPALI).language
        )
    }

    @Test
    fun unicodeNormalizationKeepsMatrasAndConjuncts() {
        val decomposed = "नेपाल" + "\u093E"
        val normalized = HandwritingUnicode.normalize(decomposed)
        assertEquals(Normalizer.normalize(decomposed.trim(), Normalizer.Form.NFC), normalized)
        assertTrue(HandwritingUnicode.looksDevanagari("क्ष"))
        assertTrue(HandwritingUnicode.allowedForLanguage("राम्रो", KeyboardLanguage.NEPALI))
        assertTrue(HandwritingUnicode.allowedForLanguage("धन्यवाद", KeyboardLanguage.NEPALI))
        assertFalse(HandwritingUnicode.allowedForLanguage("school", KeyboardLanguage.NEPALI))
        assertTrue(HandwritingUnicode.allowedForLanguage("school", KeyboardLanguage.ENGLISH))
        assertTrue(HandwritingUnicode.allowedForLanguage("म school", KeyboardLanguage.ROMAN))
        assertTrue(HandwritingUnicode.looksLatin("I am"))
    }

    @Test
    fun jobsCancelStaleResultsAndSensitiveFieldsBlockRecognition() {
        val jobs = HandwritingJobController()
        val first = jobs.current()
        jobs.bump()
        assertTrue(HandwritingLifecyclePolicy.shouldIgnoreStale(first, jobs.current()))
        assertFalse(jobs.isCurrent(first))
        assertTrue(HandwritingLifecyclePolicy.shouldCancelOnHide())
        assertTrue(HandwritingLifecyclePolicy.shouldCancelOnFieldChange())
        assertTrue(HandwritingLifecyclePolicy.shouldCancelOnPanelClose())
        assertFalse(HandwritingLifecyclePolicy.shouldRecognizeOnEveryMove())
        val password = EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD
        val pin = EditorFieldPolicy.TYPE_CLASS_NUMBER or EditorFieldPolicy.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(HandwritingPrivacyPolicy.allowsRecognition(password))
        assertFalse(HandwritingPrivacyPolicy.allowsRecognition(pin))
        assertTrue(HandwritingPrivacyPolicy.allowsRecognition(EditorFieldPolicy.TYPE_CLASS_TEXT))
        assertFalse(HandwritingPrivacyPolicy.persistStrokes())
        assertFalse(HandwritingPrivacyPolicy.logsStrokes())
        assertFalse(HandwritingPrivacyPolicy.allowsNetwork())
        val state = HandwritingInputState(FakeRecognizer(listOf("नमस्ते")))
        state.addStroke(diagonal)
        state.blockSensitiveField()
        assertEquals(HandwritingStatus.BLOCKED, state.status)
        assertNull(state.confirm())
    }

    @Test
    fun integrationKeepsExistingEngineAndDoesNotLogOrNetwork() {
        val english = LocalWordSuggester.fromWords(listOf("hello", "help", "home"))
        val nepali = LocalWordSuggester.fromWords(listOf("नमस्ते", "नेपाल", "घर"))
        val converter = RomanNepaliConverter.from(
            resource("roman_nepali_dictionary.tsv").reader(),
            setOf("school")
        )
        val engine = SuggestionEngine(english, nepali, converter)
        val query = HandwritingSuggestionBridge.queryForCommitted("नमस्ते", KeyboardLanguage.NEPALI)
        val visible = engine.suggest(query).visible()
        assertTrue(visible.size <= 3)
        assertTrue("नमस्ते" in visible)
        assertFalse(InputConnectionCommitter.commit(null, "नमस्ते"))
        assertFalse(HandwritingPrivacyPolicy.shouldLearnCommitted(
            EditorFieldPolicy.TYPE_CLASS_TEXT or EditorFieldPolicy.TYPE_TEXT_VARIATION_PASSWORD,
            "नमस्ते"
        ))
        assertEquals("Nepali handwriting", HandwritingUiState.showsLanguage(KeyboardLanguage.NEPALI))
        assertEquals("Offline recognizer is not bundled", HandwritingUiState.statusMessage(HandwritingStatus.RECOGNIZER_UNAVAILABLE))
        assertEquals("Language, Nepali", AccessibilityLabels.languageControl(KeyboardLanguage.NEPALI))
        assertEquals("Handwriting नमस्ते", AccessibilityLabels.handwritingCandidate("नमस्ते"))
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertFalse(manifest.readText().contains("android.permission.INTERNET"))
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }
        assertFalse(gradle.readText().contains("tensorflow"))
        assertFalse(gradle.readText().contains("mlkit"))
    }

    private fun resource(name: String): File = listOf(
        File("src/main/res/raw/$name"),
        File("app/src/main/res/raw/$name")
    ).first { it.isFile }

    private class FakeRecognizer(
        private val results: List<String>
    ) : NepaliHandwritingRecognizer {
        override val isAvailable: Boolean = true
        override fun recognize(ink: HandwritingInk): List<String> = results
    }
}
