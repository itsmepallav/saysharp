package com.saysharp.shared.session

import com.saysharp.shared.practice.*

fun sessionStages(progress: SessionProgress, definition: SessionDefinition): List<PracticeStage> {
    require(progress.day == definition.day)
    return if (progress.stage == PracticeStage.Baseline && PracticeStage.Baseline !in definition.stages)
        listOf(PracticeStage.Baseline) + definition.stages else definition.stages
}

/** Completion rechecks the whole exercise, independent of the visible final screen. */
fun readyToComplete(progress: SessionProgress, definition: SessionDefinition): Boolean {
    if (progress.recordingInProgress) return false
    if ((1 until progress.day).any { it !in progress.completedDays }) return false
    return if (definition.isCheckpoint) {
        progress.retryAssessment != null && progress.comparison != null &&
            listOf(RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh).all {
                progress.recordings[it]?.id in progress.listenedRecordingIds
            }
    } else progress.firstAssessment != null &&
        progress.recordings[RecordingSlot.First]?.id in progress.listenedRecordingIds &&
        listOf(RecordingSlot.First, RecordingSlot.Retry, RecordingSlot.Application).all { it in progress.recordings }
}

/** Pure state transition. Recording, persistence and telemetry are coordinator responsibilities. */
fun advanceSession(progress: SessionProgress, definition: SessionDefinition): SessionProgress {
    if (progress.stage == PracticeStage.Complete) return progress
    val stages = sessionStages(progress, definition)
    val index = stages.indexOf(progress.stage)
    if (index < 0) return progress
    val slot = progress.stage.recordingSlot()
    if (slot != null && slot !in progress.recordings) return progress
    val allowed = when (progress.stage) {
        PracticeStage.FirstCheck -> progress.firstAssessment != null &&
            progress.recordings[RecordingSlot.First]?.id in progress.listenedRecordingIds
        PracticeStage.Compare -> true
        PracticeStage.CheckpointCompare -> progress.retryAssessment != null && progress.comparison != null &&
            listOf(RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh).all {
                progress.recordings[it]?.id in progress.listenedRecordingIds
            }
        PracticeStage.Vocabulary -> progress.vocabularyAssessment != null
        PracticeStage.Recall -> progress.recallAssessment != null
        else -> true
    }
    if (!allowed) return progress
    val next = stages.getOrNull(index + 1) ?: return progress
    // Only the current sequential lesson can complete; future metadata is never completion.
    if (next == PracticeStage.Complete && !readyToComplete(progress, definition)) return progress
    return progress.copy(stage = next, completedDays = if (next == PracticeStage.Complete)
        progress.completedDays + (progress.day to progress.review()) else progress.completedDays)
}

/** The renderer and tests share the rule: examples are never part of an unscaffolded attempt. */
fun SessionDefinition.showsModelExample(stage: PracticeStage): Boolean = !isCheckpoint && stage == PracticeStage.Lesson
