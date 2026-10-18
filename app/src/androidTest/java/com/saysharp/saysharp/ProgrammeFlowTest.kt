package com.saysharp.saysharp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProgrammeFlowTest {
    @get:Rule val compose = createComposeRule()
    private val storage = ProgrammeTestStore()
    private val generation = mutableIntStateOf(0)
    private fun show(small: Boolean = false) {
        compose.setContent {
            key(generation.intValue) {
                val controller = remember { PracticeController(ProgrammeAudio(), storage) }
                if (small) CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.requiredSize(320.dp, 568.dp)) { SaySharpApp(controller, controller::startRecording) }
                } else SaySharpApp(controller, controller::startRecording)
            }
        }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun history(count: Int) = (1..count).associateWith { DayReview() }

    @Test fun freshTodayAndProgrammeAgreeAndUpcomingLessonsHaveNoStartAction() {
        show()
        compose.onNodeWithText("Day 1 of 14").assertExists()
        compose.onNodeWithText("Lead with your point").assertExists()
        compose.onNodeWithText("0 of 14 lessons completed").assertExists()
        click("Programme")
        compose.onNodeWithText("Day 1 · Current lesson").assertExists()
        compose.onNodeWithText("Day 2 · Upcoming").assertExists()
        compose.onNodeWithText("Start Day 2").assertDoesNotExist()
        compose.onNodeWithText("Speak With Conviction").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Day 14 · Upcoming").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Day 14").assertDoesNotExist()
        compose.runOnIdle { assertTrue(storage.value.completedDays.isEmpty()) }
    }

    @Test fun dayOneCompletionRefreshesBothScreensAndNextLessonStartsImmediately() {
        show(); click("Start today's practice"); click("Try it myself")
        click("Start recording"); click("Stop and save attempt"); click("Listen and check")
        compose.onNodeWithContentDescription("Play recording · First attempt").performClick()
        click("Not yet"); click("See one useful example"); click("Try again")
        click("Start recording"); click("Stop and save attempt"); click("Compare my attempts")
        click("Try a fresh question"); click("Start recording"); click("Stop and save attempt")
        click("Finish my practice"); click("Finish session"); click("Today")
        compose.onNodeWithText("Day 2 of 14").assertExists()
        compose.onNodeWithText("1 of 14 lessons completed").assertExists()
        click("Programme")
        compose.onNodeWithText("Day 1 · Completed").assertExists()
        compose.onNodeWithText("Day 2 · Current lesson").assertExists()
        click("Start Day 2")
        compose.onNodeWithText("Give one strong reason").assertExists()
        compose.runOnIdle { assertEquals(2, storage.value.day); assertEquals(setOf(1), storage.value.completedDays.keys) }
    }

    @Test fun partialSessionResumesAfterRelaunchWithoutDuplicatingProgress() {
        storage.value = PracticeSession(stage = PracticeStage.Retry,
            recordings = mapOf(RecordingSlot.First to RecordingRef("kept-first", 35_000)))
        show()
        click("Programme")
        compose.onNodeWithText("Day 1 · In progress").assertExists()
        compose.runOnIdle { generation.intValue++ }
        compose.onNodeWithText("Resume practice").assertExists()
        click("Resume practice")
        compose.onNodeWithText("Retry · Attempt 2").assertExists()
        compose.runOnIdle {
            assertEquals("kept-first", storage.value.recordings.getValue(RecordingSlot.First).id)
            assertTrue(storage.value.completedDays.isEmpty())
        }
    }

    @Test fun completedFirstPhaseSelectsDayEightAndShowsBothPhases() {
        storage.value = PracticeSession(day = 7, stage = PracticeStage.Complete, completedDays = history(7))
        show()
        compose.onNodeWithText("Day 8 of 14").assertExists()
        compose.onNodeWithText("SPEAK WITH CONVICTION").assertExists()
        compose.onNodeWithText("7 of 14 lessons completed").assertExists()
        click("Programme")
        compose.onNodeWithText("Day 7 · Completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Day 8 · Current lesson").performScrollTo().assertIsDisplayed()
    }

    @Test fun finalCompletionOffersReviewAndNoFurtherPractice() {
        storage.value = PracticeSession(day = 14, stage = PracticeStage.Complete, completedDays = history(14))
        show()
        compose.onNodeWithText("Programme complete").assertExists()
        compose.onNodeWithText("14 of 14 lessons completed").assertExists()
        compose.onNodeWithText("Start today's practice").assertDoesNotExist()
        compose.onNodeWithText("Resume practice").assertDoesNotExist()
        click("Programme")
        compose.onNodeWithText("Day 14 · Completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Day 14").assertDoesNotExist()
        click("Today"); click("Review my practice")
        compose.onNodeWithText("DAY 14 · COMPLETED").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(14, storage.value.day) }
    }

    @Test fun smallScreenAndDoubleFontKeepPracticeAndProgrammeReachable() {
        show(small = true)
        compose.onNodeWithText("Lead with your point").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start today's practice").performScrollTo().assertIsDisplayed()
        click("Programme")
        compose.onNodeWithText("Day 1 · Current lesson").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Day 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Day 14 · Upcoming").performScrollTo().assertIsDisplayed()
    }
}
private class ProgrammeTestStore : SessionStore {
    var value = PracticeSession()
    override fun load() = value
    override fun save(session: PracticeSession) { value = session }
}
private class ProgrammeAudio : PracticeAudio {
    private var sequence = 0
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("test-${sequence++}", 35_000)
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { onFinished() }
    override fun stopPlayback() {}
}
