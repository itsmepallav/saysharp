package com.saysharp.shared.engagement

import kotlin.test.*

class EngagementControllerTest {
    @Test fun welcomeAndGoalPersistAcrossRestart() {
        val store = MemoryEngagement()
        val controller = EngagementController(store)
        assertFalse(controller.state.welcomed)
        assertTrue(controller.finishWelcome(SpeakingGoal.Meetings))
        assertEquals(controller.state, EngagementController(store).state)
        assertEquals(SpeakingGoal.Meetings, store.state.goal)
    }

    @Test fun historicalMilestonesCollapseIntoOneCelebrationAndDoNotReplay() {
        val store = MemoryEngagement(EngagementState(welcomed = true))
        val controller = EngagementController(store)
        val milestone = assertNotNull(controller.pendingMilestone((1..7).toSet()))
        assertEquals(7, milestone.sessions)
        assertTrue(controller.acknowledge(milestone))
        assertEquals(setOf(1, 3, 7), store.state.acknowledged)
        assertNull(EngagementController(store).pendingMilestone((1..7).toSet()))
        assertEquals(14, controller.pendingMilestone((1..14).toSet())?.sessions)
    }

    @Test fun incompleteAndNonConsecutiveHistoryCannotEarnLaterMilestone() {
        val controller = EngagementController(MemoryEngagement(EngagementState(welcomed = true)))
        assertNull(controller.pendingMilestone(setOf(7, 14)))
        assertEquals(1, controller.pendingMilestone(setOf(1, 3, 4, 5, 6, 7))?.sessions)
        assertNull(EngagementController(MemoryEngagement()).pendingMilestone((1..14).toSet()))
    }

    @Test fun failedWritesDoNotCompleteWelcomeOrAcknowledgeRewards() {
        val store = MemoryEngagement().apply { failSave = true }
        val controller = EngagementController(store)
        assertFalse(controller.finishWelcome(SpeakingGoal.Discussions))
        assertFalse(controller.state.welcomed)
        store.failSave = false
        controller.finishWelcome(SpeakingGoal.Everyday)
        store.failSave = true
        assertFalse(controller.acknowledge(Milestones.first()))
        assertNotNull(controller.pendingMilestone(setOf(1)))
    }

    @Test fun changingGoalKeepsWelcomeAndAcknowledgements() {
        val store = MemoryEngagement(EngagementState(true, SpeakingGoal.Everyday, setOf(1, 3)))
        val controller = EngagementController(store)
        assertTrue(controller.changeGoal(SpeakingGoal.Discussions))
        assertEquals(EngagementState(true, SpeakingGoal.Discussions, setOf(1, 3)), store.state)
    }

    @Test fun unreadablePreferencesDoNotBlockPracticeOrGetOverwritten() {
        val store = MemoryEngagement().apply { failLoad = true }
        val controller = EngagementController(store)
        assertTrue(controller.state.welcomed)
        assertFalse(controller.changeGoal(SpeakingGoal.Meetings))
        assertNull(controller.pendingMilestone((1..14).toSet()))
        assertFalse(store.state.welcomed)
    }
}

private class MemoryEngagement(var state: EngagementState = EngagementState()) : EngagementStore {
    var failLoad = false
    var failSave = false
    override fun load(): EngagementState { check(!failLoad); return state }
    override fun save(state: EngagementState) { check(!failSave); this.state = state }
}
