package com.saysharp.shared.practice

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import kotlin.test.*

class PracticeControllerTest {
    private val audio = FakeAudio()
    private val store = MemoryStore()
    private fun controller() = PracticeController(audio, store)
    private fun record(c: PracticeController, duration: Long = 2_000) {
        audio.duration = duration
        c.startRecording()
        c.stopRecording()
    }

    @Test fun completeSessionAllowsNotYetAndUnsureWithoutChangingBaseline() {
        val c = controller()
        c.advance()
        record(c)
        val baseline = c.session.recordings[RecordingSlot.Baseline]
        c.advance(); c.advance()
        record(c); c.advance()
        c.assess(SelfAssessment.NotYet); c.advance()
        record(c); c.advance()
        c.assess(SelfAssessment.Unsure, retry = true)
        c.compare(Comparison.LessClear); c.advance()
        assertEquals(PracticeStage.Apply, c.session.stage)
        record(c); c.advance()
        assertEquals(PracticeStage.Summary, c.session.stage)
        c.advance()
        assertEquals(PracticeStage.Complete, c.session.stage)
        c.startRecording()
        assertFalse(c.isRecording)
        assertEquals(baseline, c.session.recordings[RecordingSlot.Baseline])
        assertEquals(4, c.session.recordings.size)
        assertEquals(c.session, store.saved)
    }

    @Test fun cannotSkipRequiredRecordingOrAssessment() {
        val c = controller()
        c.advance(); c.advance()
        assertEquals(PracticeStage.Baseline, c.session.stage)
        record(c); c.advance(); c.advance(); record(c); c.advance()
        c.advance()
        assertEquals(PracticeStage.FirstCheck, c.session.stage)
    }

    @Test fun elapsedTimeUsesClockAndStopsAtLimit() {
        val c = controller()
        c.advance(); c.startRecording()
        assertEquals(60_000L, audio.limit)
        audio.now = 60_500
        c.tick()
        assertFalse(c.isRecording)
        assertEquals(60_000L, c.elapsedMillis)
        c.advance(); c.advance(); c.startRecording()
        assertEquals(45_000L, audio.limit)
        audio.now += 45_100
        c.tick()
        assertFalse(c.isRecording)
        assertNotNull(c.session.recordings[RecordingSlot.First])
    }

    @Test fun failedReplacementPreservesEarlierAttempt() {
        val c = controller()
        c.advance(); record(c)
        val original = c.session.recordings[RecordingSlot.Baseline]
        store.failSave = true
        record(c)
        assertEquals(original, c.session.recordings[RecordingSlot.Baseline])
        assertTrue(original in audio.files)
        assertEquals(1, audio.files.size)
        assertNotNull(c.message)
    }

    @Test fun tooShortReplacementPreservesEarlierAttempt() {
        val c = controller()
        c.advance(); record(c)
        val original = c.session.recordings[RecordingSlot.Baseline]
        record(c, 200)
        assertEquals(original, c.session.recordings[RecordingSlot.Baseline])
        assertEquals(1, audio.files.size)
        assertNotNull(c.message)
    }

    @Test fun backgroundSavesAttemptAndRestoresAfterRecreation() {
        val c = controller()
        c.advance(); c.startRecording(); c.onBackground()
        assertFalse(c.isRecording)
        assertNotNull(c.session.recordings[RecordingSlot.Baseline])
        assertEquals(c.session, controller().session)
        assertTrue(c.message!!.contains("left the app"))
    }

    @Test fun failedSaveDoesNotAdvanceStage() {
        val c = controller()
        store.failSave = true
        c.advance()
        assertEquals(PracticeStage.Welcome, c.session.stage)
        assertEquals(PracticeSession(), store.saved)
    }

    @Test fun unreadableSessionCannotBeSilentlyOverwritten() {
        store.failLoad = true
        val c = controller()
        c.advance(); c.startRecording()
        assertFalse(c.isRecording)
        assertEquals(0, store.saves)
        assertNotNull(c.message)
    }

    @Test fun missingFileReopensAffectedRecordingStep() {
        val c = controller()
        c.advance(); record(c); c.advance(); c.advance(); record(c); c.advance()
        audio.files.remove(c.session.recordings[RecordingSlot.First])
        val restored = controller()
        assertEquals(PracticeStage.FirstAttempt, restored.session.stage)
        assertNotNull(restored.session.recordings[RecordingSlot.Baseline])
        assertNull(restored.session.recordings[RecordingSlot.First])
    }

    @Test fun audioInterruptionDoesNotReplaceSavedAttempt() {
        val c = controller()
        c.advance(); record(c)
        val original = c.session.recordings[RecordingSlot.Baseline]
        c.startRecording()
        audio.onError!!()
        assertFalse(c.isRecording)
        assertEquals(original, c.session.recordings[RecordingSlot.Baseline])
        assertNotNull(c.message)
    }

    @Test fun deletingPracticeClearsRecordingsAndMetadata() {
        val c = controller()
        c.advance(); record(c); c.saveWin("A clear answer")
        audio.files += RecordingRef("orphan", 2000)
        c.clearData()
        assertEquals(PracticeSession(), c.session)
        assertTrue(audio.files.isEmpty())
        assertEquals(PracticeSession(), store.saved)
    }

    @Test fun deletionFailureIsNotReportedAsSuccess() {
        val c = controller()
        c.advance(); record(c)
        audio.failDelete = true
        c.clearData()
        assertNotEquals(PracticeSession(), c.session)
        assertNotNull(c.message)
    }

    @Test fun playbackEndsAndStopsWhenAppLeavesForeground() {
        val c = controller()
        c.advance(); record(c)
        val ref = c.session.recordings.getValue(RecordingSlot.Baseline)
        c.play(ref, openingOnly = true)
        assertEquals(ref.id, c.playingId)
        audio.finished!!()
        assertNull(c.playingId)
        c.play(ref); c.onBackground()
        assertNull(c.playingId)
        assertFalse(audio.playing)
    }

    @Test fun completeProgrammePreservesEveryDayAndUsesDistinctCheckpoints() {
        val c = controller()
        var baseline: RecordingRef? = null
        for (day in 1..14) {
            assertEquals(day, c.session.day)
            val stages = mutableSetOf<PracticeStage>()
            val expectedRoute = practiceStages(c.session)
            var steps = 0
            while (c.session.stage != PracticeStage.Complete) {
                assertTrue(steps++ < 20, "Unexpected progression loop on day $day")
                val stage = c.session.stage
                stages += stage
                when (stage) {
                    PracticeStage.Baseline, PracticeStage.FirstAttempt, PracticeStage.Retry, PracticeStage.Apply,
                    PracticeStage.CheckpointRepeat, PracticeStage.CheckpointFresh -> record(c)
                    PracticeStage.FirstCheck -> c.assess(SelfAssessment.NotYet)
                    PracticeStage.Compare, PracticeStage.CheckpointCompare -> {
                        c.assess(SelfAssessment.Unsure, retry = true)
                        c.compare(Comparison.AboutTheSame)
                    }
                    PracticeStage.Vocabulary, PracticeStage.Recall -> {
                        record(c)
                        c.assessVocabulary(SelfAssessment.Unsure)
                    }
                    else -> Unit
                }
                c.advance()
                assertEquals(expectedRoute[steps], c.session.stage, "Wrong transition on day $day after $stage")
            }
            assertEquals(expectedRoute.dropLast(1), stages.toList(), "Every planned step must be visited once")
            if (day == 1) baseline = c.baseline()
            assertEquals(baseline, c.baseline())
            assertEquals((1..day).toSet(), c.session.completedDays.keys)
            if (day in listOf(7, 14)) {
                assertTrue(PracticeStage.CheckpointRepeat in stages && PracticeStage.CheckpointFresh in stages)
                assertFalse(PracticeStage.Lesson in stages)
                assertFalse(PracticeStage.Retry in stages)
            }
            assertTrue(c.beginNextDay())
        }
        assertEquals(14, c.session.day)
        assertEquals(PracticeStage.Complete, c.session.stage)
        assertEquals(50, audio.files.size)
        assertTrue(c.session.completedDays.values.flatMap { it.recordings.values }.all { it in audio.files })
        assertEquals(c.session, controller().session)
    }

    @Test fun completedDayOneFromOldInstallIsArchivedBeforeMovingOn() {
        val c = controller()
        c.advance(); record(c)
        val old = c.session.copy(stage = PracticeStage.Complete, realLifeWin = "Original note", completedDays = emptyMap())
        store.saved = old
        val upgraded = controller()
        assertEquals("Original note", upgraded.session.completedDays.getValue(1).realLifeWin)
        assertTrue(upgraded.beginNextDay())
        assertEquals(2, upgraded.session.day)
        assertEquals(old.recordings[RecordingSlot.Baseline], upgraded.baseline())
        assertEquals("Original note", store.saved.completedDays.getValue(1).realLifeWin)
    }

    @Test fun failureToStartNextDayKeepsCompletedSessionAndHistory() {
        store.saved = PracticeSession(stage = PracticeStage.Complete)
        val c = controller()
        val old = c.session
        store.failSave = true
        assertFalse(c.beginNextDay())
        assertEquals(old, c.session)
        assertNotNull(c.message)
    }

    @Test fun savingHistoricalWinDoesNotOverwriteCurrentDayNote() {
        store.saved = PracticeSession(day = 2, stage = PracticeStage.Lesson, realLifeWin = "Current", completedDays = mapOf(1 to DayReview(realLifeWin = "Earlier")))
        val c = controller()
        assertTrue(c.saveWin("Updated earlier", day = 1))
        assertEquals("Current", c.session.realLifeWin)
        assertEquals("Updated earlier", c.session.completedDays.getValue(1).realLifeWin)
        assertFalse(c.saveWin("Not completed", day = 10))
    }

    @Test fun hintUsePersistsAndUnsureDoesNotBlockRecall() {
        store.saved = PracticeSession(day = 4, stage = PracticeStage.Recall)
        val c = controller()
        c.revealRecall(); record(c)
        c.assessVocabulary(SelfAssessment.Unsure)
        val restored = controller()
        assertTrue(restored.session.recallHintUsed)
        restored.advance()
        assertEquals(PracticeStage.Lesson, restored.session.stage)
    }

    @Test fun concisionRetryHasATwentySecondLimit() {
        store.saved = PracticeSession(day = 4, stage = PracticeStage.Retry)
        val c = controller()
        c.startRecording()
        assertEquals(20_000L, audio.limit)
        audio.now = 20_100
        c.tick()
        assertFalse(c.isRecording)
    }
}

private class MemoryStore : SessionStore {
    var saved = PracticeSession()
    var failSave = false
    var failLoad = false
    var saves = 0
    override fun load(): PracticeSession { check(!failLoad); return saved }
    override fun save(session: PracticeSession) { check(!failSave); saves++; saved = session }
}

private class FakeAudio : AudioRecorder {
    var now = 0L
    var duration = 2_000L
    var limit = 0L
    var sequence = 0
    var playing = false
    var failDelete = false
    var onError: (() -> Unit)? = null
    var finished: (() -> Unit)? = null
    val files = mutableSetOf<RecordingRef>()
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) { limit = limitMillis; this.onError = onError }
    override fun stop() = RecordingRef("attempt-${sequence++}", duration).also { files += it }
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { playing = true; finished = onFinished }
    override fun stopPlayback() { playing = false }
    override fun exists(recording: RecordingRef) = recording in files
    override fun delete(recording: RecordingRef) { check(!failDelete); files -= recording }
    override fun deleteAllRecordings() { check(!failDelete); files.clear() }
    override fun monotonicMillis() = now
}
