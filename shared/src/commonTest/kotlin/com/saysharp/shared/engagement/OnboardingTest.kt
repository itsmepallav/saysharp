package com.saysharp.shared.engagement

import kotlin.test.*

class OnboardingTest {
    @Test fun exactlyThreePagesUseApprovedValuePropositionCopy() {
        assertEquals(listOf(
            OnboardingPage("Know what you mean. Struggle to say it?", "Stop rambling, losing your train of thought, or finding the right words too late."),
            OnboardingPage("Think Sharp. Speak Bold.", "Learn to express your ideas clearly, disagree respectfully, and hold your ground."),
            OnboardingPage("Get better by speaking.", "Take on a real scenario. Record your response, listen back, make one improvement, and try again."),
        ), OnboardingPages)
        assertEquals("Start practicing", StartPracticing)
    }

    @Test fun unfinishedOnboardingRemainsIncompleteAcrossColdLaunchWithoutWritingPreferences() {
        val store = OnboardingStore()
        assertFalse(EngagementController(store).state.onboardingCompleted)
        // Page navigation is presentation state, not completion; recreating dependencies must not finish it.
        assertFalse(EngagementController(store).state.onboardingCompleted)
        assertEquals(0, store.writes)
    }

    @Test fun completionNeedsNoQuestionnaireAndPreservesOtherPreferencesAcrossRestart() {
        val store = OnboardingStore(EngagementState(goal = SpeakingGoal.Discussions, acknowledged = setOf(1)))
        val controller = EngagementController(store)
        assertTrue(controller.finishWelcome())
        val restored = EngagementController(store).state
        assertTrue(restored.onboardingCompleted)
        assertEquals(SpeakingGoal.Discussions, restored.goal)
        assertEquals(setOf(1), restored.acknowledged)
    }

    @Test fun failedCompletionStaysIncompleteUntilSuccessfulRetryAndRestart() {
        val store = OnboardingStore().apply { failSave = true }
        val controller = EngagementController(store)
        assertFalse(controller.finishWelcome())
        assertFalse(controller.state.onboardingCompleted)
        assertFalse(EngagementController(store).state.onboardingCompleted)
        assertNotNull(controller.message)
        store.failSave = false
        assertTrue(controller.finishWelcome())
        assertNull(controller.message)
        assertTrue(EngagementController(store).state.onboardingCompleted)
    }
}

private class OnboardingStore(var value: EngagementState = EngagementState()) : EngagementStore {
    var failSave = false
    var writes = 0
    override fun load() = value
    override fun save(state: EngagementState) { check(!failSave); value = state; writes++ }
}
