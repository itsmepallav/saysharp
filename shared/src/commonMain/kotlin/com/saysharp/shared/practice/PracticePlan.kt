package com.saysharp.shared.practice

import com.saysharp.shared.lesson.Programme

/** One sequence for controller transitions and the UI step counter. Stored stage names stay stable. */
fun practiceStages(session: PracticeSession): List<PracticeStage> = buildList {
    if (session.day == 1) { add(PracticeStage.Welcome); add(PracticeStage.Baseline) }
    else if (session.stage == PracticeStage.Baseline) add(PracticeStage.Baseline)
    if (Programme.checkpoint(session.day) != null) {
        addAll(listOf(PracticeStage.CheckpointRepeat, PracticeStage.CheckpointFresh, PracticeStage.CheckpointCompare))
        if (Programme.recalls(session.day).isNotEmpty()) add(PracticeStage.Recall)
    } else {
        if (Programme.recalls(session.day).isNotEmpty()) add(PracticeStage.Recall)
        addAll(listOf(PracticeStage.Lesson, PracticeStage.FirstAttempt, PracticeStage.FirstCheck, PracticeStage.Retry, PracticeStage.Compare))
        if (Programme.lesson(session.day).vocabulary != null) add(PracticeStage.Vocabulary)
        add(PracticeStage.Apply)
    }
    add(PracticeStage.Summary); add(PracticeStage.Complete)
}

