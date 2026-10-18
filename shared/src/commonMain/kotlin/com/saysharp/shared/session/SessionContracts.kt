package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingReference
import com.saysharp.shared.practice.PracticeStage
import com.saysharp.shared.practice.RecordingSlot
import com.saysharp.shared.practice.recordingSlot

/** Bundled teaching content, separate from a user's saved progress. */
data class SpeakingPrompt(val id: String, val text: String, val limitMillis: Long) {
    init { require(id.isNotBlank() && text.isNotBlank() && limitMillis > 0) }
}

data class SessionDefinition(
    val day: Int,
    val title: String,
    val instruction: String,
    val selfCheck: String,
    val correctiveExample: String,
    val prompts: Map<RecordingSlot, SpeakingPrompt>,
    val stages: List<PracticeStage>,
    val scenario: String = "",
    val weakExample: String = "",
    val correction: String = "",
    val listeningHint: String = "",
    val objective: String,
    val technique: String,
    val exampleExplanation: String = "",
    val realWorldApplication: String = "",
    val suggestedSeconds: IntRange = 30..60,
    val preparationSeconds: Int = 15,
    val openingOnly: Boolean = false,
    val checkpoint: CheckpointContent? = null,
) {
    val id: String get() = lessonId(day)
    val phase: com.saysharp.shared.programme.ProgrammePhase get() =
        com.saysharp.shared.programme.ProgrammePhase.entries.single { day in it.days }
    val isCheckpoint: Boolean get() = checkpoint != null
    init {
        require(day in 1..14 && title.isNotBlank())
        require(objective.isNotBlank() && instruction.isNotBlank() && technique.isNotBlank())
        require(selfCheck.isNotBlank() && listeningHint.isNotBlank())
        require(suggestedSeconds.first > 0 && suggestedSeconds.last <= 60 && preparationSeconds >= 0)
        if (checkpoint == null) require(scenario.isNotBlank() && weakExample.isNotBlank() &&
            correctiveExample.isNotBlank() && exampleExplanation.isNotBlank() && correction.isNotBlank())
        require(stages.isNotEmpty() && stages.last() == PracticeStage.Complete)
        require(stages.distinct().size == stages.size)
        require(stages.mapNotNull { it.recordingSlot() }.all { it in prompts })
    }
}

/** A kept attempt, not a transcript, score or complete retake history. */
data class Attempt(val slot: RecordingSlot, val recording: RecordingReference, val assessment: SelfAssessment? = null)

fun lessonId(day: Int): String {
    require(day in 1..14)
    return "saysharp-day-${day.toString().padStart(2, '0')}"
}

/** Compare an unchanged familiar question with independent performance on a new question. */
data class CheckpointContent(val comparisonLessonId: String, val reflection: String)

interface SessionRepository {
    /** Local bundled lookup; throws if the definition is unavailable. */
    fun definition(day: Int): SessionDefinition
    fun definition(id: String): SessionDefinition = (1..14).firstOrNull { lessonId(it) == id }
        ?.let(::definition) ?: throw IllegalArgumentException("Unknown lesson ID")
}

interface ProgressRepository {
    fun load(): SessionProgress
    /** Atomic: on failure, the previous saved progress must remain readable. */
    fun save(session: SessionProgress)
}

/** SessionProgress currently uses the established storage model during the incremental cutover. */
typealias SessionProgress = com.saysharp.shared.practice.PracticeSession

fun SessionProgress.attempt(slot: RecordingSlot): Attempt? = recordings[slot]?.let {
    Attempt(slot, it, when (slot) {
        RecordingSlot.First -> firstAssessment
        RecordingSlot.Retry, RecordingSlot.CheckpointFresh -> retryAssessment
        RecordingSlot.Vocabulary -> vocabularyAssessment
        RecordingSlot.Recall -> recallAssessment
        else -> null
    })
}
