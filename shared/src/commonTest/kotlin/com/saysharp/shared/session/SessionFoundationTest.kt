package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionFoundationTest {
    private val dayOne = BundledSessionRepository.definition(1)
    private val kept = RecordingReference("opaque-recording", 2_000)

    @Test fun dayOneReusesExistingPromptsAndRequiresFreshTransfer() {
        assertEquals(DayOneSession, dayOne)
        assertNotEquals(dayOne.prompts.getValue(RecordingSlot.First).text, dayOne.prompts.getValue(RecordingSlot.Application).text)
        assertEquals(60_000, dayOne.prompts.getValue(RecordingSlot.First).limitMillis)
        assertEquals(20_000, BundledSessionRepository.definition(4).prompts.getValue(RecordingSlot.Retry).limitMillis)
    }

    @Test fun recordingsAndAssessmentGateTransitionsWithoutRequiringPositiveRatings() {
        val first = PracticeSession(stage = PracticeStage.FirstAttempt)
        assertEquals(first, advanceSession(first, dayOne))
        val recorded = first.copy(recordings = mapOf(RecordingSlot.First to kept), listenedRecordingIds = setOf(kept.id))
        val check = advanceSession(recorded, dayOne)
        assertEquals(PracticeStage.FirstCheck, check.stage)
        assertEquals(check, advanceSession(check, dayOne))
        assertEquals(PracticeStage.Lesson, advanceSession(check.copy(firstAssessment = SelfAssessment.NotYet), dayOne).stage)
        val compare = check.copy(stage = PracticeStage.Compare, retryAssessment = SelfAssessment.Unsure, comparison = Comparison.LessClear)
        assertEquals(PracticeStage.Apply, advanceSession(compare, dayOne).stage)
    }

    @Test fun checkpointsRetainBothPromptsAndRestoreMissingBaselineEntry() {
        val definition = BundledSessionRepository.definition(7)
        val recovery = PracticeSession(day = 7, stage = PracticeStage.Baseline, recordings = mapOf(RecordingSlot.Baseline to kept))
        assertEquals(PracticeStage.CheckpointRepeat, advanceSession(recovery, definition).stage)
        assertNotEquals(definition.prompts.getValue(RecordingSlot.CheckpointRepeat).text, definition.prompts.getValue(RecordingSlot.CheckpointFresh).text)
        assertFalse(PracticeStage.Retry in definition.stages)
    }

    @Test fun completionIsIdempotentAndKeepsExistingHistoryAndRecordingIds() {
        val previous = DayReview(realLifeWin = "private note")
        val progress = PracticeSession(day = 2, stage = PracticeStage.Apply,
            recordings = mapOf(RecordingSlot.Baseline to kept, RecordingSlot.Application to kept.copy(id = "transfer")),
            completedDays = mapOf(1 to previous)).withRequiredExercise()
        val complete = advanceSession(progress, BundledSessionRepository.definition(2))
        assertEquals(previous, complete.completedDays.getValue(1))
        assertEquals("transfer", complete.completedDays.getValue(2).recordings.getValue(RecordingSlot.Application).id)
        assertEquals(kept, complete.recordings[RecordingSlot.Baseline])
        assertEquals(complete, advanceSession(complete, BundledSessionRepository.definition(2)))
    }

    @Test fun attemptAssociatesTheRightAssessmentWithoutInventingAScore() {
        val progress = PracticeSession(recordings = mapOf(RecordingSlot.First to kept), firstAssessment = SelfAssessment.Unsure)
        assertEquals(Attempt(RecordingSlot.First, kept, SelfAssessment.Unsure), progress.attempt(RecordingSlot.First))
        assertNull(progress.attempt(RecordingSlot.Retry))
    }

    @Test fun definitionsRejectWrongDayAndTransitionsRejectMismatchedContent() {
        assertFailsWith<IllegalArgumentException> { BundledSessionRepository.definition(15) }
        assertFailsWith<IllegalArgumentException> { advanceSession(PracticeSession(day = 2), dayOne) }
    }

    @Test fun observableProgressOnlyAdvancesAfterSuccessfulPersistence() = runTest {
        val repository = MemoryProgress()
        val audio = Recorder()
        val controller = PracticeController(audio, repository, player = Player())
        val seen = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.state.collect { seen += it } }
        repository.failSave = true
        controller.advance()
        assertEquals(PracticeStage.Welcome, controller.state.value.progress.stage)
        assertNotNull(controller.state.value.message)
        assertTrue(seen.none { it.progress.stage == PracticeStage.Baseline })
        repository.failSave = false
        controller.advance()
        assertEquals(repository.progress, controller.state.value.progress)
        assertEquals(PracticeStage.FirstAttempt, seen.last().progress.stage)
        assertNull(seen.last().message)
    }

    @Test fun unreadableProgressIsNotOverwrittenOrDeleted() {
        val repository = MemoryProgress().apply { failLoad = true }
        val recorder = Recorder()
        val controller = PracticeController(recorder, repository, player = Player())
        controller.advance(); controller.startRecording()
        assertEquals(0, repository.saves)
        assertEquals(0, recorder.deletes)
        assertFalse(controller.state.value.isRecording)
        assertNotNull(controller.state.value.message)
    }

    @Test fun injectedTimerStopsAtLimitAndBackgroundStopsPlayerWithoutDeletingKeptAudio() = runTest {
        val repository = MemoryProgress().apply { progress = PracticeSession(stage = PracticeStage.FirstAttempt) }
        val recorder = Recorder { testScheduler.currentTime }
        val player = Player()
        val controller = PracticeController(recorder, repository, player = player, scope = backgroundScope)
        controller.startRecording()
        advanceTimeBy(60_100)
        runCurrent()
        assertFalse(controller.state.value.isRecording)
        assertNotNull(controller.state.value.progress.attempt(RecordingSlot.First))
        controller.play(kept)
        assertEquals(1, player.plays)
        assertEquals(kept.id, controller.state.value.playingId)
        controller.onBackground()
        assertNull(controller.state.value.playingId)
        assertTrue(player.stops > 0)
        assertEquals(0, recorder.deletes)
    }

    @Test fun failedRetakePreservesOriginalRecordingInObservableAndSavedProgress() {
        val repository = MemoryProgress().apply { progress = PracticeSession(stage = PracticeStage.FirstAttempt, recordings = mapOf(RecordingSlot.First to kept)) }
        val recorder = Recorder()
        val controller = PracticeController(recorder, repository, player = Player())
        controller.startRecording()
        repository.failSave = true
        controller.stopRecording()
        assertEquals(kept, controller.state.value.progress.recordings[RecordingSlot.First])
        assertEquals(kept, repository.progress.recordings[RecordingSlot.First])
        assertEquals(0, recorder.deletes) // Finalized retake is kept pending metadata retry.
    }
}

private class MemoryProgress : ProgressRepository {
    var progress = PracticeSession()
    var failLoad = false
    var failSave = false
    var saves = 0
    override fun load(): SessionProgress { check(!failLoad); return progress }
    override fun save(session: SessionProgress) { check(!failSave); progress = session; saves++ }
}

private class Recorder(private val clock: () -> Long = { 0L }) : AudioRecorder {
    var deletes = 0
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingReference("new-attempt", 2_000)
    override fun cancel() {}
    override fun exists(recording: RecordingReference) = true
    override fun delete(recording: RecordingReference) { deletes++ }
    override fun deleteAllRecordings() { deletes++ }
    override fun monotonicMillis() = clock()
}

private class Player : AudioPlayer {
    var plays = 0
    var stops = 0
    override fun play(recording: RecordingReference, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { plays++ }
    override fun stopPlayback() { stops++ }
}
