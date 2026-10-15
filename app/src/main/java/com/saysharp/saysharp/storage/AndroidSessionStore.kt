package com.saysharp.saysharp.storage

import android.util.AtomicFile
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.Comparison
import com.saysharp.shared.practice.PracticeSession
import com.saysharp.shared.practice.PracticeStage
import com.saysharp.shared.practice.RecordingSlot
import com.saysharp.shared.practice.SessionStore
import com.saysharp.shared.practice.DayReview
import com.saysharp.shared.practice.review
import org.json.JSONObject
import java.io.File

class AndroidSessionStore(directory: File) : SessionStore {
    private val file = AtomicFile(File(directory.apply { mkdirs() }, "practice-session.json"))

    override fun load(): PracticeSession {
        // AtomicFile.openRead restores a backup left by an interrupted write.
        val bytes = try { file.readFully() } catch (error: java.io.FileNotFoundException) {
            if (!file.baseFile.exists()) return PracticeSession() else throw error
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val version = json.getInt("version")
        check(version in 1..2) { "Unsupported session version" }
        val day = if (version == 1) 1 else json.getInt("day")
        require(day in 1..14)
        val review = readReview(json)
        val history = json.optJSONObject("completedDays") ?: JSONObject()
        return PracticeSession(
            stage = PracticeStage.valueOf(json.getString("stage")),
            day = day, recordings = review.recordings,
            firstAssessment = review.firstAssessment, retryAssessment = review.retryAssessment,
            comparison = review.comparison, realLifeWin = review.realLifeWin,
            vocabularyAssessment = review.vocabularyAssessment, recallAssessment = review.recallAssessment,
            recallHintUsed = review.recallHintUsed,
            completedDays = history.keys().asSequence().associate { key ->
                val historyDay = key.toInt().also { require(it in 1..14) }
                historyDay to readReview(history.getJSONObject(key))
            },
        )
    }

    private fun readReview(json: JSONObject): DayReview {
        val records = json.getJSONObject("recordings")
        val recordings = records.keys().asSequence().associate { key ->
            val item = records.getJSONObject(key)
            RecordingSlot.valueOf(key) to RecordingRef(item.getString("id"), item.getLong("durationMillis"))
        }
        return DayReview(
            recordings = recordings,
            firstAssessment = json.optString("firstAssessment").takeIf { it.isNotBlank() }?.let(SelfAssessment::valueOf),
            retryAssessment = json.optString("retryAssessment").takeIf { it.isNotBlank() }?.let(SelfAssessment::valueOf),
            comparison = json.optString("comparison").takeIf { it.isNotBlank() }?.let(Comparison::valueOf),
            realLifeWin = json.optString("realLifeWin"),
            vocabularyAssessment = json.optString("vocabularyAssessment").takeIf { it.isNotBlank() }?.let(SelfAssessment::valueOf),
            recallAssessment = json.optString("recallAssessment").takeIf { it.isNotBlank() }?.let(SelfAssessment::valueOf),
            recallHintUsed = json.optBoolean("recallHintUsed", false),
        )
    }

    private fun writeReview(review: DayReview): JSONObject {
        val records = JSONObject()
        review.recordings.forEach { (slot, ref) ->
            records.put(slot.name, JSONObject().put("id", ref.id).put("durationMillis", ref.durationMillis))
        }
        return JSONObject()
            .put("recordings", records)
            .put("firstAssessment", review.firstAssessment?.name.orEmpty())
            .put("retryAssessment", review.retryAssessment?.name.orEmpty())
            .put("comparison", review.comparison?.name.orEmpty())
            .put("realLifeWin", review.realLifeWin)
            .put("vocabularyAssessment", review.vocabularyAssessment?.name.orEmpty())
            .put("recallAssessment", review.recallAssessment?.name.orEmpty())
            .put("recallHintUsed", review.recallHintUsed)
    }

    override fun save(session: PracticeSession) {
        val history = JSONObject()
        session.completedDays.forEach { (day, review) -> history.put(day.toString(), writeReview(review)) }
        val json = writeReview(session.review().copy(recordings = session.recordings))
            .put("version", 2).put("stage", session.stage.name).put("day", session.day)
            .put("completedDays", history)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
