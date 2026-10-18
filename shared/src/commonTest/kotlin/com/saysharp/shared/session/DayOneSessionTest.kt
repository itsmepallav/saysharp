package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import kotlin.test.*

class DayOneSessionTest {
    private val audio = DayOneAudio()
    private val storage = DayOneStorage()
    private fun controller() = PracticeController(audio, storage)
    private fun record(c: PracticeController): RecordingRef {
        c.startRecording(); c.stopRecording()
        return c.session.recordings.getValue(requireNotNull(c.session.stage.recordingSlot()))
    }
    private fun listen(c: PracticeController, ref: RecordingRef) {
        c.play(ref, true); audio.finished.last().invoke()
    }

    @Test fun contentHasOneObservableSkillSameRetryAndUnscaffoldedTransfer() {
        val d = DayOneSession
        assertEquals(d.prompts.getValue(RecordingSlot.First).text, d.prompts.getValue(RecordingSlot.Retry).text)
        assertNotEquals(d.prompts.getValue(RecordingSlot.First).text, d.prompts.getValue(RecordingSlot.Application).text)
        assertTrue(d.instruction.split(' ').size in 25..45)
        assertTrue(d.scenario.isNotBlank() && d.weakExample.isNotBlank() && d.correction.isNotBlank())
        assertTrue(d.stages.indexOf(PracticeStage.Lesson) > d.stages.indexOf(PracticeStage.FirstCheck))
        assertFalse(PracticeStage.Baseline in d.stages)
    }

    @Test fun offlineLoopResumesAtEveryStepAndKeepsThreeDistinctFilesAndHistory() {
        var c = controller()
        c.advance(); val first = record(c); c.advance()
        c = controller()
        assertEquals(PracticeStage.FirstCheck, c.session.stage)
        listen(c, first)
        c = controller()
        assertTrue(first.id in c.session.listenedRecordingIds)
        c.assess(SelfAssessment.Unsure); c.advance()
        c = controller()
        assertEquals(PracticeStage.Lesson, c.session.stage)
        assertEquals(SelfAssessment.Unsure, c.session.firstAssessment)
        c.advance(); val retry = record(c); c.advance()
        c = controller()
        assertEquals(PracticeStage.Compare, c.session.stage)
        listen(c, first); listen(c, retry)
        c.advance(); val transfer = record(c); c.advance()
        c = controller()
        assertEquals(PracticeStage.Complete, c.session.stage)
        assertEquals(3, setOf(first.id, retry.id, transfer.id).size)
        assertEquals(c.session.recordings, c.session.completedDays.getValue(1).recordings)
        for (ref in listOf(first, retry, transfer)) {
            assertTrue(audio.exists(ref)); listen(c, ref)
        }
    }

    @Test fun stageBoundCommandsAndRepeatedRecordTapsAreIdempotent() {
        val c = controller()
        c.advance(PracticeStage.Welcome); c.advance(PracticeStage.Welcome)
        assertEquals(PracticeStage.FirstAttempt, c.session.stage)
        c.startRecording(); c.startRecording()
        assertEquals(1, audio.starts)
        c.stopRecording(); c.stopRecording()
        assertEquals(1, audio.stops)
        c.advance(PracticeStage.FirstAttempt); c.advance(PracticeStage.FirstAttempt)
        assertEquals(PracticeStage.FirstCheck, c.session.stage)
    }

    @Test fun permissionDenialKeepsStepAndCanBeRetried() {
        val c = controller(); c.advance(); c.permissionDenied()
        assertFalse(c.isRecording)
        assertEquals(PracticeStage.FirstAttempt, c.session.stage)
        assertTrue(c.message!!.contains("permission"))
        record(c)
        assertNull(c.message)
    }

    @Test fun synchronousRecorderFailureNeverDisplaysRecordingAndCanRetry() {
        val c = controller(); c.advance()
        audio.synchronousError = true
        c.startRecording()
        assertFalse(c.isRecording)
        assertFalse(storage.value.recordingInProgress)
        assertTrue(c.message!!.contains("interrupted"))
        audio.synchronousError = false
        record(c)
        assertEquals(1, c.session.recordings.size)
    }

    @Test fun lateRecorderCallbacksCannotCancelOrStopNewAttempt() {
        val c = controller(); c.advance(); record(c)
        val staleError = audio.errors.first()
        val staleLimit = audio.limits.first()
        c.startRecording(); staleError(); staleLimit()
        assertTrue(c.isRecording)
        assertEquals(1, audio.stops)
        c.stopRecording()
        assertEquals(1, audio.files.size)
    }

    @Test fun forceStopMarkerRestoresVisibleInterruptionAndRetainsEarlierAttempt() {
        val c = controller(); c.advance(); val first = record(c)
        c.startRecording()
        assertTrue(storage.value.recordingInProgress)
        val restored = controller() // Simulates a new process loading only durable progress.
        assertFalse(restored.isRecording)
        assertFalse(storage.value.recordingInProgress)
        assertEquals(first, restored.session.recordings[RecordingSlot.First])
        assertTrue(restored.message!!.contains("interrupted"))
    }

    @Test fun backgroundKeepsPlayablePartialAttemptAndNormalRestartResumesStep() {
        val c = controller(); c.advance(); c.startRecording(); c.onBackground()
        assertFalse(c.isRecording)
        assertFalse(storage.value.recordingInProgress)
        assertTrue(c.message!!.contains("saved"))
        val restored = controller()
        assertEquals(PracticeStage.FirstAttempt, restored.session.stage)
        assertTrue(audio.exists(restored.session.recordings.getValue(RecordingSlot.First)))
    }

    @Test fun failedRetakeSavePreservesOriginalInMemoryAndStorage() {
        val c = controller(); c.advance(); val original = record(c)
        c.startRecording(); storage.failSave = true; c.stopRecording()
        assertEquals(original, c.session.recordings[RecordingSlot.First])
        assertEquals(original, storage.value.recordings[RecordingSlot.First])
        assertTrue(original in audio.files)
        assertEquals(2, audio.files.size)
        assertTrue(c.state.value.hasPendingRecording)
        storage.failSave = false
        val restored = controller()
        assertEquals(original, restored.session.recordings[RecordingSlot.First])
    }

    @Test fun failedStartMarkerPreventsRecordingWhenStorageUnavailable() {
        val c = controller(); c.advance(); storage.failSave = true
        c.startRecording()
        assertFalse(c.isRecording)
        assertEquals(0, audio.starts)
        assertNotNull(c.message)
    }

    @Test fun failedPlaybackDoesNotUnlockSelfCheckAndSuccessfulReplayDoes() {
        val c = controller(); c.advance(); val first = record(c); c.advance()
        c.play(first, true); audio.playErrors.last().invoke()
        assertNull(c.playingId)
        assertNotNull(c.message)
        c.assess(SelfAssessment.Done); c.advance()
        assertEquals(PracticeStage.FirstCheck, c.session.stage)
        listen(c, first); c.assess(SelfAssessment.NotYet); c.advance()
        assertEquals(PracticeStage.Lesson, c.session.stage)
    }

    @Test fun latePlayerCallbacksCannotClearNewPlaybackOrMarkOldRecordingListened() {
        val c = controller(); c.advance(); val old = record(c)
        c.play(old); val staleFinish = audio.finished.last(); val staleError = audio.playErrors.last()
        c.stopPlayback(); val newer = record(c); c.play(newer)
        staleFinish(); staleError()
        assertEquals(newer.id, c.playingId)
        assertFalse(old.id in c.session.listenedRecordingIds)
        audio.finished.last().invoke()
        assertTrue(newer.id in c.session.listenedRecordingIds)
    }

    @Test fun retakeInvalidatesListeningAndAssociatedAssessmentOnlyAfterSuccessfulSave() {
        val c = controller(); c.advance(); val first = record(c)
        listen(c, first)
        storage.value = c.session.copy(firstAssessment = SelfAssessment.Done, comparison = Comparison.Clearer)
        val restored = controller(); val replacement = record(restored)
        assertNotEquals(first, replacement)
        assertNull(restored.session.firstAssessment)
        assertNull(restored.session.comparison)
        assertFalse(first.id in restored.session.listenedRecordingIds)
    }
}

private class DayOneStorage : ProgressRepository {
    var value = PracticeSession()
    var failSave = false
    override fun load() = value
    override fun save(session: SessionProgress) { check(!failSave); value = session }
}

private class DayOneAudio : PracticeAudio {
    var starts = 0
    var stops = 0
    var synchronousError = false
    val errors = mutableListOf<() -> Unit>()
    val limits = mutableListOf<() -> Unit>()
    val finished = mutableListOf<() -> Unit>()
    val playErrors = mutableListOf<() -> Unit>()
    val files = mutableSetOf<RecordingRef>()
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {
        starts++; errors += onError; limits += onLimit
        if (synchronousError) onError()
    }
    override fun stop() = RecordingRef("local-${++stops}", 35_000).also { files += it }
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = recording in files
    override fun delete(recording: RecordingRef) { files -= recording }
    override fun deleteAllRecordings() { files.clear() }
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {
        check(exists(recording)); finished += onFinished; playErrors += onError
    }
    override fun stopPlayback() {}
}
