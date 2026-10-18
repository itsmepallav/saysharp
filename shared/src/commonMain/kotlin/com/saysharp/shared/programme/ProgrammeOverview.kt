package com.saysharp.shared.programme

import com.saysharp.shared.session.SessionCatalogue
import com.saysharp.shared.practice.PracticeSession
import com.saysharp.shared.practice.PracticeStage

enum class ProgrammePhase(val title: String, val days: IntRange, val goal: String) {
    SpeakClearly("Speak Clearly", 1..7, "Express thoughts clearly, concisely and precisely."),
    SpeakWithConviction("Speak With Conviction", 8..14, "Communicate opinions, disagreements and boundaries with clarity and confidence."),
}

data class LessonMetadata(val day: Int, val title: String, val explanation: String, val phase: ProgrammePhase) {
    val id: String get() = com.saysharp.shared.session.lessonId(day)
}

/** UI metadata is a projection of the same definitions used to run the lessons. */
object ProgrammeCatalogue {
    val lessons: List<LessonMetadata> = SessionCatalogue.definitions.map {
        LessonMetadata(it.day, it.title, it.objective, it.phase)
    }
    fun lesson(day: Int): LessonMetadata = lessons.single { it.day == day }
}

enum class LessonStatus { Completed, Current, InProgress, Upcoming }
enum class TodayAction { Start, Resume, Review }
data class ProgrammeLesson(val metadata: LessonMetadata, val status: LessonStatus)
data class ProgrammeOverview(
    val lessons: List<ProgrammeLesson>,
    val currentLesson: LessonMetadata?,
    val completedCount: Int,
    val action: TodayAction,
) {
    val isComplete: Boolean get() = currentLesson == null
}

/** Pure projection of the repository-backed session snapshot. Neither screen owns progress. */
fun programmeOverview(progress: PracticeSession): ProgrammeOverview {
    val completed = progress.completedDays.keys.filter { it in 1..14 }.toSet()
    val allComplete = (1..14).all { it in completed }
    val day = if (allComplete) null else if (progress.stage == PracticeStage.Complete)
        (progress.day + 1).coerceAtMost(14) else progress.day
    val resuming = day == progress.day && progress.stage != PracticeStage.Complete &&
        (progress.stage != PracticeStage.Welcome || progress.recordings.isNotEmpty() || progress.recordingInProgress)
    return ProgrammeOverview(
        lessons = ProgrammeCatalogue.lessons.map { lesson ->
            ProgrammeLesson(lesson, when {
                lesson.day in completed -> LessonStatus.Completed
                lesson.day == day -> if (resuming) LessonStatus.InProgress else LessonStatus.Current
                else -> LessonStatus.Upcoming
            })
        },
        currentLesson = day?.let(ProgrammeCatalogue::lesson),
        completedCount = completed.size,
        action = when { allComplete -> TodayAction.Review; resuming -> TodayAction.Resume; else -> TodayAction.Start },
    )
}
