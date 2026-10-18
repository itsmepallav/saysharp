package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.telemetry.*
import kotlin.test.*

class SelfAssessmentTest {
    private val first = RecordingRef("private-first-audio.m4a", 35000)
    private fun ready(day: Int = 1) = PracticeSession(day = day, stage = PracticeStage.FirstCheck,
        recordings = mapOf(RecordingSlot.First to first), listenedRecordingIds = setOf(first.id),
        completedDays = (1 until day).associateWith { DayReview() })

    @Test fun ordinaryCriteriaAreSingleShortQuestionsWithUniqueBriefHints() {
        val ordinary = SessionCatalogue.definitions.filterNot { it.isCheckpoint }
        assertEquals(12, ordinary.size)
        assertEquals(12, ordinary.map { it.selfCheck }.toSet().size)
        assertEquals(12, ordinary.map { it.listeningHint }.toSet().size)
        for (definition in ordinary) {
            assertEquals(1, definition.selfCheck.count { it == '?' })
            assertTrue(definition.selfCheck.split(' ').size <= 18, definition.id)
            assertTrue(definition.listeningHint.isNotBlank())
            assertTrue(definition.listeningHint.split(' ').size <= 30, definition.id)
            assertTrue(definition.listeningHint.count { it == '.' || it == '?' || it == '!' } <= 2, definition.id)
            assertFalse(definition.listeningHint.contains(definition.correctiveExample))
            assertFalse(definition.selfCheck.contains("confident", ignoreCase = true))
        }
        assertTrue(BundledSessionRepository.definition(4).selfCheck.contains("information"))
        assertTrue(BundledSessionRepository.definition(6).selfCheck.contains("conclusion"))
        assertTrue(BundledSessionRepository.definition(12).listeningHint.contains("changing"))
    }

    @Test fun everyChoicePersistsAndRestoresOnEveryOrdinaryLesson() {
        for (definition in SessionCatalogue.definitions.filterNot { it.isCheckpoint }) for (choice in SelfAssessment.entries) {
            val store = AssessmentStore(ready(definition.day))
            val controller = PracticeController(AssessmentAudio(), store)
            controller.assess(choice)
            assertEquals(choice, store.value.firstAssessment)
            assertEquals(choice, controller.session.attempt(RecordingSlot.First)?.assessment)
            val restored = PracticeController(AssessmentAudio(), store)
            assertEquals(choice, restored.session.firstAssessment)
            assertEquals(definition.listeningHint, restored.definition.listeningHint)
            restored.advance()
            assertEquals(PracticeStage.Lesson, restored.session.stage) // Includes honest Unsure and Not yet.
        }
    }

    @Test fun unsureCanChangeToEitherChoiceWithoutDuplicateSaves() {
        for (replacement in listOf(SelfAssessment.Done, SelfAssessment.NotYet)) {
            val store = AssessmentStore(ready())
            val controller = PracticeController(AssessmentAudio(), store)
            controller.assess(SelfAssessment.Unsure)
            controller.assess(SelfAssessment.Unsure)
            assertEquals(1, store.writes)
            controller.assess(replacement)
            controller.assess(replacement)
            assertEquals(2, store.writes)
            assertEquals(replacement, store.value.firstAssessment)
            assertEquals(replacement, PracticeController(AssessmentAudio(), store).session.firstAssessment)
        }
    }

    @Test fun replayKeepsUnsureAndDoesNotRewriteAlreadyListenedProgress() {
        val store = AssessmentStore(ready())
        val audio = AssessmentAudio()
        val controller = PracticeController(audio, store)
        controller.assess(SelfAssessment.Unsure)
        val writes = store.writes
        controller.play(first)
        assertEquals(SelfAssessment.Unsure, controller.session.firstAssessment)
        assertEquals(first.id, controller.playingId)
        audio.finished!!()
        assertEquals(SelfAssessment.Unsure, controller.session.firstAssessment)
        assertEquals(writes, store.writes)
        assertNull(controller.playingId)
    }

    @Test fun retryKeepsInitialRecordingAssessmentAndEarlierHistory() {
        val earlier = DayReview(recordings = mapOf(RecordingSlot.First to first.copy(id = "past")), firstAssessment = SelfAssessment.Done)
        val store = AssessmentStore(ready(2).copy(firstAssessment = SelfAssessment.Unsure, completedDays = mapOf(1 to earlier)))
        val controller = PracticeController(AssessmentAudio(), store)
        controller.advance(); controller.advance() // Correction then retry.
        assertEquals(PracticeStage.Retry, controller.session.stage)
        controller.startRecording(); controller.stopRecording()
        assertEquals(first, controller.session.recordings[RecordingSlot.First])
        assertEquals(SelfAssessment.Unsure, controller.session.attempt(RecordingSlot.First)?.assessment)
        assertNotEquals(first, controller.session.recordings[RecordingSlot.Retry])
        assertNull(controller.session.attempt(RecordingSlot.Retry)?.assessment)
        assertEquals(earlier, controller.session.completedDays[1])
    }

    @Test fun staleLessonAndRecordingCommandsCannotAssessANewerAttempt() {
        val store = AssessmentStore(ready(2))
        val controller = PracticeController(AssessmentAudio(), store)
        controller.assess(SelfAssessment.Done, expectedLessonId = lessonId(1), expectedRecordingId = first.id)
        controller.assess(SelfAssessment.Done, expectedLessonId = lessonId(2), expectedRecordingId = "replaced-recording")
        assertNull(controller.session.firstAssessment)
        assertEquals(0, store.writes)
        controller.assess(SelfAssessment.Unsure, expectedLessonId = lessonId(2), expectedRecordingId = first.id)
        assertEquals(SelfAssessment.Unsure, controller.session.firstAssessment)
        assertEquals(1, store.writes)
    }

    @Test fun changingAssessmentIsBlockedOutsideItsStepOrBeforeReplay() {
        val store = AssessmentStore(ready().copy(listenedRecordingIds = emptySet()))
        var controller = PracticeController(AssessmentAudio(), store)
        controller.assess(SelfAssessment.Done)
        assertEquals(0, store.writes)
        store.value = ready().copy(stage = PracticeStage.Retry, firstAssessment = SelfAssessment.NotYet)
        controller = PracticeController(AssessmentAudio(), store)
        controller.assess(SelfAssessment.Done)
        assertEquals(SelfAssessment.NotYet, controller.session.firstAssessment)
        assertEquals(0, store.writes)
    }

    @Test fun failedAssessmentWriteKeepsSelectionAndAllowsRetry() {
        val store = AssessmentStore(ready().copy(firstAssessment = SelfAssessment.Unsure)).apply { failSave = true }
        val controller = PracticeController(AssessmentAudio(), store)
        controller.assess(SelfAssessment.Done)
        assertEquals(SelfAssessment.Unsure, controller.session.firstAssessment)
        assertEquals(SelfAssessment.Unsure, store.value.firstAssessment)
        assertNotNull(controller.message)
        store.failSave = false
        controller.assess(SelfAssessment.Done)
        assertEquals(SelfAssessment.Done, controller.session.firstAssessment)
        assertNull(controller.message)
    }

    @Test fun checkpointAssessmentBelongsToFreshAnswerAndDoesNotChangeFamiliarComparison() {
        for (day in listOf(7,14)) {
            val fresh = first.copy(id = "fresh-$day")
            val repeat = first.copy(id = "familiar-$day")
            val store = AssessmentStore(PracticeSession(day = day, stage = PracticeStage.CheckpointCompare,
                recordings = mapOf(RecordingSlot.CheckpointRepeat to repeat, RecordingSlot.CheckpointFresh to fresh),
                listenedRecordingIds = setOf(fresh.id, repeat.id), comparison = Comparison.LessClear))
            val controller = PracticeController(AssessmentAudio(), store)
            controller.assess(SelfAssessment.Unsure, retry = true)
            assertEquals(SelfAssessment.Unsure, controller.session.attempt(RecordingSlot.CheckpointFresh)?.assessment)
            assertNull(controller.session.attempt(RecordingSlot.CheckpointRepeat)?.assessment)
            assertEquals(Comparison.LessClear, controller.session.comparison)
            controller.advance()
            assertEquals(PracticeStage.Summary, controller.session.stage)
        }
    }

    @Test fun onlyOperationalMetadataReachesTelemetryNeverAudioReferenceOrReflection() {
        val sent = mutableListOf<Pair<String, Map<String, Any>>>()
        val telemetry = Telemetry(object : TelemetryBackend {
            override fun event(name: String, parameters: Map<String, Any>) { sent += name to parameters }
            override fun context(parameters: Map<String, Any>) { sent += "context" to parameters }
            override fun error(operation: String, error: Throwable?) {}
        })
        val audio = AssessmentAudio()
        val controller = PracticeController(audio, AssessmentStore(ready()), telemetry)
        controller.play(first); audio.finished!!()
        controller.assess(SelfAssessment.Unsure)
        controller.saveWin("Private speech reflection never sent")
        val assessment = sent.single { it.first == "self_assessment_saved" }.second
        assertEquals(mapOf("day" to 1, "stage" to "FirstCheck", "choice" to "Unsure"), assessment)
        assertFalse(sent.toString().contains(first.id))
        assertFalse(sent.toString().contains("Private speech reflection"))
        assertTrue(sent.flatMap { it.second.values }.all { it is String || it is Number || it is Boolean })
    }
}
private class AssessmentStore(var value: PracticeSession) : ProgressRepository {
    var writes = 0
    var failSave = false
    override fun load() = value
    override fun save(session: SessionProgress) { check(!failSave); value = session; writes++ }
}
private class AssessmentAudio : PracticeAudio {
    var finished: (() -> Unit)? = null
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("retry-private-audio.m4a", 35000)
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { finished = onFinished }
    override fun stopPlayback() {}
}
