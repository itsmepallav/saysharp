package com.saysharp.shared.platform.android

import com.saysharp.shared.platform.RecordingRef
import java.nio.file.Files
import java.io.File
import kotlin.test.*

class RecordingJournalTest {
    private val ref = RecordingRef("00000000-0000-0000-0000-000000000001.m4a", 35000)
    @Test fun finalizedMarkerSurvivesRecreationAndAcknowledgementNeverDeletesAudio() {
        val directory = Files.createTempDirectory("saysharp-journal").toFile()
        try {
            val audio = File(directory, ref.id).apply { writeText("private-test-audio") }
            RecordingJournal(directory).write(ref)
            assertEquals(ref, RecordingJournal(directory).read())
            RecordingJournal(directory).acknowledge(ref.copy(id = "00000000-0000-0000-0000-000000000002.m4a"))
            assertEquals(ref, RecordingJournal(directory).read())
            RecordingJournal(directory).acknowledge(ref)
            assertNull(RecordingJournal(directory).read())
            assertEquals("private-test-audio", audio.readText())
        } finally { directory.deleteRecursively() }
    }
    @Test fun partiallyWrittenTemporaryMarkerCannotReplacePublishedRecording() {
        val directory = Files.createTempDirectory("saysharp-journal").toFile()
        try {
            RecordingJournal(directory).write(ref)
            File(directory, "pending-recording.tmp").writeBytes(byteArrayOf(0, 1))
            assertEquals(ref, RecordingJournal(directory).read())
            val valid = File(directory, "another-valid.m4a").apply { writeText("kept") }
            RecordingJournal(directory).acknowledge(ref)
            assertTrue(valid.exists())
            assertNull(RecordingJournal(directory).read())
        } finally { directory.deleteRecursively() }
    }
    @Test fun failedMarkerWriteDoesNotDeleteAnyAudioOrInventAReference() {
        val directory = Files.createTempDirectory("saysharp-journal").toFile()
        try {
            val blocked = File(directory, "blocked").apply { writeText("not a directory") }
            val valid = File(directory, ref.id).apply { writeText("kept") }
            assertFails { RecordingJournal(blocked).write(ref) }
            assertTrue(valid.exists())
            assertEquals("not a directory", blocked.readText())
        } finally { directory.deleteRecursively() }
    }
}
