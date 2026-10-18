package com.saysharp.saysharp

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SelfAssessmentFlowTest {
    @get:Rule val compose = createComposeRule()
    private val first = RecordingRef("first-attempt", 35000)
    private val audio = SelfCheckAudio()
    private fun ready(day: Int = 1, choice: SelfAssessment? = null) = PracticeSession(day = day,
        stage = PracticeStage.FirstCheck, firstAssessment = choice,
        recordings = mapOf(RecordingSlot.First to first), listenedRecordingIds = setOf(first.id),
        completedDays = (1 until day).associateWith { DayReview() })
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun unsureGuidanceStaysWhileReplayingAndDisappearsWhenSelectionChanges() {
        val store = SelfCheckStore(ready())
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Resume practice")
        compose.onNodeWithText("Listen for one thing").assertExists()
        click("Unsure")
        val hint = BundledSessionRepository.definition(1).listeningHint
        compose.onNodeWithText(hint).assertExists()
        compose.onNodeWithText("Unsure").assertIsSelected()
        compose.onNodeWithContentDescription("Replay First attempt with guidance").performScrollTo().performClick()
        compose.onNodeWithText(hint).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop First attempt replay with guidance").assertExists()
        compose.runOnIdle { audio.finished!!() }
        compose.onNodeWithText(hint).assertExists()
        click("Done")
        compose.onNodeWithText("Done").assertIsSelected()
        compose.onNodeWithText(hint).assertDoesNotExist()
        click("Unsure"); click("Not yet")
        compose.onNodeWithText("Not yet").assertIsSelected()
        compose.onNodeWithText(hint).assertDoesNotExist()
        compose.runOnIdle { assertEquals(SelfAssessment.NotYet, store.value.firstAssessment) }
    }

    @Test fun unsureAndHintRestoreAcrossSavedInstanceRecreationAndContinueHonestly() {
        val restoration = StateRestorationTester(compose)
        val store = SelfCheckStore(ready())
        val controller = PracticeController(audio, store)
        restoration.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Resume practice"); click("Unsure")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unsure").assertIsSelected()
        compose.onNodeWithText(controller.definition.listeningHint).assertExists()
        click("See one useful example")
        compose.onNodeWithText("Put the point first").assertExists()
        compose.runOnIdle { assertEquals(SelfAssessment.Unsure, store.value.firstAssessment) }
    }

    @Test fun failedSelectionWriteKeepsUnsureAndGuidanceUntilRetrySucceeds() {
        val store = SelfCheckStore(ready(choice = SelfAssessment.Unsure))
        val controller = PracticeController(audio, store)
        compose.setContent { SaySharpApp(controller, controller::startRecording) }
        click("Resume practice")
        compose.runOnIdle { store.failSave = true }
        click("Done")
        compose.onNodeWithText("Unsure").assertIsSelected()
        compose.onNodeWithText(controller.definition.listeningHint).assertExists()
        compose.runOnIdle { store.failSave = false }
        click("Done")
        compose.onNodeWithText("Done").assertIsSelected()
        compose.onNodeWithText(controller.definition.listeningHint).assertDoesNotExist()
    }

    @Test fun switchingLessonsShowsOnlyThatLessonsHintAndCheckpointFreshGuidance() {
        val day = mutableIntStateOf(1)
        compose.setContent {
            key(day.intValue) {
                val store = remember {
                    SelfCheckStore(if (day.intValue in listOf(7,14)) PracticeSession(day = day.intValue,
                        stage = PracticeStage.CheckpointCompare, retryAssessment = SelfAssessment.Unsure,
                        recordings = mapOf(RecordingSlot.CheckpointFresh to first, RecordingSlot.CheckpointRepeat to first.copy(id = "familiar")),
                        listenedRecordingIds = setOf(first.id, "familiar"), comparison = Comparison.Unsure,
                        completedDays = (1 until day.intValue).associateWith { DayReview() })
                    else ready(day.intValue, SelfAssessment.Unsure))
                }
                val controller = remember { PracticeController(audio, store) }
                SaySharpApp(controller, controller::startRecording)
            }
        }
        var previousHint: String? = null
        for (lesson in SessionCatalogue.definitions) {
            compose.runOnIdle { day.intValue = lesson.day }
            click("Resume practice")
            compose.onNodeWithText(lesson.listeningHint).assertExists()
            previousHint?.let { compose.onNodeWithText(it).assertDoesNotExist() }
            if (lesson.isCheckpoint) {
                compose.onNodeWithContentDescription("Play recording · Familiar question · Day ${lesson.day}").assertExists()
                compose.onNodeWithContentDescription("Play recording · Fresh question · Day ${lesson.day}").assertExists()
                compose.onNodeWithText("Compared with Day 1, your familiar answer felt:").assertExists()
            }
            previousHint = lesson.listeningHint
        }
    }
}
private class SelfCheckStore(var value: PracticeSession) : ProgressRepository {
    var failSave = false
    override fun load() = value
    override fun save(session: SessionProgress) { check(!failSave); value = session }
}
private class SelfCheckAudio : PracticeAudio {
    var finished: (() -> Unit)? = null
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("retry-attempt", 35000)
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { finished = onFinished }
    override fun stopPlayback() {}
}
