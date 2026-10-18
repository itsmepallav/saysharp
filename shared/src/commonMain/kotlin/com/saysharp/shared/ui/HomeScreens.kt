package com.saysharp.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.programme.*

@Composable
internal fun TodayScreen(onOpen: () -> Unit, onReview: () -> Unit) = Page {
    val programme = LocalSessionState.current.programme
    Eyebrow("Think Sharp. Speak Bold.")
    if (programme.isComplete) {
        Text("Programme complete", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text("You've completed all 14 lessons. Listen back to your saved answers and notice what has changed.")
        ProgrammeProgress(programme)
        PrimaryAction("Review my practice", onReview)
    } else {
        val lesson = requireNotNull(programme.currentLesson)
        Text("Day ${lesson.day} of 14", style = MaterialTheme.typography.titleMedium, color = Forest)
        Eyebrow(lesson.phase.title)
        Text(lesson.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
        PrimaryAction(if (programme.action == TodayAction.Resume) "Resume practice" else "Start today's practice", onOpen)
        ProgrammeProgress(programme)
    }
}

@Composable
private fun ProgrammeProgress(programme: ProgrammeOverview) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${programme.completedCount} of 14 lessons completed", style = MaterialTheme.typography.bodyMedium, color = Muted)
        ProgressTrack(programme.completedCount)
    }
}

@Composable
internal fun ProgrammeScreen(onOpen: () -> Unit, onReview: (Int) -> Unit) = Page {
    val programme = LocalSessionState.current.programme
    Text("Your programme", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
    Text(if (programme.isComplete) "Programme complete" else "14 lessons · two phases", style = MaterialTheme.typography.bodyLarge)
    ProgrammeProgress(programme)
    ProgrammePhase.entries.forEach { phase ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(phase.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = Forest)
            QuietText("Days ${phase.days.first}–${phase.days.last}")
            QuietText(phase.goal)
        }
        programme.lessons.filter { it.metadata.phase == phase }.forEach { lesson ->
            ProgrammeLessonRow(lesson, onOpen, onReview)
        }
    }
}

@Composable
private fun ProgrammeLessonRow(lesson: ProgrammeLesson, onOpen: () -> Unit, onReview: (Int) -> Unit) {
    val current = lesson.status in listOf(LessonStatus.Current, LessonStatus.InProgress)
    Surface(
        color = if (current) Sage else Paper,
        shape = RoundedCornerShape(18.dp),
        border = if (current) BorderStroke(1.dp, Forest) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(36.dp).background(if (lesson.status == LessonStatus.Completed) Sage else Paper, CircleShape), contentAlignment = Alignment.Center) {
                Text(lesson.metadata.day.toString(), style = MaterialTheme.typography.labelLarge, color = Forest)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lesson.metadata.title, style = MaterialTheme.typography.titleMedium)
                Text(when (lesson.status) {
                    LessonStatus.Completed -> "Day ${lesson.metadata.day} · Completed"
                    LessonStatus.Current -> "Day ${lesson.metadata.day} · Current lesson"
                    LessonStatus.InProgress -> "Day ${lesson.metadata.day} · In progress"
                    LessonStatus.Upcoming -> "Day ${lesson.metadata.day} · Upcoming"
                }, style = MaterialTheme.typography.bodySmall, color = if (current) Forest else Muted)
                if (lesson.status != LessonStatus.Upcoming) TextButton(
                    onClick = { if (lesson.status == LessonStatus.Completed) onReview(lesson.metadata.day) else onOpen() },
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp),
                ) {
                    Text(when (lesson.status) {
                        LessonStatus.Completed -> "Review Day ${lesson.metadata.day}"
                        LessonStatus.InProgress -> "Resume Day ${lesson.metadata.day}"
                        else -> "Start Day ${lesson.metadata.day}"
                    })
                }
            }
        }
    }
}
