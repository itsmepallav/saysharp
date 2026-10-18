package com.saysharp.shared.engagement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saysharp.shared.telemetry.Telemetry

enum class SpeakingGoal(val label: String, val outcome: String) {
    Meetings("Speak up at work", "Make your ideas\nheard at work."),
    Everyday("Explain myself clearly", "Make your\npoint clearly."),
    Discussions("Hold my own in discussions", "Say what you mean.\nStand by it."),
}

data class EngagementState(
    val onboardingCompleted: Boolean = false,
    val goal: SpeakingGoal = SpeakingGoal.Everyday,
    val acknowledged: Set<Int> = emptySet(),
)

interface EngagementStore {
    fun load(): EngagementState
    fun save(state: EngagementState)
}

data class Milestone(val sessions: Int, val title: String, val detail: String, val next: String)

val Milestones = listOf(
    Milestone(1, "You have a starting point.", "Your baseline and first session are saved. You now have something real to compare against.", "Take one idea into a real conversation. Lead with your point."),
    Milestone(3, "Three sessions. More to build on.", "Your next goal: an answer people can follow from the start.", "Lead with your point. Give one reason. Add one example."),
    Milestone(7, "Your first checkpoint is complete.", "You answered your starting question again and tried an unfamiliar one.", "Listen to both. What carries over when the question changes?"),
    Milestone(14, "You completed the programme.", "Your goal was sharper, clearer, more articulate speaking. Now hear what changed.", "Compare your first and last answers. Keep one improvement; choose your next target."),
)

/** Rewards completed work; never infers ability from a session count. */
class EngagementController(private val store: EngagementStore, private val telemetry: Telemetry = Telemetry.None) {
    var message: String? by mutableStateOf(null)
        private set
    private var readable = true
    var state by mutableStateOf(try { store.load() } catch (error: Exception) {
        telemetry.error("welcome_restore", error)
        readable = false
        message = "Your welcome preferences could not be opened. Your practice is still available."
        EngagementState(onboardingCompleted = true)
    })
        private set

    fun finishWelcome(goal: SpeakingGoal = state.goal, skipped: Boolean = false): Boolean {
        val firstTime = !state.onboardingCompleted
        val saved = update(state.copy(onboardingCompleted = true, goal = goal))
        if (saved) telemetry.track(if (firstTime) "tutorial_complete" else "welcome_replayed",
            "goal" to goal.name, "skipped" to skipped)
        return saved
    }

    fun changeGoal(goal: SpeakingGoal): Boolean {
        val changed = goal != state.goal
        val saved = update(state.copy(goal = goal))
        if (saved && changed) telemetry.track("speaking_goal_changed", "goal" to goal.name)
        return saved
    }

    fun pendingMilestone(completedDays: Set<Int>): Milestone? {
        if (!readable || !state.onboardingCompleted) return null
        return Milestones.lastOrNull { milestone ->
            milestone.sessions !in state.acknowledged && (1..milestone.sessions).all { it in completedDays }
        }
    }

    fun acknowledge(milestone: Milestone): Boolean {
        val changed = milestone.sessions !in state.acknowledged
        val saved = update(state.copy(
            acknowledged = state.acknowledged + Milestones.filter { it.sessions <= milestone.sessions }.map { it.sessions },
        ))
        if (saved && changed) telemetry.track("milestone_acknowledged", "day" to milestone.sessions)
        return saved
    }

    private fun update(next: EngagementState): Boolean {
        if (!readable) return false
        return try {
            store.save(next)
            state = next
            message = null
            true
        } catch (error: Exception) {
            telemetry.error("welcome_save", error)
            message = "That change could not be saved. Please try again."
            false
        }
    }
}
