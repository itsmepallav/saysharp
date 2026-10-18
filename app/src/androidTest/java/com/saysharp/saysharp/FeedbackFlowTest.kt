package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.reminder.*
import com.saysharp.shared.telemetry.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedbackFlowTest {
    @get:Rule val compose = createComposeRule()
    private val sink = FeedbackTestBackend()

    private fun openFeedback() {
        val practice = PracticeController(FeedbackTestAudio(), object : SessionStore {
            override fun load() = PracticeSession()
            override fun save(session: PracticeSession) {}
        })
        val reminders = ReminderController(object : ReminderPlatform {
            override fun load() = ReminderSettings()
            override fun save(settings: ReminderSettings) {}
            override fun notificationsAllowed() = false
            override fun schedule(settings: ReminderSettings) {}
            override fun sendPreview() {}
        })
        compose.setContent { SaySharpApp(practice, {}, reminders = reminders, telemetry = Telemetry(sink)) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Feedback & ideas").performScrollTo().performClick()
    }

    @Test fun featureRequestRequiresChoiceAndConfirmsOneQueuedResponse() {
        openFeedback()
        compose.onNodeWithText("Send response").assertIsNotEnabled()
        compose.onNodeWithText("Feature request").performClick()
        compose.onNodeWithContentDescription("Spoken audio examples").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Send response").performClick()
        compose.onNodeWithText("Thanks for\nspeaking up.").assertExists()
        compose.onNodeWithText("Send response").assertDoesNotExist()
        compose.runOnIdle {
            val response = sink.events.filter { it.first == "feedback_submitted" }.single().second
            assertEquals("feature", response["feedback_kind"])
            assertEquals("audio_examples", response["feedback_option"])
        }
        compose.onNodeWithText("Back to settings").performClick()
        compose.onNodeWithText("Feedback & ideas").assertExists()
    }

    @Test fun changingCategoryClearsChoiceAndOptionalRating() {
        openFeedback()
        compose.onNodeWithContentDescription("The practice feels useful").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Rate 5 out of 5").performScrollTo().performClick()
        compose.onNodeWithText("Feature request").performScrollTo().performClick()
        compose.onNodeWithText("Send response").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Interview practice").performScrollTo().performClick()
        compose.onNodeWithText("Send response").performClick()
        compose.runOnIdle {
            val response = sink.events.first { it.first == "feedback_submitted" }.second
            assertEquals("interview_practice", response["feedback_option"])
            assertFalse(response.containsKey("rating"))
        }
    }

    @Test fun failedEnqueueKeepsSelectionAndAllowsRetry() {
        openFeedback()
        compose.onNodeWithText("Report a problem").performClick()
        compose.onNodeWithContentDescription("Recording an answer").performScrollTo().performClick()
        sink.fail = true
        compose.onNodeWithText("Send response").performClick()
        compose.onNodeWithText("Your response couldn't be queued. Please try again.").assertExists()
        compose.onNodeWithText("Thanks for\nspeaking up.").assertDoesNotExist()
        sink.fail = false
        compose.onNodeWithText("Send response").performClick()
        compose.onNodeWithText("Thanks for\nspeaking up.").assertExists()
        compose.runOnIdle { assertEquals(1, sink.events.count { it.first == "feedback_submitted" }) }
    }
}

private class FeedbackTestBackend : TelemetryBackend {
    var fail = false
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun event(name: String, parameters: Map<String, Any>) {
        if (name == "feedback_submitted") check(!fail)
        events += name to parameters
    }
    override fun context(parameters: Map<String, Any>) {}
    override fun error(operation: String, error: Throwable?) {}
}

private class FeedbackTestAudio : PracticeAudio {
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) = error("Not used")
    override fun stop(): RecordingRef = error("Not used")
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {}
    override fun stopPlayback() {}
    override fun exists(recording: RecordingRef) = false
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
}
