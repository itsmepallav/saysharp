package com.saysharp.saysharp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import com.saysharp.shared.platform.AudioRecorder
import com.saysharp.shared.platform.RecordingRef
import java.io.File
import java.util.UUID

/** Activity-scoped, foreground-only audio. All access and callbacks use the main thread. */
class AndroidAudioRecorder(private val context: Context) : AudioRecorder {
    private val directory = File(context.noBackupFilesDir, "recordings").apply { mkdirs() }
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var player: MediaPlayer? = null
    private var openingStop: Runnable? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun monotonicMillis() = SystemClock.elapsedRealtime()

    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {
        check(recorder == null)
        stopPlayback()
        check(StatFs(directory.path).availableBytes > 5L * 1024 * 1024) { "Insufficient storage" }
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        output = file
        @Suppress("DEPRECATION")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = next
        try {
            acquireFocus {
                if (recorder === next) onError()
            }
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44_100)
            next.setAudioEncodingBitRate(96_000)
            next.setAudioChannels(1)
            next.setOutputFile(file.absolutePath)
            next.setMaxDuration(limitMillis.toInt())
            next.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && recorder === next) onLimit()
            }
            next.setOnErrorListener { _, _, _ -> if (recorder === next) onError() }
            next.prepare()
            next.start()
        } catch (error: Exception) {
            cancel()
            throw error
        }
    }

    override fun stop(): RecordingRef {
        val current = checkNotNull(recorder)
        val file = checkNotNull(output)
        recorder = null
        output = null
        try {
            try { current.stop() } finally { current.release(); abandonFocus() }
            check(file.length() > 0)
            val metadata = MediaMetadataRetriever()
            val duration = try {
                metadata.setDataSource(file.absolutePath)
                checkNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull())
            } finally { metadata.release() }
            return RecordingRef(file.name, duration)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    override fun cancel() {
        val current = recorder
        recorder = null
        runCatching { current?.reset() }
        runCatching { current?.release() }
        output?.delete()
        output = null
        abandonFocus()
    }

    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {
        check(recorder == null)
        stopPlayback()
        val file = fileFor(recording)
        check(file.isFile)
        val next = MediaPlayer()
        player = next
        try {
            acquireFocus {
                if (player === next) { stopPlayback(); onFinished() }
            }
            next.setAudioAttributes(speech)
            next.setDataSource(file.absolutePath)
            next.setOnPreparedListener {
                if (player === next) {
                    next.start()
                    if (openingOnly) {
                        val stop = Runnable {
                            if (player === next) { stopPlayback(); onFinished() }
                        }
                        openingStop = stop
                        handler.postDelayed(stop, 10_000)
                    }
                }
            }
            next.setOnCompletionListener {
                if (player === next) { stopPlayback(); onFinished() }
            }
            next.setOnErrorListener { _, _, _ ->
                if (player === next) { stopPlayback(); onError() }
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
            stopPlayback()
            throw error
        }
    }

    override fun stopPlayback() {
        openingStop?.let(handler::removeCallbacks)
        openingStop = null
        val current = player
        player = null
        runCatching { current?.release() }
        if (recorder == null) abandonFocus()
    }

    override fun exists(recording: RecordingRef): Boolean = runCatching { fileFor(recording).isFile }.getOrDefault(false)
    override fun delete(recording: RecordingRef) {
        val file = fileFor(recording)
        check(!file.exists() || file.delete()) { "Could not delete recording" }
    }

    override fun deleteAllRecordings() {
        cancel()
        stopPlayback()
        val files = checkNotNull(directory.listFiles())
        files.filter { it.isFile && it.name.matches(Regex("[a-fA-F0-9-]{36}\\.m4a")) }
            .forEach { check(it.delete()) { "Could not delete recording" } }
    }

    private fun fileFor(recording: RecordingRef): File {
        require(recording.id.matches(Regex("[a-fA-F0-9-]{36}\\.m4a")))
        return File(directory, recording.id)
    }

    @Suppress("DEPRECATION")
    private fun acquireFocus(onLoss: () -> Unit) {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change < 0) handler.post { onLoss() }
        }
        focusListener = listener
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(speech)
                .setOnAudioFocusChangeListener(listener, handler)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        check(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio is in use" }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        else focusListener?.let { manager.abandonAudioFocus(it) }
        focusRequest = null
        focusListener = null
    }
}
