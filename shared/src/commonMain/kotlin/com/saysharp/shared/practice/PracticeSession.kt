package com.saysharp.shared.practice

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingRef

enum class PracticeStage { Welcome, Baseline, Lesson, FirstAttempt, FirstCheck, Retry, Compare, Apply, Summary, Complete, Vocabulary, Recall, CheckpointRepeat, CheckpointFresh, CheckpointCompare }
enum class RecordingSlot { Baseline, First, Retry, Application, Vocabulary, Recall, CheckpointRepeat, CheckpointFresh }
enum class Comparison { Clearer, AboutTheSame, LessClear, Unsure }

data class PracticeSession(
    val stage: PracticeStage = PracticeStage.Welcome,
    val recordings: Map<RecordingSlot, RecordingRef> = emptyMap(),
    val firstAssessment: SelfAssessment? = null,
    val retryAssessment: SelfAssessment? = null,
    val comparison: Comparison? = null,
    val realLifeWin: String = "",
    val day: Int = 1,
    val vocabularyAssessment: SelfAssessment? = null,
    val recallAssessment: SelfAssessment? = null,
    val recallHintUsed: Boolean = false,
    val completedDays: Map<Int, DayReview> = emptyMap(),
    val recordingInProgress: Boolean = false,
    val listenedRecordingIds: Set<String> = emptySet(),
) {
    val lessonId: String get() = com.saysharp.shared.session.lessonId(day)
}

data class DayReview(
    val recordings: Map<RecordingSlot, RecordingRef> = emptyMap(),
    val firstAssessment: SelfAssessment? = null,
    val retryAssessment: SelfAssessment? = null,
    val comparison: Comparison? = null,
    val realLifeWin: String = "",
    val vocabularyAssessment: SelfAssessment? = null,
    val recallAssessment: SelfAssessment? = null,
    val recallHintUsed: Boolean = false,
)

fun PracticeSession.review() = DayReview(recordings - RecordingSlot.Baseline, firstAssessment, retryAssessment,
    comparison, realLifeWin, vocabularyAssessment, recallAssessment, recallHintUsed)

/** Compatibility boundary for existing Android JSON storage and test stores. */
interface SessionStore : com.saysharp.shared.session.ProgressRepository

fun PracticeStage.recordingSlot(): RecordingSlot? = when (this) {
    PracticeStage.Baseline -> RecordingSlot.Baseline
    PracticeStage.FirstAttempt -> RecordingSlot.First
    PracticeStage.Retry -> RecordingSlot.Retry
    PracticeStage.Apply -> RecordingSlot.Application
    PracticeStage.Vocabulary -> RecordingSlot.Vocabulary
    PracticeStage.Recall -> RecordingSlot.Recall
    PracticeStage.CheckpointRepeat -> RecordingSlot.CheckpointRepeat
    PracticeStage.CheckpointFresh -> RecordingSlot.CheckpointFresh
    else -> null
}
