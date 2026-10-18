package com.saysharp.shared.session

/** One observable snapshot. Only the coordinator writes it; UI sends commands. */
data class SessionUiState(
    val progress: SessionProgress = com.saysharp.shared.practice.PracticeSession(),
    val isRecording: Boolean = false,
    val elapsedMillis: Long = 0L,
    val playingId: String? = null,
    val message: String? = null,
    val hasPendingRecording: Boolean = false,
) {
    val programme: com.saysharp.shared.programme.ProgrammeOverview
        get() = com.saysharp.shared.programme.programmeOverview(progress)
}

/** Existing callers share the same state; this is not a second model. */
typealias SessionState = SessionUiState
