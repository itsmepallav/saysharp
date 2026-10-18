package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.engagement.*
import com.saysharp.shared.platform.PracticeAudio
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

    private val generation = mutableIntStateOf(0)
    private var microphoneRequests = 0
    private fun show(session: PracticeSession = PracticeSession(), telemetry: Telemetry = Telemetry.None): EngagementController {
        val firstEngagement = EngagementController(store, telemetry)
        val sessionStore = object : SessionStore {
            var value = session
            override fun load() = value
            override fun save(session: PracticeSession) { value = session }
        }
        compose.setContent {
            key(generation.intValue) {
                val engagement = remember { if (generation.intValue == 0) firstEngagement else EngagementController(store, telemetry) }
                val practice = remember { PracticeController(WelcomeTestAudio(), sessionStore) }
                SaySharpApp(practice, { microphoneRequests++ }, engagement = engagement, telemetry = telemetry)
            }
        }
        return firstEngagement
    }

    private fun next() { compose.onNodeWithText("Next").performClick(); compose.waitForIdle() }
    private fun coldLaunch() { compose.runOnIdle { generation.intValue++ }; compose.waitForIdle() }

    @Test fun threePagesUseApprovedCopyAndOpenTodayWithOneActionIntoDayOne() {
        show()
        for (index in OnboardingPages.indices) {
            val page = OnboardingPages[index]
            compose.onNodeWithContentDescription("Welcome page ${index + 1} of 3").assertExists()
            compose.onNodeWithText(page.headline).assertExists().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithText(page.supportingText).assertExists()
            compose.onNodeWithText("Skip intro").assertDoesNotExist()
            compose.onNodeWithText("Where would clarity help you most?").assertDoesNotExist()
            compose.runOnIdle { assertFalse(store.state.onboardingCompleted); assertEquals(0, microphoneRequests) }
            if (index < 2) next()
        }
        compose.onNodeWithText("Start practicing").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lead with your point").assertExists()
        compose.onNodeWithText("Start today's practice").assertExists().performClick()
        compose.onNodeWithText("Try it myself").assertExists()
        compose.onNodeWithText("One clearer\nanswer today.").assertDoesNotExist()
        compose.runOnIdle { assertTrue(store.state.onboardingCompleted); assertEquals(0, microphoneRequests) }
    }

    @Test fun backAndSwipeNavigationDoNotCompleteOnboarding() {
        show(); next()
        compose.onNodeWithText("Back").performClick(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Welcome page 1 of 3").assertExists()
        compose.onNodeWithContentDescription("Welcome slides").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Welcome page 2 of 3").assertExists()
        next()
        compose.onNodeWithText("Back").performClick(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Welcome page 2 of 3").assertExists()
        compose.runOnIdle { assertFalse(store.state.onboardingCompleted); assertEquals(0, microphoneRequests) }
    }

    @Test fun closingHalfwayRestartsAtFirstPageAndCompletedColdLaunchSkipsIntro() {
        show(); next(); coldLaunch()
        compose.onNodeWithContentDescription("Welcome page 1 of 3").assertExists()
        next(); next()
        compose.onNodeWithText("Start practicing").performClick()
        coldLaunch()
        compose.onNodeWithContentDescription("Welcome slides").assertDoesNotExist()
        compose.onNodeWithText("Start today's practice").assertExists()
        compose.runOnIdle { assertTrue(store.state.onboardingCompleted) }
    }

    @Test fun failedCompletionStaysOnMethodPageAndRetryOpensToday() {
        show(); next(); next()
        compose.runOnIdle { store.failSave = true }
        compose.onNodeWithText("Start practicing").performClick()
        compose.onNodeWithText("That change could not be saved. Please try again.").assertExists()
        compose.onNodeWithContentDescription("Welcome page 3 of 3").assertExists()
        compose.runOnIdle { assertFalse(store.state.onboardingCompleted); store.failSave = false }
        compose.onNodeWithText("Start practicing").assertIsEnabled().performClick()
        compose.onNodeWithText("Lead with your point").assertExists()
    }

    @Test fun completedIntroDoesNotResetPartialDayOneSession() {
        store.state = EngagementState(onboardingCompleted = true)
        show(PracticeSession(stage = PracticeStage.Retry,
            recordings = mapOf(RecordingSlot.First to RecordingRef("first-kept", 35_000)),
            firstAssessment = com.saysharp.shared.lesson.SelfAssessment.Done,
            listenedRecordingIds = setOf("first-kept")))
        compose.onNodeWithContentDescription("Welcome slides").assertDoesNotExist()
        compose.onNodeWithText("Resume practice").performClick()
        compose.onNodeWithText("Retry · Attempt 2").assertExists()
    }

    @Test fun smallScreenAtDoubleFontScaleKeepsAllCopyScrollableAndCtaReachable() {
        val engagement = EngagementController(store)
        val practice = PracticeController(WelcomeTestAudio(), object : SessionStore {
            override fun load() = PracticeSession()
            override fun save(session: PracticeSession) {}
        })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.requiredSize(320.dp, 568.dp)) { SaySharpApp(practice, {}, engagement = engagement) }
            }
        }
        OnboardingPages.forEachIndexed { index, page ->
            compose.onNodeWithText(page.headline).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(page.supportingText).performScrollTo().assertIsDisplayed()
            if (index < 2) { compose.onNodeWithText("Next").assertIsDisplayed().performClick(); compose.waitForIdle() }
        }
        compose.onNodeWithText("Start practicing").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun activityRecreationRetainsCurrentPageAndCompletedPracticeDestination() {
        val restoration = StateRestorationTester(compose)
        val engagement = EngagementController(store)
        val practice = PracticeController(WelcomeTestAudio(), object : SessionStore {
            override fun load() = PracticeSession()
            override fun save(session: PracticeSession) {}
        })
        restoration.setContent { SaySharpApp(practice, {}, engagement = engagement) }
        next(); restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Welcome page 2 of 3").assertExists()
        next(); compose.onNodeWithText("Start practicing").performClick()
        compose.onNodeWithText("Start today's practice").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Lead with your point").assertExists()
        compose.onNodeWithText("Try it myself").assertExists()
        compose.onNodeWithContentDescription("Welcome slides").assertDoesNotExist()
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
        store.state = EngagementState(onboardingCompleted = true)
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
    var failSave = false
    override fun load() = state
    override fun save(state: EngagementState) { check(!failSave); this.state = state }
}

private class WelcomeTestAudio : PracticeAudio {
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
