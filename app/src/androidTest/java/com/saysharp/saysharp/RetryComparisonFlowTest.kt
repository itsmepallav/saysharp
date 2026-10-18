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
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RetryComparisonFlowTest {
    @get:Rule val compose = createComposeRule()
    private val first = RecordingRef("first", 35000)
    private val retry = RecordingRef("retry", 45000)
    private val audio = ComparisonAudio()
    private val store = object : ProgressRepository {
        var value = PracticeSession(stage = PracticeStage.Compare,
            recordings = mapOf(RecordingSlot.First to first, RecordingSlot.Retry to retry),
            firstAssessment = SelfAssessment.Unsure, listenedRecordingIds = setOf(first.id))
        override fun load() = value
        override fun save(session: SessionProgress) { value = session }
    }
    private fun show(small: Boolean = false) {
        val controller = PracticeController(audio, store)
        compose.setContent {
            if (small) CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.requiredSize(320.dp, 568.dp)) { SaySharpApp(controller, controller::startRecording) }
            } else SaySharpApp(controller, controller::startRecording)
        }
        click("Resume practice")
    }
    private fun click(label: String) {
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun play(label: String) {
        compose.onNodeWithContentDescription("Play recording · $label").performScrollTo().performClick()
    }

    @Test fun switchingRecordingsShowsCorrectStateAndDurationAndLeavingStopsPlayback() {
        show()
        compose.onNodeWithText("Compare your attempts").assertExists()
        play("First attempt")
        compose.onNodeWithContentDescription("Stop playback · First attempt").assertExists()
        compose.onNodeWithText("Playing · 35 seconds").assertExists()
        compose.runOnIdle { assertEquals(first.id, audio.active) }
        play("Retry")
        compose.onNodeWithContentDescription("Play recording · First attempt").assertExists()
        compose.onNodeWithContentDescription("Stop playback · Retry").assertExists()
        compose.onNodeWithText("Playing · 45 seconds").assertExists()
        compose.runOnIdle { assertEquals(retry.id, audio.active) }
        play("First attempt")
        click("Save & leave")
        compose.runOnIdle { assertNull(audio.active); assertEquals(SelfAssessment.Unsure, store.value.firstAssessment) }
        click("Resume practice")
        compose.onNodeWithContentDescription("Play recording · First attempt").assertExists()
        compose.onNodeWithContentDescription("Play recording · Retry").assertExists()
        compose.runOnIdle { assertEquals(mapOf(RecordingSlot.First to first, RecordingSlot.Retry to retry), store.value.recordings) }
    }

    @Test fun comparisonCanBeSkippedWithoutAnySecondAssessmentAndTransferIsFresh() {
        show()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Not yet").assertDoesNotExist()
        compose.onNodeWithText("Unsure").assertDoesNotExist()
        click("Try a fresh question")
        compose.onNodeWithText(DayOneSession.prompts.getValue(RecordingSlot.Application).text).assertExists()
        compose.onNodeWithText(DayOneSession.correctiveExample).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(PracticeStage.Apply, store.value.stage)
            assertNull(store.value.retryAssessment)
            assertNull(store.value.comparison)
            assertEquals(SelfAssessment.Unsure, store.value.firstAssessment)
            assertNull(audio.active)
        }
    }

    @Test fun comparisonControlsAndContinueRemainReachableWithLargeText() {
        show(small = true)
        compose.onNodeWithContentDescription("Play recording · First attempt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Play recording · Retry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try a fresh question").assertIsDisplayed().assertIsEnabled()
    }
}
private class ComparisonAudio : PracticeAudio {
    var active: String? = null
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) = error("Playback only")
    override fun stop(): RecordingRef = error("Playback only")
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) = error("Playback only")
    override fun deleteAllRecordings() = error("Playback only")
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {
        check(active == null)
        active = recording.id
    }
    override fun stopPlayback() { active = null }
}
