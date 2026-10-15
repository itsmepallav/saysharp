package com.saysharp.shared.feedback

import com.saysharp.shared.telemetry.Telemetry

data class FeedbackOption(val id: String, val label: String)

enum class FeedbackKind(val id: String, val label: String, val question: String, val options: List<FeedbackOption>) {
    General("general", "Feedback", "What stands out?", listOf(
        FeedbackOption("useful_practice", "The practice feels useful"),
        FeedbackOption("lessons_too_long", "Lessons feel too long"),
        FeedbackOption("unclear_instructions", "Instructions could be clearer"),
        FeedbackOption("needs_variety", "I'd like more variety"),
        FeedbackOption("hard_to_return", "It's hard to stay consistent"),
    )),
    Feature("feature", "Feature request", "What would you like next?", listOf(
        FeedbackOption("more_topics", "More discussion topics"),
        FeedbackOption("interview_practice", "Interview practice"),
        FeedbackOption("debate_practice", "Debate practice"),
        FeedbackOption("more_vocabulary", "More vocabulary practice"),
        FeedbackOption("audio_examples", "Spoken audio examples"),
        FeedbackOption("flexible_lesson_order", "Choose my lesson order"),
    )),
    Problem("problem", "Report a problem", "Where did something go wrong?", listOf(
        FeedbackOption("recording", "Recording an answer"),
        FeedbackOption("playback", "Playing back audio"),
        FeedbackOption("reminders", "Practice reminders"),
        FeedbackOption("saved_progress", "Saved progress or recordings"),
        FeedbackOption("layout_navigation", "Layout or navigation"),
    )),
}

/** Feedback is a structured analytics response, not a support ticket or free-text inbox. */
class FeedbackSender(private val telemetry: Telemetry) {
    fun submit(kind: FeedbackKind, option: String, rating: Int, day: Int, completedCount: Int): Boolean {
        if (kind.options.none { it.id == option } || rating !in 0..5 || day !in 1..14 || completedCount !in 0..14) return false
        val parameters = mutableListOf<Pair<String, Any>>(
            "feedback_kind" to kind.id, "feedback_option" to option,
            "day" to day, "completed_count" to completedCount,
        )
        // Zero means no rating; omit it rather than polluting the average.
        if (kind == FeedbackKind.General && rating > 0) parameters += "rating" to rating
        return telemetry.enqueue("feedback_submitted", *parameters.toTypedArray())
    }
}
