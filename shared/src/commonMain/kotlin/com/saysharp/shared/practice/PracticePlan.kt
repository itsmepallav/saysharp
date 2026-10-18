package com.saysharp.shared.practice

import com.saysharp.shared.session.BundledSessionRepository
import com.saysharp.shared.session.sessionStages

fun practiceStages(session: PracticeSession): List<PracticeStage> =
    sessionStages(session, BundledSessionRepository.definition(session.day))
