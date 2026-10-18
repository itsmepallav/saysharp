package com.saysharp.shared.session

import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.practice.*
import com.saysharp.shared.platform.*
import com.saysharp.shared.programme.ProgrammeCatalogue
import com.saysharp.shared.programme.ProgrammePhase
import kotlin.test.*

class SessionCatalogueTest {
    @Test fun catalogueContainsFourteenStableUniqueIdsLoadableByIdOrDay() {
        assertEquals((1..14).toList(), SessionCatalogue.definitions.map { it.day })
        assertEquals((1..14).map { "saysharp-day-${it.toString().padStart(2, '0')}" }, SessionCatalogue.definitions.map { it.id })
        assertEquals(14, SessionCatalogue.definitions.map { it.id }.toSet().size)
        for (d in SessionCatalogue.definitions) {
            assertEquals(d, BundledSessionRepository.definition(d.day))
            assertEquals(d, BundledSessionRepository.definition(d.id))
            assertEquals(d.id, PracticeSession(day = d.day).lessonId)
            assertEquals(d.id, ProgrammeCatalogue.lesson(d.day).id)
            assertEquals(d.title, ProgrammeCatalogue.lesson(d.day).title)
        }
        assertFailsWith<IllegalArgumentException> { BundledSessionRepository.definition("saysharp-day-15") }
    }

    @Test fun requiredContentAndOneObservableQuestionArePopulatedForEveryOrdinaryDay() {
        for (d in SessionCatalogue.definitions.filterNot { it.isCheckpoint }) {
            listOf(d.title, d.objective, d.instruction, d.technique, d.scenario, d.selfCheck,
                d.listeningHint, d.weakExample, d.correctiveExample, d.exampleExplanation,
                d.correction, d.realWorldApplication).forEach { assertTrue(it.isNotBlank(), "Missing content: ${d.id}") }
            assertEquals(1, d.selfCheck.count { it == '?' }, d.id)
            assertEquals(d.prompts.getValue(RecordingSlot.First).text, d.prompts.getValue(RecordingSlot.Retry).text)
            assertNotEquals(d.prompts.getValue(RecordingSlot.First).text, d.prompts.getValue(RecordingSlot.Application).text)
            assertNotEquals(d.weakExample, d.correctiveExample)
            assertEquals(DayOneSession.stages, d.stages)
            assertTrue(d.prompts.values.all { it.id.startsWith(d.id) && it.limitMillis in 20_000..60_000 })
            assertTrue(d.suggestedSeconds.first > 0)
        }
    }

    @Test fun phasesAndCheckpointsAreMappedCorrectlyWithNoOrdinaryCoachingRoute() {
        assertEquals(listOf(7, 14), SessionCatalogue.definitions.filter { it.isCheckpoint }.map { it.day })
        for (d in SessionCatalogue.definitions) {
            assertEquals(if (d.day <= 7) ProgrammePhase.SpeakClearly else ProgrammePhase.SpeakWithConviction, d.phase)
            if (d.isCheckpoint) {
                val checkpoint = assertNotNull(d.checkpoint)
                assertEquals(DayOneSession.id, checkpoint.comparisonLessonId)
                assertTrue(checkpoint.reflection.isNotBlank())
                assertTrue(d.weakExample.isEmpty() && d.correctiveExample.isEmpty())
                assertFalse(PracticeStage.Lesson in d.stages)
                assertFalse(PracticeStage.Retry in d.stages)
                val familiar = d.prompts.getValue(RecordingSlot.CheckpointRepeat).text
                assertEquals(DayOneSession.scenario + "\n\n" + DayOneSession.prompts.getValue(RecordingSlot.First).text, familiar)
                assertNotEquals(familiar, d.prompts.getValue(RecordingSlot.CheckpointFresh).text)
                assertEquals(DayOneSession.id, BundledSessionRepository.definition(checkpoint.comparisonLessonId).id)
            }
        }
    }

    @Test fun dayTwelveTreatsReasonablePushbackAndThoughtfulRevisionAsAssertiveness() {
        val d = BundledSessionRepository.definition(12)
        assertTrue(d.scenario.contains("serious incident"))
        assertTrue(d.scenario.contains("cost us the account"))
        assertTrue(d.correctiveExample.contains("on-call"))
        assertTrue(d.correction.contains("change"))
        assertTrue(d.instruction.contains("Revising a position"))
        assertTrue(d.prompts.getValue(RecordingSlot.Application).text.contains("workload"))
        assertNotEquals(BundledSessionRepository.definition(11).selfCheck, d.selfCheck)
    }

    @Test fun modelAnswersAreAvailableOnlyAtThePostAssessmentCorrectionStage() {
        for (d in SessionCatalogue.definitions) for (stage in d.stages) {
            assertEquals(!d.isCheckpoint && stage == PracticeStage.Lesson, d.showsModelExample(stage), "${d.id}: $stage")
        }
    }

    @Test fun replayAndAnySelfAssessmentMustPrecedeCorrectionForEveryOrdinaryLesson() {
        val ref = RecordingRef("first", 35000)
        for (d in SessionCatalogue.definitions.filterNot { it.isCheckpoint }) {
            val first = PracticeSession(day = d.day, stage = PracticeStage.FirstAttempt)
            assertEquals(first, advanceSession(first, d))
            val check = advanceSession(first.copy(recordings = mapOf(RecordingSlot.First to ref)), d)
            assertEquals(PracticeStage.FirstCheck, check.stage)
            assertEquals(check, advanceSession(check, d))
            for (choice in SelfAssessment.entries) {
                val assessed = check.copy(firstAssessment = choice)
                assertEquals(assessed, advanceSession(assessed, d)) // Replay has not completed.
                val listened = assessed.copy(listenedRecordingIds = setOf(ref.id))
                assertEquals(PracticeStage.Lesson, advanceSession(listened, d).stage)
            }
        }
    }

    @Test fun checkpointsNeedBothNewRecordingsAndReplayBeforeCompletionReflection() {
        for (day in listOf(7, 14)) {
            val d = BundledSessionRepository.definition(day)
            val repeat = RecordingRef("repeat-$day", 35000)
            val fresh = RecordingRef("fresh-$day", 35000)
            val state = PracticeSession(day = day, stage = PracticeStage.CheckpointCompare,
                recordings = mapOf(RecordingSlot.CheckpointRepeat to repeat, RecordingSlot.CheckpointFresh to fresh),
                retryAssessment = SelfAssessment.Unsure, comparison = Comparison.LessClear)
            assertEquals(state, advanceSession(state, d))
            val oneReplay = state.copy(listenedRecordingIds = setOf(fresh.id))
            assertEquals(oneReplay, advanceSession(oneReplay, d))
            assertEquals(PracticeStage.Summary, advanceSession(state.copy(listenedRecordingIds = setOf(repeat.id, fresh.id)), d).stage)
        }
    }

    @Test fun entireProgrammeResumesByLessonIdAndNeverChangesEarlierDayReviews() {
        val audio = CurriculumAudio()
        val store = CurriculumStore()
        var controller = PracticeController(audio, store)
        for (day in 1..14) {
            val past = controller.session.completedDays.toMap()
            var count = 0
            while (controller.session.stage != PracticeStage.Complete) {
                assertTrue(count++ < 12, "Stuck on day $day")
                assertEquals(lessonId(day), controller.session.lessonId)
                assertEquals(BundledSessionRepository.definition(controller.session.lessonId), controller.definition)
                val stage = controller.session.stage
                stage.recordingSlot()?.let { controller.startRecording(); controller.stopRecording() }
                if (stage == PracticeStage.FirstCheck) {
                    controller.play(controller.session.recordings.getValue(RecordingSlot.First)); audio.finish!!()
                    controller.assess(SelfAssessment.Unsure)
                }
                if (stage == PracticeStage.CheckpointCompare) {
                    controller.play(controller.session.recordings.getValue(RecordingSlot.CheckpointRepeat)); audio.finish!!()
                    controller.play(controller.session.recordings.getValue(RecordingSlot.CheckpointFresh)); audio.finish!!()
                    controller.assess(SelfAssessment.NotYet, true); controller.compare(Comparison.AboutTheSame)
                }
                controller.advance()
                val saved = controller.session
                controller = PracticeController(audio, store)
                assertEquals(saved, controller.session)
                for ((previousDay, review) in past) assertEquals(review, controller.session.completedDays[previousDay])
            }
            assertEquals((1..day).toSet(), controller.session.completedDays.keys)
            assertEquals(if (day in listOf(7,14)) 2 else 3, controller.session.completedDays.getValue(day).recordings.size)
            assertEquals(day < 14, controller.beginNextDay())
        }
        assertEquals(40, audio.files.size)
        val first = store.value.completedDays.getValue(1).recordings.getValue(RecordingSlot.First)
        assertEquals(first, controller.baseline())
        assertTrue(controller.session.completedDays.values.flatMap { it.recordings.values }.all { audio.exists(it) })
    }
}
private class CurriculumStore : ProgressRepository {
    var value = PracticeSession()
    override fun load() = value
    override fun save(session: SessionProgress) { value = session }
}
private class CurriculumAudio : PracticeAudio {
    val files = mutableSetOf<RecordingRef>()
    private var next = 0
    var finish: (() -> Unit)? = null
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) {}
    override fun stop() = RecordingRef("curriculum-${next++}", 35000).also { files += it }
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = recording in files
    override fun delete(recording: RecordingRef) { files -= recording }
    override fun deleteAllRecordings() { files.clear() }
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) { check(exists(recording)); finish = onFinished }
    override fun stopPlayback() {}
}
