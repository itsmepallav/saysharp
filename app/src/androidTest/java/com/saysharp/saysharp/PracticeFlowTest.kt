package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saysharp.shared.platform.PracticeAudio
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real shared screens, with a deterministic audio adapter. Never opens the device microphone. */
@RunWith(AndroidJUnit4::class)
class PracticeFlowTest {
    @get:Rule val compose = createComposeRule()
    private val store = TestStore()
    private val audio = TestAudio()

    private fun click(label: String) {
        val node = compose.onNodeWithText(label, ignoreCase = true)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun firstLessonCanBeCompletedWithUnsureAndNotYet() {
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Start today's practice")
        click("Try it myself")
        compose.onNodeWithText("More effective").assertDoesNotExist()
        click("Start recording"); click("Stop and save attempt")
        click("Listen and check")
        compose.onNodeWithText("More effective").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play recording · First attempt").performClick()
        click("Unsure")
        compose.onNodeWithText("What to listen for", ignoreCase = true).assertExists()
        click("See one useful example")
        compose.onNodeWithText("More effective").assertExists()
        click("Try again")
        click("Start recording"); click("Stop and save attempt")
        click("Compare my attempts")
        compose.onNodeWithContentDescription("Play recording · First attempt").assertExists()
        compose.onNodeWithContentDescription("Play recording · Retry").assertExists()
        click("Try a fresh question")
        click("Start recording"); click("Stop and save attempt")
        click("Finish my practice")
        click("Finish session")
        compose.onNodeWithText("1 of 14 sessions completed").assertExists()
        compose.runOnIdle {
            assertEquals(PracticeStage.Complete, store.value.stage)
            assertEquals(3, store.value.recordings.size)
        }
    }

    @Test fun permissionDenialLeavesRecordStepRecoverable() {
        store.value = PracticeSession(stage = PracticeStage.FirstAttempt)
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::permissionDenied) }
        click("Resume practice"); click("Start recording")
        compose.onNodeWithText("Microphone permission is needed to record. Try again, or enable it in Android app settings.").assertExists()
        compose.onNodeWithText("Keep this baseline").assertDoesNotExist()
        compose.runOnIdle { assertEquals(PracticeStage.FirstAttempt, store.value.stage) }
    }

    @Test fun startingDayTwoKeepsBaselineAndArchivesDayOne() {
        val baseline = audio.stop()
        store.value = PracticeSession(stage = PracticeStage.Complete, recordings = mapOf(RecordingSlot.Baseline to baseline), realLifeWin = "First-day win", completedDays = mapOf(1 to DayReview(realLifeWin = "First-day win")))
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Start today's practice")
        compose.onNodeWithText("Give one strong reason").assertExists()
        compose.runOnIdle {
            assertEquals(2, store.value.day)
            assertEquals(baseline, store.value.recordings[RecordingSlot.Baseline])
            assertEquals("First-day win", store.value.completedDays.getValue(1).realLifeWin)
        }
    }

    @Test fun checkpointUsesIndependentFamiliarAndFreshQuestionsBeforeReflection() {
        val baseline = audio.stop()
        store.value = PracticeSession(day = 7, stage = PracticeStage.CheckpointRepeat, recordings = mapOf(RecordingSlot.Baseline to baseline),
            completedDays = (1..6).associateWith { DayReview() })
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Resume practice")
        compose.onNodeWithText("Change one thing").assertDoesNotExist()
        click("Start recording"); click("Stop and save attempt")
        compose.onNodeWithContentDescription("Play recording · Saved attempt").assertDoesNotExist()
        click("Answer a fresh question")
        click("Start recording"); click("Stop and save attempt")
        click("Listen and compare")
        compose.onNodeWithContentDescription("Play recording · Familiar question · Day 7").performClick()
        compose.onNodeWithContentDescription("Play recording · Fresh question · Day 7").performScrollTo().performClick()
        click("About the same"); click("Not yet")
        click("Finish checkpoint"); click("Complete Day 7")
        compose.runOnIdle {
            assertEquals(PracticeStage.Complete, store.value.stage)
            assertEquals(baseline, store.value.recordings[RecordingSlot.Baseline])
        }
    }

    @Test fun recordingActionStaysReachableOnSmallScreenWithLargeText() {
        val baseline = audio.stop()
        store.value = PracticeSession(day = 13, stage = PracticeStage.FirstAttempt, recordings = mapOf(RecordingSlot.Baseline to baseline))
        val controller = PracticeController(audio, store)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                Box(Modifier.requiredSize(320.dp, 568.dp)) { SaySharpApp(controller, controller::startRecording) }
            }
        }
        click("Resume practice")
        compose.onNodeWithText("Start recording").assertIsDisplayed().performClick()
        compose.onNodeWithText("Stop and save attempt").assertIsDisplayed().performClick()
        compose.onNodeWithText("Listen and check").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(PracticeStage.FirstCheck, store.value.stage) }
    }
}

private class TestStore : SessionStore {
    var value = PracticeSession()
    override fun load() = value
    override fun save(session: PracticeSession) { value = session }
}

private class TestAudio : PracticeAudio {
    private var sequence = 0
    private val records = mutableSetOf<RecordingRef>()
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("test-${sequence++}", 2_000).also { records += it }
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { onFinished() }
    override fun stopPlayback() {}
    override fun exists(recording: RecordingRef) = recording in records
    override fun delete(recording: RecordingRef) { records -= recording }
    override fun deleteAllRecordings() { records.clear() }
    override fun monotonicMillis() = 0L
}
