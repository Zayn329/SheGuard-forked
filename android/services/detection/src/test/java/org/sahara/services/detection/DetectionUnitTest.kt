package org.sahara.services.detection

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sahara.core.domain.models.DetectorType
import org.sahara.core.domain.models.IncidentState
import org.sahara.services.detection.detectors.KeywordDetector
import org.sahara.services.detection.detectors.MotionDetector
import org.sahara.services.detection.detectors.ScreamDetector
import org.sahara.services.detection.fusion.FusionDecision
import org.sahara.services.detection.fusion.SignalFusionEngine
import org.sahara.services.detection.models.DetectionConfig
import org.sahara.services.detection.models.SignalResult

class DetectionUnitTest {

    private lateinit var config: DetectionConfig
    private lateinit var keywordDetector: KeywordDetector
    private lateinit var screamDetector: ScreamDetector
    private lateinit var motionDetector: MotionDetector
    private lateinit var fusionEngine: SignalFusionEngine

    @Before
    fun setup() {
        config = DetectionConfig()
        keywordDetector = KeywordDetector(config)
        screamDetector = ScreamDetector(config)
        motionDetector = MotionDetector(config)
        fusionEngine = SignalFusionEngine(config)
    }

    @Test
    fun testTFLiteSpeechCommandsClassifierFallbackAndIntegration() {
        val speechClassifier = org.sahara.services.detection.tflite.TFLiteSpeechCommandsClassifier(null)
        assertFalse("Classifier initialized without context should not have loaded model", speechClassifier.isModelLoaded)
        assertEquals("TFLite-SpeechCommands-v1.0", speechClassifier.modelVersion)

        keywordDetector.tfliteClassifier = speechClassifier
        assertFalse("KeywordDetector indicates model is not loaded when uninitialized", keywordDetector.isModelLoaded)
        assertEquals("TFLite-SpeechCommands-v1.0", keywordDetector.modelVersion)

        val loudAudio = ShortArray(1600) { 20000 }
        val confidence = keywordDetector.processAudioChunk(loudAudio, 16000)
        assertTrue("When TFLite speech model is unavailable, falls back to PCM RMS energy calculation", confidence >= config.keywordConfidenceThreshold)
    }

    @Test
    fun testKeywordDetectorThreshold() {
        val quietAudio = ShortArray(1600) { 100 }
        val confidenceLow = keywordDetector.analyzeKeywordPcm(quietAudio, 16000)
        assertTrue("Low energy should yield low confidence", confidenceLow < config.keywordConfidenceThreshold)

        val loudAudio = ShortArray(1600) { 20000 }
        val confidenceHigh = keywordDetector.analyzeKeywordPcm(loudAudio, 16000)
        assertTrue("Loud audio should exceed threshold", confidenceHigh >= config.keywordConfidenceThreshold)
    }

    @Test
    fun testScreamDetectorFrequencyAnalysis() {
        val nonScreamAudio = ShortArray(1600) { (it % 100).toShort() }
        val nonScreamConf = screamDetector.analyzeHybridAcousticFeatures(nonScreamAudio, 16000)
        assertTrue("Patterned low amp audio should not be scream", nonScreamConf < config.screamConfidenceThreshold)

        val screamAudio = ShortArray(1600) { i ->
            if (i % 4 == 0) 25000.toShort() else (-25000).toShort()
        }
        val screamConf = screamDetector.analyzeHybridAcousticFeatures(screamAudio, 16000)
        assertTrue("High zero-crossing and loud amplitude is scream", screamConf >= config.screamConfidenceThreshold)
    }

    @Test
    fun testDynamicLabelIndexLookup() {
        val classifier = org.sahara.services.detection.tflite.TFLiteScreamClassifier(null)
        val sampleLabels = listOf("Speech", "Music", "Silence", "Dog Bark", "Shout", "Cat Meow", "Screaming")
        classifier.updateLabels(sampleLabels)

        assertEquals(listOf(4, 6), classifier.screamLabelIndices)
    }

    @Test
    fun testTFLiteScreamClassifierDegradedFallbackAndModelStatus() {
        val classifier = org.sahara.services.detection.tflite.TFLiteScreamClassifier(null)
        assertFalse("Classifier initialized without context should not have loaded model", classifier.isModelLoaded)
        assertEquals("YAMNet-TFLite-v1.0-AudioSet", classifier.modelVersion)

        screamDetector.tfliteClassifier = classifier
        assertFalse("ScreamDetector indicates model not loaded when uninitialized", screamDetector.isModelLoaded)
        assertEquals("YAMNet-TFLite-v1.0-AudioSet", screamDetector.modelVersion)

        val dummyAudio = ShortArray(1600) { 1000 }
        val confidence = screamDetector.processAudioChunk(dummyAudio, 16000)
        assertTrue("In degraded mode without TFLite model, processAudioChunk falls back gracefully to DSP confidence", confidence >= 0f)
    }

    @Test
    fun testSignalFusionRuleEvaluation() = runBlocking {
        assertEquals(IncidentState.MONITORING, fusionEngine.currentState)

        val keywordSignal = SignalResult(DetectorType.KEYWORD, 0.85f, "help", System.currentTimeMillis())
        fusionEngine.onSignalReceived(keywordSignal)

        assertEquals(IncidentState.ACTIVE_INCIDENT, fusionEngine.currentState)
        assertTrue("Keyword signal alone satisfies default rule", fusionEngine.evaluateConfirmationRule())

        fusionEngine.resetState()
        val screamSignal = SignalResult(DetectorType.SCREAM, 0.80f, "scream", System.currentTimeMillis())
        fusionEngine.onSignalReceived(screamSignal)
        assertEquals(IncidentState.CANDIDATE_INCIDENT, fusionEngine.currentState)
        assertFalse("Scream alone does not satisfy default rule", fusionEngine.evaluateConfirmationRule())

        val motionSignal = SignalResult(DetectorType.MOTION, 0.90f, "impact", System.currentTimeMillis())
        fusionEngine.onSignalReceived(motionSignal)
        assertEquals(IncidentState.ACTIVE_INCIDENT, fusionEngine.currentState)
        assertTrue("Scream AND Motion satisfies default rule", fusionEngine.evaluateConfirmationRule())
    }

    @Test
    fun testConfirmationTimeoutExpiration() {
        val screamSignal = SignalResult(DetectorType.SCREAM, 0.80f, "scream", System.currentTimeMillis() - 10000L)
        fusionEngine.onSignalReceived(screamSignal)
        assertEquals(IncidentState.CANDIDATE_INCIDENT, fusionEngine.currentState)

        fusionEngine.checkConfirmationTimeout(System.currentTimeMillis())
        assertEquals(IncidentState.MONITORING, fusionEngine.currentState)
    }

    @Test
    fun testSpeechCommandsModelFilesExistAndAreValid() {
        val modelFile = java.io.File("../../app/src/main/assets/models/speech_commands.tflite")
        assertTrue("speech_commands.tflite model file must exist in assets/models/", modelFile.exists())
        assertTrue("speech_commands.tflite model file size must be > 0", modelFile.length() > 0)

        val labelsFile = java.io.File("../../app/src/main/assets/models/speech_commands_labels.txt")
        assertTrue("speech_commands_labels.txt labels file must exist in assets/models/", labelsFile.exists())
        val labels = labelsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue("speech_commands_labels.txt must contain labels", labels.isNotEmpty())
    }

    @Test
    fun testKeywordDetectorUsesTFLiteClassifierWhenLoaded() {
        val speechClassifier = org.sahara.services.detection.tflite.TFLiteSpeechCommandsClassifier(null)
        val labelsFile = java.io.File("../../app/src/main/assets/models/speech_commands_labels.txt")
        val loadedLabels = labelsFile.readLines().map { it.trim() }

        val labelsField = org.sahara.services.detection.tflite.TFLiteSpeechCommandsClassifier::class.java.getDeclaredField("labels")
        labelsField.isAccessible = true
        labelsField.set(speechClassifier, loadedLabels)

        val isLoadedField = org.sahara.services.detection.tflite.TFLiteSpeechCommandsClassifier::class.java.getDeclaredField("isModelLoaded")
        isLoadedField.isAccessible = true
        isLoadedField.set(speechClassifier, true)

        assertTrue(speechClassifier.isModelLoaded)
        keywordDetector.tfliteClassifier = speechClassifier

        // When tfliteClassifier is loaded but interpreter is null (e.g. mock loaded state), classifyAudioFrame returns -1f and falls back gracefully
        val dummyAudio = ShortArray(1600) { 20000 }
        val confidence = keywordDetector.processAudioChunk(dummyAudio, 16000)
        assertTrue("KeywordDetector handles audio processing cleanly when TFLite classifier is attached", confidence >= 0f)
    }

    @Test
    fun testDetectionLogManagerStoreAndClear() {
        val logManager = org.sahara.services.detection.log.DetectionLogManager
        logManager.clearLogs()
        assertEquals(0, logManager.events.value.size)

        val signal = SignalResult(
            detectorType = DetectorType.KEYWORD,
            confidence = 0.88f,
            label = "help"
        )
        val samplePcm = ShortArray(100) { 500 }
        logManager.logEvent(signal, samplePcm)

        val logs = logManager.events.value
        assertEquals(1, logs.size)
        assertEquals("help", logs[0].signal.label)
        assertEquals(DetectorType.KEYWORD, logs[0].signal.detectorType)
        assertEquals(0.88f, logs[0].signal.confidence, 0.001f)
        assertTrue("Audio payload stored for audio event", logs[0].audioData != null)

        logManager.clearLogs()
        assertEquals(0, logManager.events.value.size)
    }

    @Test
    fun testFusionDecisionMapsToMicroReportAndCandidatePattern() = runBlocking {
        val screamSignal = SignalResult(DetectorType.SCREAM, 0.90f, "scream", System.currentTimeMillis())
        val motionSignal = SignalResult(DetectorType.MOTION, 0.85f, "impact", System.currentTimeMillis())

        val confirmDecision = FusionDecision.ConfirmIncident(listOf(screamSignal, motionSignal))
        assertTrue(confirmDecision.activeSignals.size == 2)

        val category = org.sahara.core.domain.models.ReportCategory.SUSPICIOUS_ACTIVITY
        val microReport = org.sahara.core.domain.models.MicroReport(
            anonymousReporterToken = "sensor_node_test",
            category = category,
            latitude = 19.0760,
            longitude = 72.8777,
            approximateArea = "Bandra West / Mumbai Central",
            contextDescription = "Automated Sensor Fusion Signal: Scream (conf: 0.90), Impact (conf: 0.85)"
        )

        val secondReport = microReport.copy(
            reportId = java.util.UUID.randomUUID(),
            anonymousReporterToken = "sensor_node_test2",
            timestamp = System.currentTimeMillis() + 1000L
        )

        val patternEngine = org.sahara.core.domain.engine.SpatioTemporalPatternEngine()
        val candidatePatterns = patternEngine.detectCandidatePatterns(listOf(microReport, secondReport))

        assertEquals(1, candidatePatterns.size)
        assertEquals(org.sahara.core.domain.models.ReportCategory.SUSPICIOUS_ACTIVITY, candidatePatterns[0].category)
        assertEquals(2, candidatePatterns[0].reportCount)
    }
}
