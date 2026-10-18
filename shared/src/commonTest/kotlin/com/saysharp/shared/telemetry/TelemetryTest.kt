package com.saysharp.shared.telemetry

import com.saysharp.shared.engagement.*
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import kotlin.test.*
import com.saysharp.shared.session.withRequiredExercise

class TelemetryTest {
    private val sink = CapturingBackend()
    private val telemetry = Telemetry(sink)
    private val store = TrackingSessionStore()
    private val audio = TrackingAudio()

    @Test fun completedChapterIsReportedOnceOnlyAfterPersistence() {
        store.session = PracticeSession(day = 2, stage = PracticeStage.Apply, recordings = mapOf(RecordingSlot.Application to RecordingRef("transfer", 35000)), completedDays = mapOf(1 to DayReview())).withRequiredExercise()
        val controller = PracticeController(audio, store, telemetry)
        assertTrue(sink.events.isEmpty())
        controller.advance()
        controller.advance()
        PracticeController(audio, store, telemetry) // Restoring completed history isn't a new completion.
        assertEquals(1, sink.events.count { it.first == "chapter_completed" })
        assertEquals(2, sink.events.first { it.first == "chapter_completed" }.second["day"])
    }

    @Test fun failedSaveReportsErrorWithoutReportingCompletion() {
        store.session = PracticeSession(day = 2, stage = PracticeStage.Apply, recordings = mapOf(RecordingSlot.Application to RecordingRef("transfer", 35000)), completedDays = mapOf(1 to DayReview())).withRequiredExercise()
        store.failSave = true
        val controller = PracticeController(audio, store, telemetry)
        controller.advance()
        assertEquals(PracticeStage.Apply, controller.session.stage)
        assertTrue(sink.events.none { it.first in listOf("chapter_completed", "practice_step_completed") })
        assertEquals(listOf("practice_save"), sink.errors)
    }

    @Test fun retakesReportOnlySavedAttemptsAndPreservePreviousOnFailure() {
        store.session = PracticeSession(stage = PracticeStage.Baseline)
        val controller = PracticeController(audio, store, telemetry)
        controller.startRecording(); controller.stopRecording()
        val original = controller.baseline()
        controller.startRecording()
        store.failSave = true
        controller.stopRecording()
        assertEquals(original, controller.baseline())
        assertEquals(1, sink.events.count { it.first == "recording_saved" })
        val starts = sink.events.filter { it.first == "recording_started" }
        assertEquals(listOf(false, true), starts.map { it.second["is_retake"] })
        val saved = sink.events.first { it.first == "recording_saved" }.second
        assertEquals(2L, saved["duration_seconds"])
        assertTrue(saved.values.none { it.toString().contains("private-recording") })
    }

    @Test fun realLifeNotesAreNeverEventParametersAndUnchangedNotesDoNotRepeat() {
        val controller = PracticeController(audio, store, telemetry)
        val secret = "Private conversation with someone named Alice"
        assertTrue(controller.saveWin(secret))
        assertTrue(controller.saveWin(secret))
        val events = sink.events.filter { it.first == "real_life_win_saved" }
        assertEquals(1, events.size)
        assertEquals(mapOf("day" to 1, "has_note" to true), events.single().second)
        assertFalse(sink.events.toString().contains(secret))
    }

    @Test fun brokenTelemetryCannotPreventSavingOrCompletingPractice() {
        val broken = Telemetry(object : TelemetryBackend {
            override fun event(name: String, parameters: Map<String, Any>) { error("SDK unavailable") }
            override fun context(parameters: Map<String, Any>) { error("SDK unavailable") }
            override fun error(operation: String, error: Throwable?) { error("SDK unavailable") }
        })
        broken.error("test")
        broken.screen("today")
        store.session = PracticeSession(day = 2, stage = PracticeStage.Apply, recordings = mapOf(RecordingSlot.Application to RecordingRef("transfer", 35000)), completedDays = mapOf(1 to DayReview())).withRequiredExercise()
        val controller = PracticeController(audio, store, broken)
        controller.advance()
        assertEquals(PracticeStage.Complete, store.session.stage)
    }

    @Test fun onboardingReplayAndFailedPersistenceDoNotRepeatFirstCompletion() {
        val preferences = object : EngagementStore {
            var state = EngagementState()
            var failSave = true
            override fun load() = state
            override fun save(state: EngagementState) { check(!failSave); this.state = state }
        }
        val controller = EngagementController(preferences, telemetry)
        assertFalse(controller.finishWelcome(SpeakingGoal.Everyday))
        assertTrue(sink.events.isEmpty())
        preferences.failSave = false
        assertTrue(controller.finishWelcome(SpeakingGoal.Everyday, skipped = true))
        assertTrue(controller.finishWelcome(SpeakingGoal.Everyday))
        assertEquals(1, sink.events.count { it.first == "tutorial_complete" })
        assertEquals(true, sink.events.first { it.first == "tutorial_complete" }.second["skipped"])
        assertEquals(1, sink.events.count { it.first == "welcome_replayed" })
    }
}

private class CapturingBackend : TelemetryBackend {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val errors = mutableListOf<String>()
    override fun event(name: String, parameters: Map<String, Any>) { events += name to parameters }
    override fun context(parameters: Map<String, Any>) {}
    override fun error(operation: String, error: Throwable?) { errors += operation }
}

private class TrackingSessionStore : SessionStore {
    var session = PracticeSession()
    var failSave = false
    override fun load() = session
    override fun save(session: PracticeSession) { check(!failSave); this.session = session }
}

private class TrackingAudio : PracticeAudio {
    var sequence = 0
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("private-recording-${sequence++}", 2_000)
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {}
    override fun stopPlayback() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
}
