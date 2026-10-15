package com.saysharp.saysharp

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.lesson.Programme
import com.saysharp.shared.reminder.*
import com.saysharp.shared.ui.SaySharpApp
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Store artwork source captures: real shared UI, isolated state, no microphone or user data. */
class StoreScreenshotCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio = StoreDemoAudio()

    private fun show(session: PracticeSession = PracticeSession()) {
        val controller = PracticeController(audio, object : SessionStore {
            private var value = session
            override fun load() = value
            override fun save(session: PracticeSession) { value = session }
        })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.5f, 1f)) {
                Box(Modifier.requiredSize(400.dp, 680.dp).consumeWindowInsets(WindowInsets.systemBars).testTag("store-screen")) {
                    SaySharpApp(controller, {}, reminders = ReminderController(object : ReminderPlatform {
                        override fun load() = ReminderSettings()
                        override fun save(settings: ReminderSettings) {}
                        override fun notificationsAllowed() = false
                        override fun schedule(settings: ReminderSettings) {}
                        override fun sendPreview() {}
                    }))
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onNodeWithTag("store-screen").captureToImage().asAndroidBitmap()
        val directory = File(context.cacheDir, "store-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun alignContent(label: String, inset: Float = 32f) {
        val node = compose.onNodeWithText(label, ignoreCase = true)
        node.performScrollTo()
        val scroll = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(label, ignoreCase = true)))
        val distance = node.fetchSemanticsNode().boundsInRoot.top - scroll.fetchSemanticsNode().boundsInRoot.top - inset
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        compose.waitForIdle()
    }

    @Test fun today() { show(); capture("01-today") }
    @Test fun programme() {
        show()
        compose.onNodeWithText("Programme").performClick()
        alignContent("01—07  ·  Build your foundation")
        capture("02-programme")
    }
    @Test fun lesson() {
        show(PracticeSession(stage = PracticeStage.Lesson))
        compose.onNodeWithText("Continue practice").performClick()
        alignContent(Programme.firstLesson.instruction)
        capture("03-lesson")
    }
    @Test fun compare() {
        show(PracticeSession(stage = PracticeStage.Compare, recordings = audio.examples()))
        compose.onNodeWithText("Continue practice").performClick()
        capture("04-compare")
    }
    @Test fun disagreement() {
        show(PracticeSession(day = 9, stage = PracticeStage.Lesson))
        compose.onNodeWithText("Continue practice").performClick()
        alignContent(Programme.lesson(9).instruction)
        capture("05-disagreement")
    }
    @Test fun progress() {
        show(PracticeSession(stage = PracticeStage.Compare, recordings = audio.examples()))
        compose.onNodeWithText("Progress").performClick()
        alignContent("Your starting point", 78f)
        capture("06-progress")
    }
}

private class StoreDemoAudio : AudioRecorder {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = File(instrumentation.targetContext.cacheDir, "store-demo-audio").apply { mkdirs() }
    fun examples(): Map<RecordingSlot, RecordingRef> = listOf(
        RecordingSlot.Baseline to "baseline", RecordingSlot.First to "first", RecordingSlot.Retry to "retry",
    ).associate { (slot, name) ->
        val file = File(directory, "$name.wav")
        instrumentation.context.assets.open("store-demo/$name.wav").use { input -> file.outputStream().use(input::copyTo) }
        val metadata = MediaMetadataRetriever()
        val duration = try {
            metadata.setDataSource(file.absolutePath)
            requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
        } finally { metadata.release() }
        slot to RecordingRef(file.absolutePath, duration)
    }
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) = error("Store captures never use the microphone")
    override fun stop(): RecordingRef = error("Store captures never record")
    override fun cancel() {}
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {}
    override fun stopPlayback() {}
    override fun exists(recording: RecordingRef) = File(recording.id).isFile
    override fun delete(recording: RecordingRef) {}
    override fun deleteAllRecordings() {}
    override fun monotonicMillis() = 0L
}
