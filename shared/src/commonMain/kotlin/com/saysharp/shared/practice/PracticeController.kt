package com.saysharp.shared.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.lesson.Programme
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.telemetry.Telemetry

class PracticeController(private val audio: AudioRecorder, private val store: SessionStore, private val telemetry: Telemetry = Telemetry.None) {
    private var saveBlocked = false
    var message: String? by mutableStateOf(null)
        private set
    var session by mutableStateOf(restore())
        private set
    var isRecording by mutableStateOf(false)
        private set
    var elapsedMillis by mutableStateOf(0L)
        private set
    var playingId: String? by mutableStateOf(null)
        private set
    private var startedAt = 0L
    val limitMillis: Long get() = when (session.stage) {
        PracticeStage.Baseline, PracticeStage.CheckpointRepeat, PracticeStage.CheckpointFresh -> 60_000
        PracticeStage.Retry, PracticeStage.Apply -> if (session.day == 4) 20_000 else 45_000
        else -> 45_000
    }
    val nextDay: Int get() = if (session.stage == PracticeStage.Complete) (session.day + 1).coerceAtMost(14) else session.day

    private fun restore(): PracticeSession = try {
        val loaded = store.load()
        // Old Day 1 installs had no history. Archive completion without changing audio IDs.
        val saved = if (loaded.stage == PracticeStage.Complete && loaded.day !in loaded.completedDays)
            loaded.copy(completedDays = loaded.completedDays + (loaded.day to loaded.review())) else loaded
        val missing = saved.recordings.filterValues { !audio.exists(it) }.keys
        if (missing.isEmpty() || saved.stage == PracticeStage.Complete) saved else {
            telemetry.track("recording_missing", "day" to saved.day, "count" to missing.size)
            message = "A saved recording is missing. Please record that step again."
            val stage = when {
                RecordingSlot.Baseline in missing -> PracticeStage.Baseline
                RecordingSlot.First in missing -> PracticeStage.FirstAttempt
                RecordingSlot.Retry in missing -> PracticeStage.Retry
                RecordingSlot.Application in missing -> PracticeStage.Apply
                RecordingSlot.Vocabulary in missing -> PracticeStage.Vocabulary
                RecordingSlot.CheckpointRepeat in missing -> PracticeStage.CheckpointRepeat
                RecordingSlot.CheckpointFresh in missing -> PracticeStage.CheckpointFresh
                RecordingSlot.Recall in missing -> PracticeStage.Recall
                else -> PracticeStage.Apply
            }
            saved.copy(stage = stage, recordings = saved.recordings - missing)
        }
    } catch (error: Exception) {
        telemetry.error("practice_restore", error)
        // Keep the original file intact. Do not overwrite unreadable user data.
        saveBlocked = true
        message = "Saved practice could not be opened. Your files have been kept. Try reopening the app."
        PracticeSession()
    }

    fun beginNextDay(): Boolean {
        if (session.stage != PracticeStage.Complete) return !saveBlocked
        if (session.day >= 14) return true
        stopPlayback()
        val day = session.day + 1
        val baseline = session.recordings.filterKeys { it == RecordingSlot.Baseline }.filterValues { audio.exists(it) }
        val entry = if (baseline.isEmpty()) PracticeStage.Baseline else entryStage(day)
        return commit(PracticeSession(
            day = day, stage = entry,
            recordings = baseline,
            completedDays = session.completedDays + (session.day to session.review()),
        ))
    }

    private fun entryStage(day: Int) = when {
            Programme.checkpoint(day) != null -> PracticeStage.CheckpointRepeat
            Programme.recalls(day).isNotEmpty() -> PracticeStage.Recall
            else -> PracticeStage.Lesson
    }

    private fun commit(next: PracticeSession): Boolean {
        if (saveBlocked) return false
        return try {
            store.save(next)
            session = next
            telemetry.context("day" to next.day, "stage" to next.stage.name)
            message = null
            true
        } catch (error: Exception) {
            telemetry.error("practice_save", error)
            message = "Could not save this step. Check available storage and try again."
            false
        }
    }

    fun advance() {
        if (isRecording) return
        val s = session
        if (s.stage == PracticeStage.Complete) return
        val slot = s.stage.recordingSlot()
        if (slot != null && slot !in s.recordings) return
        val canAdvance = when (s.stage) {
            PracticeStage.FirstCheck -> s.firstAssessment != null
            PracticeStage.Compare, PracticeStage.CheckpointCompare -> s.retryAssessment != null && s.comparison != null
            PracticeStage.Vocabulary -> s.vocabularyAssessment != null
            PracticeStage.Recall -> s.recallAssessment != null
            else -> true
        }
        if (!canAdvance) return
        val stages = practiceStages(s)
        val index = stages.indexOf(s.stage)
        val next = stages.getOrNull(index + 1)?.takeIf { index >= 0 } ?: return
        stopPlayback()
        if (commit(if (next == PracticeStage.Complete) s.copy(stage = next, completedDays = s.completedDays + (s.day to s.review())) else s.copy(stage = next))) {
            telemetry.track("practice_step_completed", "day" to s.day, "stage" to s.stage.name)
            if (next == PracticeStage.Complete) {
                telemetry.track("chapter_completed", "day" to s.day,
                    "completed_count" to session.completedDays.size, "comparison" to (s.comparison?.name ?: "none"),
                    "chapter_type" to if (Programme.checkpoint(s.day) != null) "checkpoint" else "practice")
                if (s.day == 14) telemetry.track("programme_completed", "completed_count" to session.completedDays.size)
            }
        }
    }

    fun permissionDenied() {
        telemetry.track("recording_blocked", "day" to session.day, "stage" to session.stage.name, "reason" to "permission")
        message = "Microphone permission is needed to record. Try again, or enable it in Android app settings."
    }

    fun startRecording() {
        if (isRecording || saveBlocked || session.stage.recordingSlot() == null) return
        stopPlayback()
        message = null
        try {
            audio.start(limitMillis, onLimit = { stopRecording() }, onError = {
                telemetry.error("recording_interrupted")
                telemetry.track("recording_failed", "day" to session.day, "stage" to session.stage.name, "reason" to "platform_callback")
                audio.cancel()
                isRecording = false
                message = "Recording was interrupted. Your earlier attempt is safe. Please try again."
            })
            startedAt = audio.monotonicMillis()
            elapsedMillis = 0
            isRecording = true
            telemetry.track("recording_started", "day" to session.day, "stage" to session.stage.name,
                "slot" to requireNotNull(session.stage.recordingSlot()).name,
                "is_retake" to (session.stage.recordingSlot() in session.recordings))
        } catch (error: Exception) {
            telemetry.error("recording_start", error)
            telemetry.track("recording_failed", "day" to session.day, "stage" to session.stage.name, "reason" to "start")
            audio.cancel()
            message = "Could not start recording. Check microphone permission and available storage, then try again."
        }
    }

    fun tick() {
        if (!isRecording) return
        elapsedMillis = (audio.monotonicMillis() - startedAt).coerceAtLeast(0).coerceAtMost(limitMillis)
        if (elapsedMillis >= limitMillis) stopRecording()
    }

    fun stopRecording(interrupted: Boolean = false) {
        if (!isRecording) return
        val slot = session.stage.recordingSlot() ?: return
        isRecording = false
        try {
            val recording = audio.stop()
            if (recording.durationMillis < 1_000) {
                telemetry.track("recording_rejected", "day" to session.day, "slot" to slot.name, "reason" to "too_short")
                audio.delete(recording)
                message = "That was too short to keep. Record at least one second and try again."
                return
            }
            val previous = session.recordings[slot]
            if (commit(session.copy(recordings = session.recordings + (slot to recording)))) {
                telemetry.track("recording_saved", "day" to session.day, "slot" to slot.name,
                    "duration_seconds" to recording.durationMillis / 1_000, "is_retake" to (previous != null), "interrupted" to interrupted)
                previous?.let { runCatching { audio.delete(it) } }
                if (interrupted) message = "Recording stopped when you left the app. Your attempt was saved."
            } else {
                // The previous attempt remains referenced and playable if saving fails.
                runCatching { audio.delete(recording) }
            }
        } catch (error: Exception) {
            telemetry.error("recording_save", error)
            telemetry.track("recording_failed", "day" to session.day, "slot" to slot.name, "reason" to "save")
            audio.cancel()
            message = "Could not save a playable recording. Your earlier attempt is safe. Please try again."
        }
    }

    fun assess(value: SelfAssessment, retry: Boolean = false) {
        val old = if (retry) session.retryAssessment else session.firstAssessment
        val allowed = if (retry) session.stage in listOf(PracticeStage.Compare, PracticeStage.CheckpointCompare) else session.stage == PracticeStage.FirstCheck
        if (allowed && old != value && commit(if (retry) session.copy(retryAssessment = value) else session.copy(firstAssessment = value))) {
            telemetry.track("self_assessment_saved", "day" to session.day, "stage" to session.stage.name, "choice" to value.name)
        }
    }

    fun compare(value: Comparison) {
        if (session.stage in listOf(PracticeStage.Compare, PracticeStage.CheckpointCompare) && session.comparison != value && commit(session.copy(comparison = value))) {
            telemetry.track("comparison_saved", "day" to session.day, "choice" to value.name)
        }
    }

    fun assessVocabulary(value: SelfAssessment) {
        val next = when (session.stage) {
            PracticeStage.Vocabulary -> if (session.vocabularyAssessment != value) session.copy(vocabularyAssessment = value) else return
            PracticeStage.Recall -> if (session.recallAssessment != value) session.copy(recallAssessment = value) else return
            else -> return
        }
        if (commit(next)) telemetry.track("self_assessment_saved", "day" to session.day, "stage" to session.stage.name, "choice" to value.name)
    }

    fun revealRecall() {
        if (session.stage == PracticeStage.Recall && !session.recallHintUsed && commit(session.copy(recallHintUsed = true))) {
            telemetry.track("recall_hint_used", "day" to session.day)
        }
    }

    fun saveWin(text: String, day: Int = session.day): Boolean {
        val note = text.take(500)
        val previous = if (day == session.day) session.realLifeWin else session.completedDays[day]?.realLifeWin
        val history = session.completedDays[day]?.let { session.completedDays + (day to it.copy(realLifeWin = note)) } ?: session.completedDays
        if (day != session.day && day !in history) return false
        val saved = commit(session.copy(realLifeWin = if (day == session.day) note else session.realLifeWin, completedDays = history))
        if (saved && note != previous) telemetry.track("real_life_win_saved", "day" to day, "has_note" to note.isNotBlank())
        return saved
    }

    fun baseline(): RecordingRef? = session.recordings[RecordingSlot.Baseline]

    fun play(recording: RecordingRef, openingOnly: Boolean = false) {
        if (isRecording) return
        if (playingId == recording.id) { stopPlayback(); return }
        stopPlayback()
        playingId = recording.id
        try {
            val currentSlot = session.recordings.entries.firstOrNull { it.value.id == recording.id }?.key
            val historical = session.completedDays.entries.firstOrNull { (_, review) -> review.recordings.values.any { it.id == recording.id } }
            val slot = currentSlot ?: historical?.value?.recordings?.entries?.firstOrNull { it.value.id == recording.id }?.key
            val day = if (currentSlot != null) session.day else historical?.key ?: session.day
            audio.play(recording, openingOnly, onFinished = { playingId = null }, onError = {
                telemetry.error("playback_callback")
                playingId = null
                message = "Could not play this recording. Try again or record a new attempt."
            })
            telemetry.track("playback_started", "day" to day, "slot" to (slot?.name ?: "unknown"), "opening_only" to openingOnly)
        } catch (error: Exception) {
            telemetry.error("playback_start", error)
            stopPlayback()
            message = "Playback is unavailable right now. Check your audio output and try again."
        }
    }

    fun stopPlayback() { audio.stopPlayback(); playingId = null }

    fun onBackground() {
        stopRecording(interrupted = true)
        stopPlayback()
    }

    fun clearData() {
        onBackground()
        try {
            // Also removes orphan attempts left by process death or a failed replacement.
            audio.deleteAllRecordings()
            saveBlocked = false
            if (commit(PracticeSession())) telemetry.track("practice_data_deleted")
        } catch (error: Exception) {
            telemetry.error("practice_delete", error)
            message = "Some recordings could not be deleted. Please try again."
        }
    }
}
