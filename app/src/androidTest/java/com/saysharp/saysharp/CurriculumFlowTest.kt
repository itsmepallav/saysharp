package com.saysharp.saysharp

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real shared rendering and transitions; audio is local fake data, never the device microphone. */
@OptIn(ExperimentalTestApi::class)
class CurriculumFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun awaitText(label: String) {
        // Wait for the requested destination, not a fixed delay or a retry of the whole test.
        compose.waitUntilExactlyOneExists(hasText(label), timeoutMillis = 5_000)
    }
    private fun click(label: String) {
        awaitText(label)
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun everyOrdinaryLessonHidesExamplesUntilReplaySelfCheckAndCorrection() {
        val selectedDay = mutableIntStateOf(1)
        lateinit var current: PracticeController
        compose.setContent {
            key(selectedDay.intValue) {
                val store = remember { object : ProgressRepository {
                    var value = PracticeSession(day = selectedDay.intValue,
                        completedDays = (1 until selectedDay.intValue).associateWith { DayReview() })
                    override fun load() = value
                    override fun save(session: SessionProgress) { value = session }
                } }
                val controller = remember { PracticeController(LessonAudio(selectedDay.intValue), store) }
                current = controller
                SaySharpApp(controller, controller::startRecording)
            }
        }
        for (d in SessionCatalogue.definitions.filterNot { it.isCheckpoint }) {
            compose.runOnIdle { selectedDay.intValue = d.day }
            compose.onNodeWithText(d.title).assertExists()
            click("Start today's practice")
            awaitText(d.instruction)
            compose.onNodeWithText(d.instruction).assertExists()
            compose.onNodeWithText(d.correctiveExample).assertDoesNotExist()
            click("Try it myself")
            compose.onNodeWithText(d.scenario).assertExists()
            compose.onNodeWithText(d.correctiveExample).assertDoesNotExist()
            click("Start recording"); click("Stop and save attempt"); click("Listen and check")
            compose.onNodeWithText(d.correctiveExample).assertDoesNotExist()
            compose.onNodeWithText("Unsure").assertDoesNotExist()
            compose.onNodeWithContentDescription("Play recording · First attempt").performClick()
            click("Unsure")
            compose.onNodeWithText(d.listeningHint).assertExists()
            compose.onNodeWithText(d.correctiveExample).assertDoesNotExist()
            click("See one useful example")
            compose.onNodeWithText(d.weakExample).assertExists()
            compose.onNodeWithText(d.correctiveExample).assertExists()
            compose.onNodeWithText(d.exampleExplanation).assertExists()
            compose.onNodeWithText(d.correction).assertExists()
            click("Try again")
            compose.onNodeWithText(d.scenario).assertExists()
            click("Start recording"); click("Stop and save attempt"); click("Compare my attempts")
            compose.onNodeWithContentDescription("Play recording · First attempt").assertExists()
            compose.onNodeWithContentDescription("Play recording · Retry").assertExists()
            click("Try a fresh question")
            compose.onNodeWithText(d.prompts.getValue(RecordingSlot.Application).text).assertExists()
            compose.onNodeWithText(d.correctiveExample).assertDoesNotExist()
            click("Start recording"); click("Stop and save attempt"); click("Finish my practice")
            compose.runOnIdle {
                assertEquals(PracticeStage.Complete, current.session.stage)
                val recordings = current.session.completedDays.getValue(d.day).recordings
                assertEquals(3, recordings.size)
                assertEquals(3, recordings.values.map { it.id }.toSet().size)
                assertTrue(recordings.values.all { it.id.startsWith("lesson-${d.day}-") })
            }
            click("Finish session")
            compose.onNodeWithText("${d.day} of 14 sessions completed").assertExists()
        }
    }

    @Test fun bothCheckpointsRequireTheirOwnReplaysThenCompleteIndependently() {
        val selectedDay = mutableIntStateOf(7)
        lateinit var current: PracticeController
        compose.setContent {
            key(selectedDay.intValue) {
                val store = remember { object : ProgressRepository {
                    var value = PracticeSession(day = selectedDay.intValue, stage = PracticeStage.CheckpointRepeat,
                        completedDays = (1 until selectedDay.intValue).associateWith { DayReview() })
                    override fun load() = value
                    override fun save(session: SessionProgress) { value = session }
                } }
                val controller = remember { PracticeController(LessonAudio(selectedDay.intValue), store) }
                current = controller
                SaySharpApp(controller, controller::startRecording)
            }
        }
        for (day in listOf(7, 14)) {
            compose.runOnIdle { selectedDay.intValue = day }
            click("Resume practice")
            click("Start recording"); click("Stop and save attempt"); click("Answer a fresh question")
            click("Start recording"); click("Stop and save attempt"); click("Listen and compare")
            awaitText("Finish checkpoint")
            compose.onNodeWithText("Finish checkpoint").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Play recording · Familiar question · Day $day").performScrollTo().performClick()
            click("About the same")
            awaitText("Finish checkpoint")
            compose.onNodeWithText("Finish checkpoint").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Play recording · Fresh question · Day $day").performScrollTo().performClick()
            // Checkpoints have a familiar-comparison Unsure and a fresh-self-check Unsure.
            compose.onAllNodesWithText("Unsure").onLast().performScrollTo().performClick()
            click("Finish checkpoint"); click("Complete Day $day")
            compose.runOnIdle {
                assertEquals(PracticeStage.Complete, current.session.stage)
                assertEquals(2, current.session.completedDays.getValue(day).recordings.size)
                assertEquals((1..day).toSet(), current.session.completedDays.keys)
            }
            click("Finish session")
        }
    }
}
private class LessonAudio(private val day: Int) : PracticeAudio {
    private var next = 0
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("lesson-$day-${next++}", 35000)
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { onFinished() }
    override fun stopPlayback() {}
}
