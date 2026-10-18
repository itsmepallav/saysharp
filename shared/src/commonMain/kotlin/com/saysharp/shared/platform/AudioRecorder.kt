package com.saysharp.shared.platform

/** Opaque app-private file identifier, never a platform URL exposed to shared UI. */
data class RecordingReference(val id: String, val durationMillis: Long)

/** Compatibility spelling for the existing JSON and platform adapters. */
typealias RecordingRef = RecordingReference

interface AudioPlayer {
    fun play(recording: RecordingReference, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit)
    fun stopPlayback()
}

/** Platform adapters own permission, private files, codecs, audio focus and resources.
 * Calls and callbacks are serialized on the UI thread. start requires permission.
 */
interface AudioRecorder {
    fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit)
    fun stop(): RecordingRef
    fun cancel()
    fun exists(recording: RecordingRef): Boolean
    fun delete(recording: RecordingRef)
    fun deleteAllRecordings()
    /** Durable finalized file awaiting metadata commit; never unfinished microphone output. */
    fun pendingRecording(): RecordingRef? = null
    fun acknowledgeRecording(recording: RecordingRef) {}
    fun monotonicMillis(): Long
}

/** Transitional combined adapter. New coordinators receive recorder/player separately. */
interface PracticeAudio : AudioRecorder, AudioPlayer
