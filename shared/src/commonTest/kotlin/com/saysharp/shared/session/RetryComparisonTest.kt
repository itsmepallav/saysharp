package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import kotlin.test.*

class RetryComparisonTest {
    private val audio = RetryAudio()
    private val store = RetryStore()
    private fun controller() = PracticeController(audio, store)
    private fun record(c: PracticeController) { c.startRecording(); c.stopRecording() }
    private fun reachRetry(): PracticeController {
        val c = controller()
        c.advance(); record(c); c.advance()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.play(first); audio.finished.last().invoke()
        c.assess(SelfAssessment.Unsure); c.advance()
        assertEquals(PracticeStage.Lesson, c.session.stage)
        assertTrue(c.definition.showsModelExample(c.session.stage))
        assertTrue(audio.exists(first))
        c.advance()
        assertEquals(PracticeStage.Retry, c.session.stage)
        return c
    }
    private fun reachCompare(): PracticeController = reachRetry().also { record(it); it.advance() }

    @Test fun correctionsAreOneSpecificInstructionAndRetryAlwaysUsesOriginalQuestion() {
        for (definition in SessionCatalogue.definitions.filterNot { it.isCheckpoint }) {
            assertTrue(definition.correction.isNotBlank())
            assertEquals(1, definition.correction.count { it == '.' || it == '?' || it == '!' }, definition.id)
            assertTrue(definition.correction.split(' ').size <= 25, definition.id)
            assertFalse(definition.correction.contains("confident", ignoreCase = true))
            assertEquals(definition.prompts.getValue(RecordingSlot.First).text, definition.prompts.getValue(RecordingSlot.Retry).text)
            assertNotEquals(definition.prompts.getValue(RecordingSlot.First).text, definition.prompts.getValue(RecordingSlot.Application).text)
        }
    }

    @Test fun firstIsSavedAndSelfAssessedBeforeCorrectionAndRetryHasDistinctIdentity() {
        val c = reachRetry()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        assertEquals(first, store.value.recordings[RecordingSlot.First])
        record(c)
        val retry = c.session.recordings.getValue(RecordingSlot.Retry)
        assertNotEquals(first.id, retry.id)
        assertTrue(audio.exists(first) && audio.exists(retry))
        assertEquals(SelfAssessment.Unsure, c.session.firstAssessment)
        assertNull(c.session.retryAssessment)
    }

    @Test fun switchingAtoBandBtoAStopsPreviousAudioAndStaleCallbacksCannotClearNewPlayback() {
        val c = reachCompare()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        val retry = c.session.recordings.getValue(RecordingSlot.Retry)
        audio.events.clear()
        c.play(first)
        val staleFinish = audio.finished.last()
        val staleError = audio.playErrors.last()
        c.play(retry)
        assertEquals(listOf("play:${first.id}", "stop:${first.id}", "play:${retry.id}"), audio.events)
        staleFinish(); staleError()
        assertEquals(retry.id, c.playingId)
        assertEquals(retry.id, audio.activePlayer)
        c.play(first)
        assertEquals(first.id, audio.activePlayer)
        assertEquals(first.id, c.playingId)
        assertEquals(listOf("stop:${retry.id}", "play:${first.id}"), audio.events.takeLast(2))
    }

    @Test fun tappingPlayingAttemptStopsItAndLeavingCompareStopsPlayback() {
        val c = reachCompare()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.play(first); c.play(first)
        assertNull(c.playingId); assertNull(audio.activePlayer)
        c.play(first); c.advance(PracticeStage.Compare)
        assertEquals(PracticeStage.Apply, c.session.stage)
        assertNull(c.playingId); assertNull(audio.activePlayer)
    }

    @Test fun optionalComparisonNeedsNeitherReplayNorSecondAssessment() {
        val c = reachCompare()
        assertEquals(PracticeStage.Compare, c.session.stage)
        assertNull(c.session.retryAssessment)
        assertNull(c.session.comparison)
        assertFalse(c.session.recordings.getValue(RecordingSlot.Retry).id in c.session.listenedRecordingIds)
        c.advance()
        assertEquals(PracticeStage.Apply, c.session.stage)
        assertEquals(SelfAssessment.Unsure, c.session.firstAssessment)
        assertNull(c.session.retryAssessment)
    }

    @Test fun repeatedRecordStopAndAdvanceCommandsDoNotCreateDuplicateRetryOrSkipTransfer() {
        val c = reachRetry()
        c.startRecording(); c.startRecording()
        c.stopRecording(); c.stopRecording()
        assertEquals(2, audio.starts)
        assertEquals(2, audio.stops)
        assertEquals(2, audio.files.size)
        c.advance(PracticeStage.Retry); c.advance(PracticeStage.Retry)
        assertEquals(PracticeStage.Compare, c.session.stage)
        c.advance(PracticeStage.Compare); c.advance(PracticeStage.Compare)
        assertEquals(PracticeStage.Apply, c.session.stage)
        c.advance()
        assertEquals(PracticeStage.Apply, c.session.stage) // New recording still required.
    }

    @Test fun failedRetrySaveKeepsFirstAndCannotAdvanceWithoutASavedRetry() {
        val c = reachRetry()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.startRecording(); store.failSave = true; c.stopRecording(); c.advance()
        assertEquals(PracticeStage.Retry, c.session.stage)
        assertNull(c.session.recordings[RecordingSlot.Retry])
        assertEquals(mapOf(RecordingSlot.First to first), c.session.recordings)
        assertEquals(SelfAssessment.Unsure, c.session.firstAssessment)
        assertTrue(first in audio.files)
        assertEquals(2, audio.files.size)
        assertTrue(c.state.value.hasPendingRecording)
        store.failSave = false
        val restored = controller()
        assertEquals(PracticeStage.Retry, restored.session.stage)
        assertEquals(first, restored.session.recordings[RecordingSlot.First])
        record(restored); restored.advance()
        assertEquals(PracticeStage.Compare, restored.session.stage)
    }

    @Test fun nativeRetryFailurePreservesOriginalAndItsAssessment() {
        val c = reachRetry()
        val before = c.session.recordings
        c.startRecording(); audio.recordingError!!()
        assertFalse(c.isRecording)
        assertEquals(before, c.session.recordings)
        assertEquals(SelfAssessment.Unsure, c.session.firstAssessment)
        assertTrue(c.message!!.contains("interrupted"))
        c.advance()
        assertEquals(PracticeStage.Retry, c.session.stage)
    }

    @Test fun backgroundFinalizesPartialRetryAndRecreationRetainsBothReferences() {
        val c = reachRetry()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.startRecording(); c.onBackground()
        assertFalse(c.isRecording)
        val retry = c.session.recordings.getValue(RecordingSlot.Retry)
        assertNotEquals(first.id, retry.id)
        assertTrue(c.message!!.contains("saved"))
        val restored = controller()
        assertEquals(c.session, restored.session)
        restored.advance()
        assertEquals(PracticeStage.Compare, restored.session.stage)
    }

    @Test fun processDeathDuringRetryRestoresUnfinishedStepWithoutReplacingFirst() {
        val c = reachRetry()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.startRecording()
        assertTrue(store.value.recordingInProgress)
        val newAudio = RetryAudio().apply { files += audio.files }
        val restored = PracticeController(newAudio, store)
        assertEquals(PracticeStage.Retry, restored.session.stage)
        assertFalse(restored.isRecording)
        assertFalse(restored.session.recordingInProgress)
        assertNull(restored.session.recordings[RecordingSlot.Retry])
        assertEquals(first, restored.session.recordings[RecordingSlot.First])
        assertEquals(SelfAssessment.Unsure, restored.session.firstAssessment)
        assertTrue(restored.message!!.contains("interrupted"))
    }

    @Test fun returningToComparisonAfterBackgroundAndRestartCreatesNoAttempts() {
        val c = reachCompare()
        val refs = c.session.recordings
        val writes = store.writes
        c.play(refs.getValue(RecordingSlot.Retry)); c.onBackground()
        assertNull(c.playingId); assertNull(audio.activePlayer)
        val restored = controller()
        assertEquals(PracticeStage.Compare, restored.session.stage)
        assertEquals(refs, restored.session.recordings)
        assertEquals(2, audio.starts)
        assertEquals(2, audio.stops)
        assertEquals(writes, store.writes)
        assertEquals(SelfAssessment.Unsure, restored.session.firstAssessment)
    }

    @Test fun startingRetryStopsPlaybackAndRecordingBlocksBothPlayers() {
        val c = reachRetry()
        val first = c.session.recordings.getValue(RecordingSlot.First)
        c.play(first)
        c.startRecording()
        assertNull(c.playingId); assertNull(audio.activePlayer)
        val plays = audio.events.count { it.startsWith("play:") }
        c.play(first)
        assertEquals(plays, audio.events.count { it.startsWith("play:") })
        assertTrue(c.isRecording)
        c.stopRecording()
        assertTrue(audio.exists(first))
    }
}
private class RetryStore : ProgressRepository {
    var value = PracticeSession()
    var writes = 0
    var failSave = false
    override fun load() = value
    override fun save(session: SessionProgress) { check(!failSave); value = session; writes++ }
}
private class RetryAudio : PracticeAudio {
    val files = mutableSetOf<RecordingRef>()
    val events = mutableListOf<String>()
    val finished = mutableListOf<() -> Unit>()
    val playErrors = mutableListOf<() -> Unit>()
    var starts = 0
    var stops = 0
    var activePlayer: String? = null
    var recordingError: (() -> Unit)? = null
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) { starts++; recordingError = onError }
    override fun stop() = RecordingRef("attempt-${++stops}", 35000).also { files += it }
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = recording in files
    override fun delete(recording: RecordingRef) { files -= recording }
    override fun deleteAllRecordings() { files.clear() }
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {
        check(activePlayer == null && exists(recording))
        activePlayer = recording.id; events += "play:${recording.id}"
        finished += { if (activePlayer == recording.id) activePlayer = null; onFinished() }
        playErrors += onError
    }
    override fun stopPlayback() { activePlayer?.let { events += "stop:$it" }; activePlayer = null }
}
