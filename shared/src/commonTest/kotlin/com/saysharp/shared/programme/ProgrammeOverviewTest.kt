package com.saysharp.shared.programme

import com.saysharp.shared.platform.*
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

class ProgrammeOverviewTest {
    private fun history(count: Int) = (1..count).associateWith { DayReview() }
    private val transfer = RecordingRef("transfer", 35_000)
    private fun finishDay(day: Int) = PracticeSession(day = day,
        stage = if (day in listOf(7, 14)) PracticeStage.Summary else PracticeStage.Apply,
        recordings = mapOf(RecordingSlot.Application to transfer), completedDays = history(day - 1)).withRequiredExercise()

    @Test fun freshUserHasExactlyOneCurrentDayAndStartsAtDayOne() {
        val overview = programmeOverview(PracticeSession())
        assertEquals(1, overview.currentLesson?.day)
        assertEquals("Lead with your point", overview.currentLesson?.title)
        assertEquals(ProgrammePhase.SpeakClearly, overview.currentLesson?.phase)
        assertEquals(TodayAction.Start, overview.action)
        assertEquals(0, overview.completedCount)
        assertEquals(LessonStatus.Current, overview.lessons.first().status)
        assertTrue(overview.lessons.drop(1).all { it.status == LessonStatus.Upcoming })
    }

    @Test fun catalogueHasFourteenExistingDefinitionsAndCorrectPhaseBoundaries() {
        assertEquals((1..14).toList(), ProgrammeCatalogue.lessons.map { it.day })
        for (metadata in ProgrammeCatalogue.lessons) {
            assertTrue(metadata.title.isNotBlank() && metadata.explanation.isNotBlank())
            assertEquals(metadata.day, BundledSessionRepository.definition(metadata.day).day)
            assertEquals(if (metadata.day <= 7) ProgrammePhase.SpeakClearly else ProgrammePhase.SpeakWithConviction, metadata.phase)
        }
    }

    @Test fun completionRefreshesProgressAndSelectsNextLessonWithoutAWait() {
        val progress = advanceSession(finishDay(1), BundledSessionRepository.definition(1))
        val overview = programmeOverview(progress)
        assertEquals(1, overview.completedCount)
        assertEquals(LessonStatus.Completed, overview.lessons[0].status)
        assertEquals(LessonStatus.Current, overview.lessons[1].status)
        assertEquals(2, overview.currentLesson?.day)
        assertEquals(TodayAction.Start, overview.action)
    }

    @Test fun interruptedAndPartiallyCompletedSessionShowsResumeWithoutChangingDay() {
        for (stage in listOf(PracticeStage.FirstAttempt, PracticeStage.FirstCheck, PracticeStage.Retry, PracticeStage.Apply)) {
            val overview = programmeOverview(PracticeSession(stage = stage, recordingInProgress = true))
            assertEquals(1, overview.currentLesson?.day)
            assertEquals(TodayAction.Resume, overview.action)
            assertEquals(LessonStatus.InProgress, overview.lessons[0].status)
            assertEquals(0, overview.completedCount)
        }
    }

    @Test fun daySevenCompletionSelectsDayEightInSecondPhase() {
        val complete = advanceSession(finishDay(7), BundledSessionRepository.definition(7))
        val overview = programmeOverview(complete)
        assertEquals(7, overview.completedCount)
        assertEquals(8, overview.currentLesson?.day)
        assertEquals(ProgrammePhase.SpeakWithConviction, overview.currentLesson?.phase)
        assertEquals(TodayAction.Start, overview.action)
        assertTrue(overview.lessons.take(7).all { it.status == LessonStatus.Completed })
    }

    @Test fun dayFourteenCompletionHasNoNextLessonAndCannotStartDayFifteen() {
        val store = OverviewStore(finishDay(14))
        val controller = PracticeController(OverviewAudio(), store)
        controller.advance()
        val overview = controller.state.value.programme
        assertTrue(overview.isComplete)
        assertNull(overview.currentLesson)
        assertEquals(14, overview.completedCount)
        assertEquals(TodayAction.Review, overview.action)
        assertTrue(overview.lessons.all { it.status == LessonStatus.Completed })
        val saved = store.value
        assertFalse(controller.beginNextDay())
        controller.advance()
        assertEquals(saved, store.value)
    }

    @Test fun savedProgressAndResumeSurviveControllerRecreation() {
        val store = OverviewStore(PracticeSession(day = 8, stage = PracticeStage.FirstCheck,
            completedDays = history(7), recordings = mapOf(RecordingSlot.First to transfer)))
        val before = PracticeController(OverviewAudio(), store).state.value
        val restored = PracticeController(OverviewAudio(), store).state.value
        assertEquals(before.progress, restored.progress)
        assertEquals(before.programme, restored.programme)
        assertEquals(TodayAction.Resume, restored.programme.action)
        assertEquals(8, restored.programme.currentLesson?.day)
    }

    @Test fun todayAndProgrammeAlwaysUseTheSameCurrentLessonAndCompletionCount() {
        for (day in 1..14) {
            for (stage in listOf(PracticeStage.FirstAttempt, PracticeStage.Complete)) {
                val progress = PracticeSession(day = day, stage = stage,
                    completedDays = history(if (stage == PracticeStage.Complete) day else day - 1))
                val overview = SessionUiState(progress).programme
                val currentRows = overview.lessons.filter { it.status in listOf(LessonStatus.Current, LessonStatus.InProgress) }
                assertEquals(overview.currentLesson?.day, currentRows.singleOrNull()?.metadata?.day)
                assertEquals(overview.completedCount, overview.lessons.count { it.status == LessonStatus.Completed })
                assertEquals(!overview.isComplete, currentRows.size == 1)
            }
        }
    }

    @Test fun futureDaysCannotCompleteWithoutEveryPrecedingLesson() {
        for (day in 2..14) {
            val future = finishDay(day).copy(completedDays = history(day - 2))
            assertEquals(future, advanceSession(future, BundledSessionRepository.definition(day)))
            assertFalse(day in programmeOverview(future).lessons.filter { it.status == LessonStatus.Completed }.map { it.metadata.day })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun flowOnlyRefreshesScreensAfterSuccessfulPersistenceAndRetry() = runTest {
        val store = OverviewStore(finishDay(1)).apply { failSave = true }
        val controller = PracticeController(OverviewAudio(), store)
        val snapshots = mutableListOf<ProgrammeOverview>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.state.collect { snapshots += it.programme }
        }
        controller.advance()
        assertEquals(0, controller.state.value.programme.completedCount)
        assertTrue(snapshots.all { it.currentLesson?.day == 1 })
        store.failSave = false
        controller.advance()
        assertEquals(1, snapshots.last().completedCount)
        assertEquals(2, snapshots.last().currentLesson?.day)
        assertEquals(controller.state.value.programme, PracticeController(OverviewAudio(), store).state.value.programme)
    }
}

private class OverviewStore(var value: PracticeSession) : ProgressRepository {
    var failSave = false
    override fun load() = value
    override fun save(session: SessionProgress) { check(!failSave); value = session }
}
private class OverviewAudio : PracticeAudio {
    override fun start(limitMillis: Long, onLimit: () -> Unit, onError: () -> Unit) = error("Not used")
    override fun stop(): RecordingRef = error("Not used")
    override fun cancel() {}
    override fun exists(recording: RecordingRef) = true
    override fun delete(recording: RecordingRef) = error("Not used")
    override fun deleteAllRecordings() = error("Not used")
    override fun monotonicMillis() = 0L
    override fun play(recording: RecordingRef, openingOnly: Boolean, onFinished: () -> Unit, onError: () -> Unit) {}
    override fun stopPlayback() {}
}
