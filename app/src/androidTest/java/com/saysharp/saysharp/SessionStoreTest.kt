package com.saysharp.saysharp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saysharp.saysharp.storage.AndroidSessionStore
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    @Test fun sessionRoundTripsThroughPrivateAtomicStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "session-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store = AndroidSessionStore(dir)
            assertEquals(PracticeSession(), store.load())
            val snapshot = PracticeSession(
                stage = PracticeStage.Compare,
                recordings = mapOf(RecordingSlot.Baseline to RecordingRef("sample.m4a", 12_000)),
                firstAssessment = SelfAssessment.NotYet,
                retryAssessment = SelfAssessment.Unsure,
                comparison = Comparison.AboutTheSame,
                realLifeWin = "I said “no” clearly.\nA second line.",
                day = 4,
                recallAssessment = SelfAssessment.NotYet,
                recallHintUsed = true,
                completedDays = mapOf(1 to DayReview(realLifeWin = "First day"), 2 to DayReview(vocabularyAssessment = SelfAssessment.Unsure)),
            )
            store.save(snapshot)
            assertEquals(snapshot, AndroidSessionStore(dir).load())
            store.save(PracticeSession())
            assertEquals(PracticeSession(), store.load())
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test fun versionOneDataCanBeReadAndResavedWithoutLosingAudioIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "migration-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val json = """{"version":1,"stage":"Complete","recordings":{"Baseline":{"id":"original.m4a","durationMillis":12000}},"firstAssessment":"NotYet","retryAssessment":"Unsure","comparison":"AboutTheSame","realLifeWin":"Old note"}"""
            File(dir, "practice-session.json").writeText(json)
            val store = AndroidSessionStore(dir)
            val migrated = store.load()
            assertEquals(1, migrated.day)
            assertEquals("original.m4a", migrated.recordings.getValue(RecordingSlot.Baseline).id)
            assertEquals("Old note", migrated.realLifeWin)
            assertEquals(SelfAssessment.Unsure, migrated.retryAssessment)
            store.save(migrated)
            assertEquals(migrated, AndroidSessionStore(dir).load())
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }
}
