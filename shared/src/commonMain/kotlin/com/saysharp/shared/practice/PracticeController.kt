package com.saysharp.shared.practice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.saysharp.shared.session.*
import com.saysharp.shared.platform.AudioPlayer
import com.saysharp.shared.platform.PracticeAudio
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.telemetry.Telemetry

class PracticeController(private val audio: AudioRecorder, private val store: ProgressRepository, private val telemetry: Telemetry = Telemetry.None,
    private val player: AudioPlayer,
    private val definitions: SessionRepository = BundledSessionRepository,
    private val scope: CoroutineScope? = null,
) {
    constructor(audio: PracticeAudio, store: ProgressRepository, telemetry: Telemetry = Telemetry.None) :
        this(audio, store, telemetry, audio)

    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private var saveBlocked = false
    private var pendingRecording: RecordingRef? = null
    private var recordingGeneration = 0L
    private var playbackGeneration = 0L
    var message: String?
        get() = state.value.message
        private set(value) { mutableState.value = state.value.copy(message = value) }
    var session: PracticeSession
        get() = state.value.progress
        private set(value) { mutableState.value = state.value.copy(progress = value) }
    var isRecording: Boolean
        get() = state.value.isRecording
        private set(value) { mutableState.value = state.value.copy(isRecording = value) }
    var elapsedMillis: Long
        get() = state.value.elapsedMillis
        private set(value) { mutableState.value = state.value.copy(elapsedMillis = value) }
    var playingId: String?
        get() = state.value.playingId
        private set(value) { mutableState.value = state.value.copy(playingId = value) }

    init { session = restore() }
    private var ticker: Job? = null
    private var startedAt = 0L
    val definition: SessionDefinition get() = definitions.definition(session.day)
    val limitMillis: Long get() = session.stage.recordingSlot()?.let {
        definition.prompts[it]?.limitMillis
    } ?: 45_000
    val nextDay: Int get() = if (session.stage == PracticeStage.Complete) (session.day + 1).coerceAtMost(14) else session.day

    private fun restore(): PracticeSession = try {
        var loaded = store.load()
        val pending = audio.pendingRecording()
        if (pending != null) {
            val referenced = loaded.recordings.values.any { it.id == pending.id } ||
                loaded.completedDays.values.any { review -> review.recordings.values.any { it.id == pending.id } }
            if (referenced) runCatching { audio.acknowledgeRecording(pending) }
            else if (pending.durationMillis < 1000) {
                runCatching { audio.delete(pending); audio.acknowledgeRecording(pending) }
            } else if (loaded.recordingInProgress && loaded.stage.recordingSlot() != null && audio.exists(pending)) {
                pendingRecording = pending
                val recovered = withRecording(loaded, requireNotNull(loaded.stage.recordingSlot()), pending)
                try {
                    store.save(recovered)
                    loaded = recovered
                    pendingRecording = null
                    runCatching { audio.acknowledgeRecording(pending) }
                    message = "Your finished recording was recovered. You can continue."
                } catch (error: Exception) {
                    mutableState.value = state.value.copy(hasPendingRecording = true)
                    message = "Your recording is kept locally, but its progress could not be saved. Retry saving."
                }
            }
        }
        if (loaded.recordingInProgress && pendingRecording == null) {
            val cleared = loaded.copy(recordingInProgress = false)
            try { store.save(cleared); loaded = cleared } catch (error: Exception) {
                telemetry.error("practice_save", error)
            }
            message = "Recording was interrupted before it could be saved. Your earlier attempts are safe. Please record this step again."
        }
        val repaired = repairMissing(loaded)
        if (repaired != loaded) {
            try { store.save(repaired) } catch (error: Exception) {
                saveBlocked = true
                message = "Recording references could not be repaired. Reopen the app to try again."
            }
        }
        repaired
    } catch (error: Exception) {
        telemetry.error("practice_restore", error)
        saveBlocked = true
        message = "Saved practice could not be opened. Your files have been kept. Try reopening the app."
        PracticeSession()
    }

    private fun repairMissing(saved: PracticeSession): PracticeSession {
        val missing = saved.recordings.filterValues { !audio.exists(it) }.keys
        val history = saved.completedDays.mapValues { (_, review) ->
            val kept = review.recordings.filterValues { audio.exists(it) }
            if (kept == review.recordings) review else review.copy(recordings = kept)
        }
        if (missing.isEmpty() && history == saved.completedDays) return saved
        message = "A saved recording is unavailable. Other recordings and completed lessons are kept."
        val recovery = if (saved.stage == PracticeStage.Complete) saved.stage else when {
            RecordingSlot.First in missing -> PracticeStage.FirstAttempt
            RecordingSlot.Retry in missing -> PracticeStage.Retry
            RecordingSlot.Application in missing -> PracticeStage.Apply
            RecordingSlot.CheckpointRepeat in missing -> PracticeStage.CheckpointRepeat
            RecordingSlot.CheckpointFresh in missing -> PracticeStage.CheckpointFresh
            else -> saved.stage
        }
        val kept = saved.recordings - missing
        return saved.copy(stage = recovery, recordings = kept, completedDays = history,
            firstAssessment = if (RecordingSlot.First in missing && saved.stage != PracticeStage.Complete) null else saved.firstAssessment,
            retryAssessment = if (RecordingSlot.CheckpointFresh in missing && saved.stage != PracticeStage.Complete) null else saved.retryAssessment,
            listenedRecordingIds = saved.listenedRecordingIds.intersect(kept.values.map { it.id }.toSet()))
    }

    private fun withRecording(progress: PracticeSession, slot: RecordingSlot, recording: RecordingRef): PracticeSession = progress.copy(
        recordings = progress.recordings + (slot to recording), recordingInProgress = false,
        listenedRecordingIds = progress.listenedRecordingIds - listOfNotNull(progress.recordings[slot]?.id),
        firstAssessment = if (slot == RecordingSlot.First) null else progress.firstAssessment,
        retryAssessment = if (slot in listOf(RecordingSlot.Retry, RecordingSlot.CheckpointFresh)) null else progress.retryAssessment,
        comparison = if (slot in listOf(RecordingSlot.First, RecordingSlot.Retry, RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh)) null else progress.comparison)

    private fun deleteIfUnreferenced(recording: RecordingRef) {
        val referenced = session.recordings.values.any { it.id == recording.id } ||
            session.completedDays.values.any { review -> review.recordings.values.any { it.id == recording.id } }
        if (!referenced) runCatching { audio.delete(recording) }
    }

    fun retryPendingSave() {
        val recording = pendingRecording ?: return
        val slot = session.stage.recordingSlot() ?: return
        val previous = session.recordings[slot]
        if (!audio.exists(recording)) { message = "The pending recording is unavailable. Reopen the app to recover."; return }
        if (commit(withRecording(session, slot, recording))) {
            pendingRecording = null
            mutableState.value = state.value.copy(hasPendingRecording = false)
            runCatching { audio.acknowledgeRecording(recording) }
            previous?.takeIf { it.id != recording.id }?.let(::deleteIfUnreferenced)
        }
    }

    fun beginNextDay(): Boolean {
        if (pendingRecording != null) return !saveBlocked
        if (session.stage != PracticeStage.Complete) return !saveBlocked
        if (session.day >= 14) return false
        stopPlayback()
        val day = session.day + 1
        val baseline = baseline()?.takeIf { audio.exists(it) }?.let { mapOf(RecordingSlot.Baseline to it) } ?: emptyMap()
        val entry = definitions.definition(day).stages.first()
        return commit(PracticeSession(
            day = day, stage = entry,
            recordings = baseline,
            completedDays = session.completedDays + (session.day to session.review()),
        ))
    }

    private fun commit(next: PracticeSession): Boolean {
        if (saveBlocked) return false
        if (next == session) return true
        return try {
            store.save(next)
            mutableState.value = state.value.copy(progress = next, message = null)
            telemetry.context("day" to next.day, "stage" to next.stage.name)
            true
        } catch (error: Exception) {
            telemetry.error("practice_save", error)
            message = "Could not save this step. Check available storage and try again."
            false
        }
    }

    fun advance(expectedStage: PracticeStage = session.stage) {
        if (isRecording || pendingRecording != null || session.stage != expectedStage) return
        val repaired = repairMissing(session)
        if (repaired != session) { commit(repaired); message = "A required recording is unavailable. Record that step again."; return }
        val s = session
        val next = advanceSession(s, definitions.definition(s.day))
        if (next == s) return
        stopPlayback()
        if (commit(next)) {
            telemetry.track("practice_step_completed", "day" to s.day, "stage" to s.stage.name)
            if (next.stage == PracticeStage.Complete) {
                telemetry.track("chapter_completed", "day" to s.day,
                    "completed_count" to session.completedDays.size, "comparison" to (s.comparison?.name ?: "none"),
                    "chapter_type" to if (definitions.definition(s.day).isCheckpoint) "checkpoint" else "practice")
                if (s.day == 14) telemetry.track("programme_completed", "completed_count" to session.completedDays.size)
            }
        }
    }

    fun permissionDenied() {
        telemetry.track("recording_blocked", "day" to session.day, "stage" to session.stage.name, "reason" to "permission")
        message = "Microphone permission is needed to record. Try again, or enable it in Android app settings."
    }

    fun startRecording() {
        if (isRecording || saveBlocked || pendingRecording != null || session.stage.recordingSlot() == null) return
        stopPlayback()
        message = null
        if (!commit(session.copy(recordingInProgress = true))) return
        val generation = ++recordingGeneration
        isRecording = true
        try {
            audio.start(limitMillis, onLimit = { if (generation == recordingGeneration) stopRecording() }, onError = {
                if (generation != recordingGeneration || !isRecording) return@start
                recordingGeneration++
                telemetry.error("recording_interrupted")
                telemetry.track("recording_failed", "day" to session.day, "stage" to session.stage.name, "reason" to "platform_callback")
                audio.cancel()
                isRecording = false
                ticker?.cancel()
                ticker = null
                commit(session.copy(recordingInProgress = false))
                message = "Recording was interrupted. Your earlier attempt is safe. Please try again."
            })
            if (generation != recordingGeneration || !isRecording) return
            startedAt = audio.monotonicMillis()
            elapsedMillis = 0
            isRecording = true
            ticker?.cancel()
            ticker = scope?.launch {
                while (isRecording) { delay(100); tick() }
            }
            telemetry.track("recording_started", "day" to session.day, "stage" to session.stage.name,
                "slot" to requireNotNull(session.stage.recordingSlot()).name,
                "is_retake" to (session.stage.recordingSlot() in session.recordings))
        } catch (error: Exception) {
            recordingGeneration++
            isRecording = false
            commit(session.copy(recordingInProgress = false))
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
        recordingGeneration++
        ticker?.cancel()
        ticker = null
        try {
            val recording = audio.stop()
            if (recording.durationMillis < 1_000) {
                telemetry.track("recording_rejected", "day" to session.day, "slot" to slot.name, "reason" to "too_short")
                audio.delete(recording)
                runCatching { audio.acknowledgeRecording(recording) }
                commit(session.copy(recordingInProgress = false))
                message = "That was too short to keep. Record at least one second and try again."
                return
            }
            val previous = session.recordings[slot]
            if (commit(withRecording(session, slot, recording))) {
                runCatching { audio.acknowledgeRecording(recording) }
                telemetry.track("recording_saved", "day" to session.day, "slot" to slot.name,
                    "duration_seconds" to recording.durationMillis / 1_000, "is_retake" to (previous != null), "interrupted" to interrupted)
                previous?.let(::deleteIfUnreferenced)
                if (interrupted) message = "Recording stopped when you left the app. Your attempt was saved."
            } else {
                pendingRecording = recording
                mutableState.value = state.value.copy(hasPendingRecording = true)
                message = "Your recording is kept locally, but its progress could not be saved. Retry saving."
            }
        } catch (error: Exception) {
            telemetry.error("recording_save", error)
            telemetry.track("recording_failed", "day" to session.day, "slot" to slot.name, "reason" to "save")
            audio.cancel()
            commit(session.copy(recordingInProgress = false))
            message = "Could not save a playable recording. Your earlier attempt is safe. Please try again."
        }
    }

    fun assess(value: SelfAssessment, retry: Boolean = false,
        expectedLessonId: String? = null, expectedRecordingId: String? = null,
    ) {
        if (expectedLessonId != null && expectedLessonId != session.lessonId) return
        val assessedSlot = if (retry && definition.isCheckpoint) RecordingSlot.CheckpointFresh else if (retry) RecordingSlot.Retry else RecordingSlot.First
        val recordingId = session.recordings[assessedSlot]?.id ?: return
        if (expectedRecordingId != null && expectedRecordingId != recordingId) return
        if (recordingId !in session.listenedRecordingIds) return
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
        ?: session.completedDays[1]?.recordings?.get(RecordingSlot.First)
        ?: session.recordings[RecordingSlot.First].takeIf { session.day == 1 }

    fun play(recording: RecordingRef, openingOnly: Boolean = false) {
        if (isRecording) return
        if (playingId == recording.id) { stopPlayback(); return }
        stopPlayback()
        playingId = recording.id
        message = null
        val generation = playbackGeneration
        try {
            val currentSlot = session.recordings.entries.firstOrNull { it.value.id == recording.id }?.key
            val historical = session.completedDays.entries.firstOrNull { (_, review) -> review.recordings.values.any { it.id == recording.id } }
            val slot = currentSlot ?: historical?.value?.recordings?.entries?.firstOrNull { it.value.id == recording.id }?.key
            val day = if (currentSlot != null) session.day else historical?.key ?: session.day
            player.play(recording, openingOnly, onFinished = {
                if (generation == playbackGeneration) {
                    playingId = null
                    if (recording.id !in session.listenedRecordingIds && recording.id in session.recordings.values.map { it.id })
                        commit(session.copy(listenedRecordingIds = session.listenedRecordingIds + recording.id))
                }
            }, onError = {
                if (generation != playbackGeneration) return@play
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

    fun stopPlayback() { playbackGeneration++; player.stopPlayback(); playingId = null }

    fun onBackground() {
        stopRecording(interrupted = true)
        stopPlayback()
    }

    fun clearData() {
        onBackground()
        try {
            // Also removes orphan attempts left by process death or a failed replacement.
            audio.deleteAllRecordings()
            audio.pendingRecording()?.let { audio.acknowledgeRecording(it) }
            pendingRecording = null
            mutableState.value = state.value.copy(hasPendingRecording = false)
            saveBlocked = false
            if (commit(PracticeSession())) telemetry.track("practice_data_deleted")
        } catch (error: Exception) {
            telemetry.error("practice_delete", error)
            message = "Some recordings could not be deleted. Please try again."
        }
    }
}
