package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saysharp.shared.session.BundledSessionRepository
import com.saysharp.shared.practice.*
import com.saysharp.shared.engagement.*

@Composable
internal fun ProgressScreen(controller: PracticeController, requestedDay: Int?, onSelectDay: (Int) -> Unit, onPractice: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showMilestones by rememberSaveable { mutableStateOf(false) }
    var showWin by rememberSaveable { mutableStateOf(false) }
    val state = LocalSessionState.current
    val session = state.progress
    val availableDays = (session.completedDays.keys + session.day).sorted()
    val day = requestedDay?.takeIf { it in availableDays } ?: session.day
    val review = session.completedDays[day] ?: session.review()
    val definition = BundledSessionRepository.definition(day)
    val checkpoint = definition.checkpoint
    if (showMilestones) AlertDialog(
        onDismissRequest = { showMilestones = false },
        title = { Text("Every step counts.", style = MaterialTheme.typography.headlineMedium) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { MilestoneCollection(session.completedDays.keys) } },
        confirmButton = { TextButton(onClick = { showMilestones = false }) { Text("Keep going") } },
    )
    if (showWin) AlertDialog(
        onDismissRequest = { showWin = false },
        icon = { Glyph(Symbol.Check, Forest, Modifier.size(32.dp)) },
        title = { Text("Your real-life win is saved.") },
        text = { Text("Keep the moments that matter. You can return to this note alongside your recordings.") },
        confirmButton = { TextButton(onClick = { showWin = false }) { Text("Keep building on it") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete your practice?") },
        text = { Text("This removes your saved recordings, self-checks, and note from this device. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { controller.clearData(); confirmDelete = false }) { Text("Delete practice") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep practice") } },
    )
    Page {
        Eyebrow("Small steps, real change")
        Text("Your voice,\nover time.", style = MaterialTheme.typography.headlineLarge)
        QuietText("Listen back. Notice what feels clearer.")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${session.completedDays.size} of 14 sessions completed", style = MaterialTheme.typography.bodyMedium, color = Muted)
            ProgressTrack(session.completedDays.size)
        }
        val earned = Milestones.count { milestone -> (1..milestone.sessions).all { it in session.completedDays } }
        TextButton(onClick = { showMilestones = true }) { Text("Your milestones · $earned / ${Milestones.size}") }
        if (controller.baseline() == null && session.completedDays.isEmpty() && review.recordings.isEmpty() && review.realLifeWin.isBlank()) {
            Panel {
                Box(Modifier.size(56.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Glyph(Symbol.Mic, Forest, Modifier.size(26.dp)) }
                Text("Every voice has\na starting point.", style = MaterialTheme.typography.headlineMedium)
                QuietText("Record your first answer. Your attempts and reflections will find a home here.")
                PrimaryAction("Start practice", onPractice)
            }
            QuietText("You judge your progress. There are no AI scores or rankings.")
            return@Page
        }
        controller.baseline()?.let { Playback(controller, it, "Your starting point") }
        Eyebrow("Practice journal")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableDays.forEach { number -> FilterChip(selected = day == number, onClick = { onSelectDay(number) }, label = { Text("Day $number") },
                modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Forest, selectedLabelColor = Color.White)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(definition.title, style = MaterialTheme.typography.headlineMedium)
            Eyebrow(if (day in session.completedDays) "Day $day · Completed" else "Day $day · In progress", Forest)
        }
        if (review.recordings.isEmpty()) QuietText("Your practice attempts will appear here.")
        RecordingSlot.entries.filter { it != RecordingSlot.Baseline }.forEach { slot ->
            review.recordings[slot]?.let { ref ->
                Playback(controller, ref, when (slot) {
                    RecordingSlot.Baseline -> "Your starting point"
                    RecordingSlot.First -> "Day $day · First attempt"
                    RecordingSlot.Retry -> "Day $day · Retry"
                    RecordingSlot.Application -> "Day $day · Fresh question"
                    RecordingSlot.Vocabulary -> "Day $day · Expression practice"
                    RecordingSlot.Recall -> "Day $day · Expression recall"
                    RecordingSlot.CheckpointRepeat -> "Day $day · Same question as baseline"
                    RecordingSlot.CheckpointFresh -> "Day $day · Unfamiliar question"
                })
            }
        }
        if (review.firstAssessment != null || review.retryAssessment != null) {
            val question = definition.selfCheck
            Panel(color = Sage) {
                Eyebrow("Your reflections", Forest)
                Text(question, style = MaterialTheme.typography.titleMedium)
                if (checkpoint == null) ReflectionRow("First attempt", review.firstAssessment?.let(::assessmentLabel) ?: "Not checked")
                if (checkpoint != null || review.retryAssessment != null) ReflectionRow(if (checkpoint == null) "Retry" else "Fresh question", review.retryAssessment?.let(::assessmentLabel) ?: "Not checked")
                review.comparison?.let { HorizontalDivider(color = Line); ReflectionRow(if (checkpoint != null) "Compared with baseline" else "Compared with first", comparisonLabel(it)) }
                QuietText("Your own assessment, not an objective score.")
            }
        }
        review.vocabularyAssessment?.let { Text("Expression practice: ${assessmentLabel(it)}") }
        review.recallAssessment?.let { Text("Expression recall: ${assessmentLabel(it)} · ${if (review.recallHintUsed) "Hint used" else "No hint revealed"}") }
        var note by remember(day, review.realLifeWin) { mutableStateOf(review.realLifeWin) }
        var noteSaved by remember(note) { mutableStateOf(false) }
        Eyebrow("Beyond the app")
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(500) },
            label = { Text("A real-life win (optional)") },
            placeholder = { Text("I explained my proposal without overexplaining.") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
        )
        TextButton(onClick = {
            noteSaved = controller.saveWin(note, day)
            if (noteSaved && note.isNotBlank()) showWin = true
        }, enabled = note != review.realLifeWin) { Text(if (noteSaved) "Note saved" else "Save my win") }
        HorizontalDivider(color = Line)
        QuietText("Private to this device. No cloud backup. Uninstalling removes your practice.")
        TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete all practice") }
    }
}

@Composable
internal fun ReflectionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
