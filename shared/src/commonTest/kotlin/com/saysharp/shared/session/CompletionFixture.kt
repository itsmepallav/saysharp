package com.saysharp.shared.session

import com.saysharp.shared.practice.*
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingRef

/** Model a genuinely finished exercise, rather than assuming the final stage proves it. */
fun PracticeSession.withRequiredExercise(): PracticeSession {
    val slots = if (day in listOf(7,14)) listOf(RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh)
        else listOf(RecordingSlot.First, RecordingSlot.Retry, RecordingSlot.Application)
    val refs = slots.associateWith { recordings[it] ?: RecordingRef("fixture-$day-${it.name}", 35000) }
    return copy(recordings = recordings + refs, listenedRecordingIds = listenedRecordingIds + refs.values.map { it.id },
        firstAssessment = firstAssessment ?: SelfAssessment.Done,
        retryAssessment = if (day in listOf(7,14)) SelfAssessment.Unsure else retryAssessment,
        comparison = if (day in listOf(7,14)) Comparison.Unsure else comparison)
}
