package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.engagement.*
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.*
import com.saysharp.shared.ui.SaySharpApp
import com.saysharp.shared.telemetry.Telemetry
import com.saysharp.shared.telemetry.TelemetryBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EngagementFlowTest {
    @get:Rule val compose = createComposeRule()
    private val store = WelcomeTestStore()

    private fun show(session: PracticeSession = PracticeSession(), telemetry: Telemetry = Telemetry.None): EngagementController {
        val engagement = EngagementController(store, telemetry)
        val practice = PracticeController(WelcomeTestAudio(), object : SessionStore {
            override fun load() = session
            override fun save(session: PracticeSession) {}
        })
        compose.setContent { SaySharpApp(practice, {}, engagement = engagement, telemetry = telemetry) }
        return engagement
    }

    @Test fun threePagesExplainMethodAndKeepChosenGoal() {
        show()
        compose.onNodeWithContentDescription("Welcome page 1 of 3").assertExists()
        compose.onNodeWithContentDescription("Welcome slides").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Welcome page 2 of 3").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("Welcome page 3 of 3").assertExists()
        compose.onNodeWithText("Speak up at work").performScrollTo().performClick()
        compose.onNodeWithText("Start practice").performClick()
        compose.onNodeWithText("One clearer\nanswer today.").assertExists()
        compose.runOnIdle { assertTrue(store.state.welcomed); assertEquals(SpeakingGoal.Meetings, store.state.goal) }
    }

    @Test fun introCanBeSkippedWithoutBlockingPractice() {
        show()
        compose.onNodeWithText("Skip intro").performClick()
        compose.onNodeWithText("Start practice").assertExists()
        compose.runOnIdle { assertTrue(store.state.welcomed) }
    }

    @Test fun welcomeAnalyticsTracksSettledPagesWithoutCountingPrefetchedNeighbours() {
        val pages = mutableListOf<Int>()
        val telemetry = Telemetry(object : TelemetryBackend {
            override fun event(name: String, parameters: Map<String, Any>) {
                if (name == "welcome_slide_viewed") pages += parameters.getValue("page") as Int
            }
            override fun context(parameters: Map<String, Any>) {}
            override fun error(operation: String, error: Throwable?) {}
        })
        show(telemetry = telemetry)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1), pages) }
        compose.onNodeWithContentDescription("Welcome slides").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2), pages) }
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2, 3), pages) }
    }

    @Test fun earnedMilestoneDismissalPersistsAndCollectionIsReachable() {
        store.state = EngagementState(welcomed = true)
        val engagement = show(PracticeSession(stage = PracticeStage.Complete, completedDays = mapOf(1 to DayReview())))
        compose.onNodeWithText("You have a starting point.").assertExists()
        compose.onNodeWithText("Keep going").performClick()
        compose.runOnIdle { assertNull(engagement.pendingMilestone(setOf(1))); assertEquals(setOf(1), store.state.acknowledged) }
        compose.onNodeWithText("Progress").performClick()
        compose.onNodeWithText("Your milestones · 1 / 4").performScrollTo().performClick()
        compose.onNodeWithText("Every step counts.").assertExists()
        compose.onNodeWithText("Keep going").performClick()
        compose.onNodeWithText("Every step counts.").assertDoesNotExist()
    }
}

private class WelcomeTestStore : EngagementStore {
    var state = EngagementState()
    override fun load() = state
    override fun save(state: EngagementState) { this.state = state }
}

private class WelcomeTestAudio : AudioRecorder {
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) = error("Not used")
    override fun stop(): RecordingRef = error("Not used")
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {}
    override fun stopPlayback() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
}
