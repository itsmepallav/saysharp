package com.saysharp.saysharp

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.saysharp.saysharp.storage.AndroidSessionStore
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.platform.android.AndroidAudioRecorder
import com.saysharp.shared.practice.PracticeController
import com.saysharp.shared.practice.PracticeSession
import com.saysharp.shared.practice.PracticeStage
import com.saysharp.shared.practice.RecordingSlot
import com.saysharp.shared.practice.DayReview
import com.saysharp.shared.platform.PracticeAudio
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real Android media and filesystem, isolated from the user's private recordings/progress. */
class NativeAudioRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativeCaptureJournalRecreationPlaybackAndCorruptionReleaseResources() {
        assumeTrue("Grant RECORD_AUDIO to run the native microphone check",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        compose.setContent { }
        val dir = File(context.cacheDir, "native-audio-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = dir }
        val audio = AndroidAudioRecorder(isolated)
        val recreated = AndroidAudioRecorder(isolated)
        val store = AndroidSessionStore(File(dir, "progress"))
        store.save(PracticeSession(stage = PracticeStage.FirstAttempt, recordingInProgress = true))
        val failed = AtomicBoolean(false)
        var ref: RecordingRef? = null
        var startMs = 0.0
        var stopMs = 0.0
        try {
            compose.runOnIdle {
                val start = SystemClock.elapsedRealtimeNanos()
                audio.start(10_000, { failed.set(true) }, { failed.set(true) })
                startMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            }
            // Wait off the main thread; this deliberately records a short ambient sample locally.
            SystemClock.sleep(1600)
            compose.runOnIdle {
                val start = SystemClock.elapsedRealtimeNanos()
                ref = audio.stop()
                stopMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                assertFalse(failed.get())
                assertTrue(ref!!.durationMillis >= 1000)
                assertTrue(audio.exists(ref!!))
            }
            assertEquals(ref, recreated.pendingRecording())
            compose.runOnIdle {
                val restored = PracticeController(recreated, store)
                assertEquals(ref, restored.session.recordings[RecordingSlot.First])
                assertEquals(restored.session, AndroidSessionStore(File(dir, "progress")).load())
                assertFalse(restored.session.recordingInProgress)
                assertNull(recreated.pendingRecording())
            }
            val finished = CountDownLatch(1)
            val playbackStarted = SystemClock.elapsedRealtimeNanos()
            compose.runOnIdle { recreated.play(ref!!, false, { finished.countDown() }, { failed.set(true); finished.countDown() }) }
            assertTrue("Native playback must terminate", finished.await(10, TimeUnit.SECONDS))
            assertFalse("Valid AAC must play", failed.get())
            Log.i("R107_PERF", "capture_start_ms=$startMs stop_sync_journal_ms=$stopMs playback_to_completion_ms=${(SystemClock.elapsedRealtimeNanos()-playbackStarted)/1e6} audio_duration_ms=${ref!!.durationMillis}")
            compose.runOnIdle { recreated.acknowledgeRecording(ref!!) }
            assertNull(AndroidAudioRecorder(isolated).pendingRecording())
            val corrupt = RecordingRef("${UUID.randomUUID()}.m4a", 2000)
            File(dir, "recordings/${corrupt.id}").writeText("invalid AAC")
            val error = CountDownLatch(1)
            compose.runOnIdle {
                try { recreated.play(corrupt, false, { fail("Corrupt audio must not succeed") }, { error.countDown() }) }
                catch (_: Exception) { error.countDown() }
            }
            assertTrue("Corrupt media must fail without hanging", error.await(10, TimeUnit.SECONDS))
            // Verify error cleanup permits the next valid playback operation.
            val replay = CountDownLatch(1)
            compose.runOnIdle { recreated.play(ref!!, false, { replay.countDown() }, { failed.set(true); replay.countDown() }) }
            assertTrue(replay.await(10, TimeUnit.SECONDS))
            assertFalse(failed.get())
            compose.runOnIdle { recreated.stopPlayback(); recreated.cancel() }
        } finally {
            compose.runOnIdle { recreated.stopPlayback(); recreated.cancel(); audio.stopPlayback(); audio.cancel() }
            dir.deleteRecursively()
        }
    }

    @Test fun repeatedNativeCaptureCommandsKeepOneDurableAttemptPerRetake() {
        assumeTrue("Grant RECORD_AUDIO for real capture profiling",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        compose.setContent { }
        val dir = File(context.cacheDir, "capture-profile-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = dir }
        val native = AndroidAudioRecorder(isolated)
        var starts = 0
        var stops = 0
        val counted = object : PracticeAudio by native {
            override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {
                starts++; native.start(limitMillis, onLimit, onError)
            }
            override fun stop(): RecordingRef { stops++; return native.stop() }
        }
        val store = AndroidSessionStore(File(dir, "progress"))
        store.save(PracticeSession(stage = PracticeStage.FirstAttempt))
        val controller = PracticeController(counted, store)
        val startTimes = mutableListOf<Double>()
        val stopTimes = mutableListOf<Double>()
        val ids = mutableSetOf<String>()
        try {
            repeat(5) { index ->
                compose.runOnIdle {
                    val start = SystemClock.elapsedRealtimeNanos()
                    controller.startRecording(); controller.startRecording()
                    startTimes += (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                    assertTrue(controller.isRecording)
                    assertEquals(index + 1, starts)
                }
                // Capture duration, not a UI synchronization workaround. Never block the main thread here.
                SystemClock.sleep(1300)
                compose.runOnIdle {
                    val start = SystemClock.elapsedRealtimeNanos()
                    controller.stopRecording(); controller.stopRecording()
                    stopTimes += (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                    assertEquals(index + 1, stops)
                    assertFalse(controller.isRecording)
                    assertFalse(controller.state.value.hasPendingRecording)
                    assertNull(native.pendingRecording())
                    val ref = controller.session.recordings.getValue(RecordingSlot.First)
                    assertTrue(native.exists(ref))
                    assertTrue(ids.add(ref.id))
                    assertEquals(controller.session, AndroidSessionStore(File(dir, "progress")).load())
                    assertEquals(1, File(dir, "recordings").listFiles()!!.count { it.extension == "m4a" })
                }
            }
            for ((name, values) in listOf("controller_start" to startTimes, "controller_stop_commit" to stopTimes)) {
                val ordered = values.sorted()
                Log.i("R108_PERF", "$name n=5 p50_ms=${ordered[2]} max_ms=${ordered.last()}")
            }
        } finally {
            compose.runOnIdle { controller.onBackground(); native.cancel() }
            dir.deleteRecursively()
        }
    }

    @Test fun atomicMetadataWriteLatencyAndRestartRoundTrip() {
        compose.setContent { }
        val dir = File(context.cacheDir, "storage-profile-${UUID.randomUUID()}")
        val timings = mutableListOf<Double>()
        try {
            val store = AndroidSessionStore(dir)
            repeat(30) { i ->
                val snapshot = PracticeSession(stage = PracticeStage.Retry, realLifeWin = "test-$i",
                    recordings = mapOf(RecordingSlot.First to RecordingRef("test-$i.m4a", 35000)))
                compose.runOnIdle {
                    val start = SystemClock.elapsedRealtimeNanos()
                    store.save(snapshot)
                    timings += (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                }
                assertEquals(snapshot, AndroidSessionStore(dir).load())
            }
            val ordered = timings.sorted()
            Log.i("R107_PERF", "atomic_metadata_n=30 p50_ms=${ordered[14]} p95_ms=${ordered[28]} max_ms=${ordered.last()}")
            // Metadata-only representative completed programme: no real recordings or personal notes.
            val history = (1..14).associateWith { day ->
                val slots = if (day in listOf(7, 14)) listOf(RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh)
                    else listOf(RecordingSlot.First, RecordingSlot.Retry, RecordingSlot.Application)
                DayReview(recordings = slots.associateWith { RecordingRef("${UUID.randomUUID()}.m4a", 35000) },
                    realLifeWin = "x".repeat(500))
            }
            timings.clear()
            repeat(30) { i ->
                val snapshot = PracticeSession(day = 14, stage = PracticeStage.Complete,
                    completedDays = history, recordings = history.getValue(14).recordings, realLifeWin = "sample-$i")
                compose.runOnIdle {
                    val start = SystemClock.elapsedRealtimeNanos()
                    store.save(snapshot)
                    timings += (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                }
                assertEquals(snapshot, AndroidSessionStore(dir).load())
            }
            val full = timings.sorted()
            Log.i("R108_PERF", "full_programme_metadata n=30 bytes=${File(dir, "practice-session.json").length()} p50_ms=${full[14]} p95_ms=${full[28]} max_ms=${full.last()}")
        } finally { dir.deleteRecursively() }
    }
}
