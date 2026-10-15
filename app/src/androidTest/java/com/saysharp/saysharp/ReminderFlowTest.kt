package com.saysharp.saysharp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.PracticeController
import com.saysharp.shared.practice.PracticeSession
import com.saysharp.shared.practice.SessionStore
import com.saysharp.shared.reminder.*
import com.saysharp.shared.ui.SaySharpApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderFlowTest {
    @get:Rule val compose = createComposeRule()
    private val platform = ScreenReminders()
    private val reminders = ReminderController(platform)

    private fun openSettings() {
        val practice = PracticeController(NoAudio(), object : SessionStore {
            override fun load() = PracticeSession()
            override fun save(session: PracticeSession) {}
        })
        compose.setContent {
            SaySharpApp(practice, {}, reminders, onRequestReminder = { reminders.setEnabled(true) })
        }
        compose.onNodeWithContentDescription("Settings").performClick()
    }

    @Test fun timeIsSavedAndOptOutCancelsDailyReminder() {
        openSettings()
        compose.onNodeWithContentDescription("Daily reminder").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Daily reminder").assertIsOn()
        compose.onNodeWithText("Reminder time · 19:00").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("08")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("35")
        compose.onNodeWithText("Save time").performClick()
        compose.onNodeWithText("Reminder time · 08:35").assertExists()
        compose.onNodeWithText("Send test notification").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, platform.previews); assertEquals(ReminderSettings(true, 8, 35), platform.scheduled) }
        compose.onNodeWithContentDescription("Daily reminder").performScrollTo().performClick()
        compose.runOnIdle { assertNull(platform.scheduled); assertFalse(platform.settings.enabled) }
    }

    @Test fun deniedPermissionStaysOffAndTimeDialogCanBeCancelled() {
        platform.allowed = false
        reminders.refresh()
        openSettings()
        compose.onNodeWithContentDescription("Daily reminder").performClick().assertIsOff()
        compose.onNodeWithText("Send test notification").assertIsNotEnabled()
        compose.onNodeWithText("Reminder time · 19:00").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("10")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reminder time · 19:00").assertExists()
        compose.runOnIdle { assertEquals(ReminderSettings(), platform.settings); assertNull(platform.scheduled) }
    }
}

private class ScreenReminders : ReminderPlatform {
    var settings = ReminderSettings()
    var allowed = true
    var scheduled: ReminderSettings? = null
    var previews = 0
    override fun load() = settings
    override fun save(settings: ReminderSettings) { this.settings = settings }
    override fun notificationsAllowed() = allowed
    override fun schedule(settings: ReminderSettings) { scheduled = settings.takeIf { it.enabled && allowed } }
    override fun sendPreview() { previews++ }
}

private class NoAudio : AudioRecorder {
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
