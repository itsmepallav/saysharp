package com.saysharp.shared.session

import com.saysharp.shared.practice.*
import com.saysharp.shared.platform.*
import com.saysharp.shared.lesson.SelfAssessment
import kotlin.test.*

class PersistenceRecoveryTest {
    private val first = RecordingRef("first",35000)
    private val retry = RecordingRef("retry",35000)
    private val transfer = RecordingRef("transfer",35000)
    private fun valid(day: Int = 1) = PracticeSession(day = day,
        stage = if (day in listOf(7,14)) PracticeStage.Summary else PracticeStage.Apply,
        completedDays = (1 until day).associateWith { DayReview() }).withRequiredExercise()

    @Test fun metadataFailureRetainsFinishedAudioAndRetrySaveIsIdempotent() {
        val store = RecoveryStore(PracticeSession(stage = PracticeStage.Retry, recordings = mapOf(RecordingSlot.First to first), firstAssessment = SelfAssessment.Unsure))
        val audio = RecoveryAudio().apply { files += first }
        val c = PracticeController(audio, store)
        c.startRecording(); store.fail = true; c.stopRecording()
        assertTrue(c.state.value.hasPendingRecording)
        assertEquals(first, c.session.recordings[RecordingSlot.First])
        assertNull(c.session.recordings[RecordingSlot.Retry])
        assertTrue(audio.exists(retry))
        c.advance(); c.startRecording()
        assertEquals(1, audio.starts)
        assertEquals(PracticeStage.Retry,c.session.stage)
        store.fail = false
        c.retryPendingSave(); val writes = store.writes; c.retryPendingSave()
        assertEquals(writes,store.writes)
        assertEquals(retry,c.session.recordings[RecordingSlot.Retry])
        assertEquals(SelfAssessment.Unsure,c.session.firstAssessment)
        assertNull(audio.pending)
    }
    @Test fun crashBetweenFinalizedFileAndMetadataCommitRecoversCorrectSlot() {
        for (slot in listOf(RecordingSlot.First,RecordingSlot.Retry,RecordingSlot.Application)) {
            val stage = when(slot) { RecordingSlot.First -> PracticeStage.FirstAttempt; RecordingSlot.Retry -> PracticeStage.Retry; else -> PracticeStage.Apply }
            val store = RecoveryStore(PracticeSession(stage=stage,recordingInProgress=true))
            val audio = RecoveryAudio().apply { pending=retry; files+=retry }
            val c = PracticeController(audio,store)
            assertEquals(retry,c.session.recordings[slot])
            assertFalse(c.session.recordingInProgress)
            assertNull(audio.pending)
            assertTrue(c.message!!.contains("recovered"))
            assertEquals(c.session,PracticeController(audio,store).session)
        }
    }
    @Test fun failedRecoveryWriteRemainsRetryableAndDoesNotDiscardFile() {
        val store=RecoveryStore(PracticeSession(stage=PracticeStage.Retry,recordingInProgress=true)).apply { fail=true }
        val audio=RecoveryAudio().apply { pending=retry; files+=retry }
        val c=PracticeController(audio,store)
        assertTrue(c.state.value.hasPendingRecording)
        assertTrue(c.beginNextDay()) // Resume must expose Retry save, even after relaunch.
        assertTrue(audio.exists(retry))
        assertNull(c.session.recordings[RecordingSlot.Retry])
        store.fail=false; c.retryPendingSave()
        assertEquals(retry,store.value.recordings[RecordingSlot.Retry])
    }
    @Test fun crashAfterMetadataCommitOnlyAcknowledgesMarkerWithoutDuplicateAttempt() {
        val store=RecoveryStore(PracticeSession(stage=PracticeStage.Retry,recordings=mapOf(RecordingSlot.Retry to retry)))
        val audio=RecoveryAudio().apply { pending=retry; files+=retry }
        val c=PracticeController(audio,store)
        assertEquals(0,store.writes)
        assertEquals(retry,c.session.recordings[RecordingSlot.Retry])
        assertNull(audio.pending)
    }
    @Test fun unfinishedMicrophoneOutputIsNotPromotedOnRestart() {
        val store=RecoveryStore(PracticeSession(stage=PracticeStage.Retry,recordingInProgress=true,recordings=mapOf(RecordingSlot.First to first)))
        val audio=RecoveryAudio().apply { files+=first }
        val c=PracticeController(audio,store)
        assertNull(c.session.recordings[RecordingSlot.Retry])
        assertEquals(first,c.session.recordings[RecordingSlot.First])
        assertFalse(c.isRecording)
        assertTrue(c.message!!.contains("interrupted"))
    }
    @Test fun missingActiveFileRepairsStageAndAssessmentDurablyWithoutDeletingOthers() {
        val store=RecoveryStore(valid().copy(recordings=mapOf(RecordingSlot.First to first,RecordingSlot.Retry to retry,RecordingSlot.Application to transfer)))
        val audio=RecoveryAudio().apply { files+=listOf(retry,transfer) }
        val c=PracticeController(audio,store)
        assertEquals(PracticeStage.FirstAttempt,c.session.stage)
        assertNull(c.session.firstAssessment)
        assertNull(c.session.recordings[RecordingSlot.First])
        assertEquals(setOf(retry,transfer),audio.files)
        assertEquals(c.session,store.value)
    }
    @Test fun missingHistoricalFilesAreReportedWithoutReopeningCompletedLessons() {
        val review=DayReview(recordings=mapOf(RecordingSlot.First to first,RecordingSlot.Retry to retry))
        val store=RecoveryStore(PracticeSession(stage=PracticeStage.Complete,recordings=review.recordings,completedDays=mapOf(1 to review)))
        val audio=RecoveryAudio().apply { files+=retry }
        val c=PracticeController(audio,store)
        assertEquals(PracticeStage.Complete,c.session.stage)
        assertEquals(setOf(1),c.session.completedDays.keys)
        assertFalse(RecordingSlot.First in c.session.completedDays.getValue(1).recordings)
        assertTrue(c.message!!.contains("unavailable"))
        assertTrue(audio.exists(retry))
    }
    @Test fun finalStageAloneCannotCompleteAndFileLossBeforeCompletionRepairsInstead() {
        val empty=PracticeSession(stage=PracticeStage.Apply,recordings=mapOf(RecordingSlot.Application to transfer))
        assertEquals(empty,advanceSession(empty,BundledSessionRepository.definition(1)))
        val store=RecoveryStore(valid())
        val audio=RecoveryAudio().apply { files+=store.value.recordings.values }
        val c=PracticeController(audio,store)
        audio.files.remove(store.value.recordings.getValue(RecordingSlot.Retry))
        c.advance()
        assertEquals(PracticeStage.Retry,c.session.stage)
        assertTrue(c.session.completedDays.isEmpty())
    }
    @Test fun blankProvidedAndSkippedReflectionNeverDuplicateCompletion() {
        for (note in listOf("", "Use this when explaining my plan.")) {
            val store=RecoveryStore(valid())
            val audio=RecoveryAudio().apply { files+=store.value.recordings.values }
            var c=PracticeController(audio,store)
            c.advance(); c.advance()
            assertEquals(setOf(1),c.session.completedDays.keys)
            assertTrue(c.saveWin(note))
            val writes=store.writes
            assertTrue(c.saveWin(note)); c.advance()
            assertEquals(writes,store.writes)
            c=PracticeController(audio,store)
            assertEquals(note,c.session.realLifeWin)
            assertEquals(note,c.session.completedDays.getValue(1).realLifeWin)
            assertEquals(1,c.state.value.programme.completedCount)
            assertEquals(2,c.state.value.programme.currentLesson?.day)
        }
    }
    @Test fun failedReflectionSaveKeepsCompletionAndAllowsRetry() {
        val store=RecoveryStore(valid())
        val audio=RecoveryAudio().apply { files+=store.value.recordings.values }
        val c=PracticeController(audio,store);c.advance()
        store.fail=true
        assertFalse(c.saveWin("Local reflection"))
        assertEquals(PracticeStage.Complete,c.session.stage)
        assertEquals("",c.session.completedDays.getValue(1).realLifeWin)
        store.fail=false;assertTrue(c.saveWin("Local reflection"))
    }
    @Test fun checkpointsRequireBothRecordingsReplaysAndTheirSpecificAssessment() {
        for (day in listOf(7,14)) {
            val complete=valid(day)
            assertTrue(readyToComplete(complete,BundledSessionRepository.definition(day)))
            val missing=complete.copy(recordings=complete.recordings-RecordingSlot.CheckpointFresh)
            assertEquals(missing,advanceSession(missing,BundledSessionRepository.definition(day)))
            assertFalse(readyToComplete(complete.copy(retryAssessment=null),BundledSessionRepository.definition(day)))
        }
    }
}
private class RecoveryStore(var value:PracticeSession):ProgressRepository {
    var fail=false;var writes=0
    override fun load()=value
    override fun save(session:SessionProgress){check(!fail);value=session;writes++}
}
private class RecoveryAudio:PracticeAudio {
    val files=mutableSetOf<RecordingRef>();var pending:RecordingRef?=null;var starts=0
    override fun start(limitMillis:Long,onLimit:()->Unit,onError:()->Unit){starts++}
    override fun stop()=RecordingRef("retry",35000).also{files+=it;pending=it}
    override fun pendingRecording()=pending
    override fun acknowledgeRecording(recording:RecordingRef){if(pending==recording)pending=null}
    override fun cancel(){}
    override fun exists(recording:RecordingRef)=recording in files
    override fun delete(recording:RecordingRef){files-=recording}
    override fun deleteAllRecordings(){files.clear()}
    override fun monotonicMillis()=0L
    override fun play(recording:RecordingRef,openingOnly:Boolean,onFinished:()->Unit,onError:()->Unit){}
    override fun stopPlayback(){}
}
