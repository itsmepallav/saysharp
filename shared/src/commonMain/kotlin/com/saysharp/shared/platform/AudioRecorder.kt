package com.saysharp.shared.platform

/** Opaque app-private file identifier, never a platform URL exposed to shared UI. */
data class RecordingRef(val id: String, val durationMillis: Long)

/** Platform adapters own permission, private files, codecs, audio focus and resources.
 * Calls and callbacks are serialized on the UI thread. start requires permission.
 */
interface AudioRecorder {
    fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit)
    fun stop(): RecordingRef
    fun cancel()
    fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit)
    fun stopPlayback()
    fun exists(recording: RecordingRef): Boolean
    fun delete(recording: RecordingRef)
    fun deleteAllRecordings()
    fun monotonicMillis(): Long
}
