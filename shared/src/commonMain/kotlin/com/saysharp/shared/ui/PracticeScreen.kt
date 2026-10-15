package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saysharp.shared.lesson.Programme
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.lesson.guidance
import com.saysharp.shared.practice.*
import kotlinx.coroutines.delay

@Composable
internal fun PracticeFlow(controller: PracticeController, requestRecording: () -> Unit, onDone: () -> Unit) {
    val session = controller.session
    val checkpoint = Programme.checkpoint(session.day)
    val lesson = if (checkpoint == null) Programme.lesson(session.day) else Programme.firstLesson
    val outline = Programme.outline[session.day - 1]
    // Reset scroll position and preparation time when moving to a new step.
    key(session.day, session.stage) {
        Column(Modifier.fillMaxSize()) {
        val stages = practiceStages(session)
        val step = (stages.indexOf(session.stage) + 1).coerceAtLeast(1)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow("Day ${session.day.toString().padStart(2, '0')} · ${outline.focus}", modifier = Modifier.weight(1f).padding(end = 12.dp))
                Text("$step / ${stages.size}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            ProgressTrack(step, stages.size)
        }
        Page(Modifier.weight(1f)) {
            when (session.stage) {
                PracticeStage.Welcome -> {
                    Text("Hear where you start.", style = MaterialTheme.typography.headlineLarge)
                    Text("First, record a short answer. Keep it natural. Later, you can listen back and notice what changed.")
                    Section("You are the judge", "Each drill asks you to check one clear behaviour. Done, not yet, and unsure are all valid answers.")
                    Text("Audio stays on this device and is not uploaded. Uninstalling the app removes it. You can delete your practice from Progress.")
                }
                PracticeStage.Baseline -> {
                    Text("Your first answer", style = MaterialTheme.typography.headlineLarge)
                    Text(Programme.baselinePrompt, style = MaterialTheme.typography.titleLarge)
                    Text("Take a moment to think. Speak naturally for up to 60 seconds.")
                    RecordingControls(controller, requestRecording, RecordingSlot.Baseline)
                    Text("Once you keep it, this baseline stays unchanged.", style = MaterialTheme.typography.bodySmall)
                }
                PracticeStage.Lesson -> {
                    Text(outline.title, style = MaterialTheme.typography.headlineLarge)
                    QuietText(outline.outcome)
                    Text(lesson.instruction, style = MaterialTheme.typography.titleLarge)
                    Section("Practice scenario", lesson.brief)
                }
                PracticeStage.FirstAttempt -> {
                    Text("Make your point.", style = MaterialTheme.typography.headlineLarge)
                    Text(lesson.prompt, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.First)
                }
                PracticeStage.FirstCheck -> {
                    Text("Check one thing", style = MaterialTheme.typography.headlineLarge)
                    Text("Listen for today's skill. You do not need to judge everything.")
                    session.recordings[RecordingSlot.First]?.let { Playback(controller, it, "First attempt", openingOnly = lesson.openingOnly) }
                    Section("A less focused answer", lesson.firstExample)
                    Section("A clearer answer", lesson.clearerExample)
                    Assessment(controller, session.firstAssessment, retry = false, question = lesson.selfCheck, clarification = lesson.guidance().listenFor)
                }
                PracticeStage.Retry -> {
                    Text("Make this answer clearer.", style = MaterialTheme.typography.headlineLarge)
                    Text(lesson.prompt, style = MaterialTheme.typography.titleLarge)
                    Section("Change one thing", lesson.guidance().retryFocus)
                    QuietText(lesson.scaffold)
                    RecordingControls(controller, requestRecording, RecordingSlot.Retry)
                }
                PracticeStage.Compare -> {
                    Text("Hear the difference", style = MaterialTheme.typography.headlineLarge)
                    session.recordings[RecordingSlot.First]?.let { Playback(controller, it, "First attempt") }
                    session.recordings[RecordingSlot.Retry]?.let { Playback(controller, it, "Retry") }
                    Assessment(controller, session.retryAssessment, retry = true, question = lesson.selfCheck, clarification = lesson.guidance().listenFor)
                    Text("Compared with your first attempt, your retry felt:", style = MaterialTheme.typography.titleMedium)
                    Comparison.entries.forEach { value ->
                        Choice(comparisonLabel(value), session.comparison == value) { controller.compare(value) }
                    }
                }
                PracticeStage.Apply -> {
                    Text("Make it work on a new question.", style = MaterialTheme.typography.headlineLarge)
                    Text(lesson.applicationPrompt, style = MaterialTheme.typography.titleLarge)
                    Text("Use your own words. There is no required position.")
                    RecordingControls(controller, requestRecording, RecordingSlot.Application)
                }
                PracticeStage.Summary -> {
                    Text("Your next win\nis out there.", style = MaterialTheme.typography.headlineLarge)
                    Section("Make it count today", checkpoint?.realLifeTask ?: lesson.realLifeTask)
                    QuietText(outline.outcome)
                }
                PracticeStage.Complete -> {
                    Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Glyph(Symbol.Check, Forest, Modifier.size(30.dp)) }
                    Text("Day ${session.day} complete", style = MaterialTheme.typography.headlineLarge)
                    Text(if (session.day == 14) "Your answers are saved. Compare Day 1 and Day 14: what sounds sharper or clearer?" else outline.outcome)
                    Section("Use it today", checkpoint?.realLifeTask ?: lesson.realLifeTask)
                }
                PracticeStage.Vocabulary -> {
                    val word = requireNotNull(lesson.vocabulary)
                    Text("Make it part of your speech", style = MaterialTheme.typography.headlineLarge)
                    Section(word.phrase, word.meaning)
                    Section("An example", word.example)
                    Text(word.prompt, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.Vocabulary)
                    VocabularyAssessment(controller, session.vocabularyAssessment, "Did the expression fit your meaning?")
                }
                PracticeStage.Recall -> {
                    val words = Programme.recalls(session.day)
                    Text("Bring an expression back", style = MaterialTheme.typography.headlineLarge)
                    Text("Try to retrieve the expression before revealing it. Use it naturally, rather than just saying its name.")
                    words.forEach { Text(it.recallPrompt, style = MaterialTheme.typography.titleLarge) }
                    if (session.recallHintUsed) words.forEach { Section(it.phrase, it.meaning) }
                    else TextButton(onClick = controller::revealRecall) { Text("Reveal the expression") }
                    RecordingControls(controller, requestRecording, RecordingSlot.Recall)
                    VocabularyAssessment(controller, session.recallAssessment, "Could you use the expression naturally?")
                    Text(if (session.recallHintUsed) "You used a hint. That is recorded separately from your self-check." else "No hint revealed for this attempt.", style = MaterialTheme.typography.bodySmall)
                }
                PracticeStage.CheckpointRepeat -> {
                    Text("The same question. A new answer.", style = MaterialTheme.typography.headlineLarge)
                    Text("Answer without replaying your baseline first. There is no scaffold for this checkpoint.")
                    Text(Programme.baselinePrompt, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.CheckpointRepeat)
                }
                PracticeStage.CheckpointFresh -> {
                    Text("Now try an unfamiliar question", style = MaterialTheme.typography.headlineLarge)
                    Text(requireNotNull(checkpoint).freshPrompt, style = MaterialTheme.typography.titleLarge)
                    Text("Use your own structure. Speak for up to 60 seconds.")
                    RecordingControls(controller, requestRecording, RecordingSlot.CheckpointFresh)
                }
                PracticeStage.CheckpointCompare -> {
                    Text("Listen for change", style = MaterialTheme.typography.headlineLarge)
                    controller.baseline()?.let { Playback(controller, it, "Original baseline") }
                    session.recordings[RecordingSlot.CheckpointRepeat]?.let { Playback(controller, it, "Same question · Day ${session.day}") }
                    Text("Compared with your baseline, your new answer felt:", style = MaterialTheme.typography.titleMedium)
                    Comparison.entries.forEach { value -> Choice(comparisonLabel(value), session.comparison == value) { controller.compare(value) } }
                    session.recordings[RecordingSlot.CheckpointFresh]?.let { Playback(controller, it, "Fresh question · Day ${session.day}") }
                    Assessment(controller, session.retryAssessment, true, "On the fresh question, did your first sentence answer the question?", "A direct opening states the choice before giving background.")
                    Text("Getting better at a repeated answer does not by itself prove improvement on new questions.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Surface(color = Paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                PracticeAction(controller, requestRecording, onDone)
            }
        }
        }
    }
}

@Composable
internal fun PracticeAction(controller: PracticeController, requestRecording: () -> Unit, onDone: () -> Unit) {
    val s = controller.session
    val slot = s.stage.recordingSlot()
    if (controller.isRecording) {
        PrimaryAction("Stop and save attempt", { controller.stopRecording() }, symbol = Symbol.Stop)
        return
    }
    if (slot != null && slot !in s.recordings) {
        PrimaryAction("Start recording", requestRecording, symbol = Symbol.Mic)
        return
    }
    val (label, enabled) = when (s.stage) {
        PracticeStage.Welcome -> "Record my starting point" to true
        PracticeStage.Baseline -> "Keep this baseline" to true
        PracticeStage.Lesson -> "Try it myself" to true
        PracticeStage.FirstAttempt -> "Listen and check" to true
        PracticeStage.FirstCheck -> "Practise the skill again" to (s.firstAssessment != null)
        PracticeStage.Retry -> "Compare my attempts" to true
        PracticeStage.Compare -> (if (Programme.lesson(s.day).vocabulary != null) "Practise an expression" else "Try a fresh question") to (s.retryAssessment != null && s.comparison != null)
        PracticeStage.Apply -> "Finish my practice" to true
        PracticeStage.Summary -> "Complete Day ${s.day}" to true
        PracticeStage.Complete -> "Hear my progress" to true
        PracticeStage.Vocabulary -> "Try a fresh question" to (s.vocabularyAssessment != null)
        PracticeStage.Recall -> (if (Programme.checkpoint(s.day) != null) "Finish checkpoint" else "Continue to today's skill") to (s.recallAssessment != null)
        PracticeStage.CheckpointRepeat -> "Answer a fresh question" to true
        PracticeStage.CheckpointFresh -> "Listen and compare" to true
        PracticeStage.CheckpointCompare -> (if (Programme.recalls(s.day).isEmpty()) "Finish checkpoint" else "Recall an expression") to (s.comparison != null && s.retryAssessment != null)
    }
    PrimaryAction(label, if (s.stage == PracticeStage.Complete) onDone else controller::advance, enabled)
}

@Composable
internal fun RecordingControls(controller: PracticeController, requestRecording: () -> Unit, slot: RecordingSlot) {
    val ref = controller.session.recordings[slot]
    var preparation by remember { mutableStateOf(15) }
    var preparing by remember { mutableStateOf(ref == null) }
    LaunchedEffect(preparing) {
        while (preparing && preparation > 0) { delay(1_000); preparation-- }
        preparing = false
    }
    if (ref == null || controller.isRecording) {
        val remaining = ((controller.limitMillis - controller.elapsedMillis + 999) / 1000).coerceAtLeast(0)
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (controller.isRecording) (controller.elapsedMillis.toFloat() / controller.limitMillis).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxSize(), color = Clay, trackColor = Line, strokeWidth = 3.dp)
                Box(Modifier.size(88.dp).background(if (controller.isRecording) Forest else Sage, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(Symbol.Mic, if (controller.isRecording) Color.White else Forest, Modifier.size(32.dp))
                }
            }
            Text(if (controller.isRecording) "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}" else "Ready when you are", style = MaterialTheme.typography.titleLarge)
            QuietText(if (controller.isRecording) "Recording · seconds remaining" else if (preparing) "Take ${preparation}s to gather your thoughts." else "Up to ${controller.limitMillis / 1000} seconds. Use your own words.")
        }
    } else {
        Playback(controller, ref, "Saved attempt")
        TextButton(onClick = { preparing = false; requestRecording() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Record again") }
        QuietText("Your previous attempt stays saved until you finish a new one.")
    }
}

@Composable
internal fun Assessment(controller: PracticeController, selected: SelfAssessment?, retry: Boolean, question: String, clarification: String) {
    Text(question, style = MaterialTheme.typography.titleLarge)
    SelfAssessment.entries.forEach { value ->
        Choice(assessmentLabel(value), selected == value) { controller.assess(value, retry) }
    }
    if (selected == SelfAssessment.Unsure) Section("What to listen for", clarification)
}

@Composable
internal fun VocabularyAssessment(controller: PracticeController, selected: SelfAssessment?, question: String) {
    Text(question, style = MaterialTheme.typography.titleMedium)
    SelfAssessment.entries.forEach { value -> Choice(assessmentLabel(value), selected == value) { controller.assessVocabulary(value) } }
}

