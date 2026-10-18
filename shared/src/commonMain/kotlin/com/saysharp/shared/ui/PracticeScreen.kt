package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.practice.*
import com.saysharp.shared.session.sessionStages
import com.saysharp.shared.session.showsModelExample
import kotlinx.coroutines.delay

@Composable
internal fun PracticeFlow(controller: PracticeController, requestRecording: () -> Unit, onDone: () -> Unit) {
    val state = LocalSessionState.current
    val session = state.progress
    val definition = controller.definition
    var reflection by rememberSaveable(session.day) { mutableStateOf(session.realLifeWin) }
    // Reset scroll position and preparation time when moving to a new step.
    key(session.day, session.stage) {
        Column(Modifier.fillMaxSize()) {
        val stages = sessionStages(session, definition)
        val step = (stages.indexOf(session.stage) + 1).coerceAtLeast(1)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow("Day ${session.day.toString().padStart(2, '0')} · ${definition.phase.title}", modifier = Modifier.weight(1f).padding(end = 12.dp))
                Text("$step / ${stages.size}", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            ProgressTrack(step, stages.size)
        }
        Page(Modifier.weight(1f)) {
            when (session.stage) {
                PracticeStage.Welcome -> {
                    Text(definition.title, style = MaterialTheme.typography.headlineLarge)
                    Section("Today's technique", definition.technique)
                    Text(definition.instruction)
                    Section("Your situation", definition.scenario)
                    QuietText("Three short recordings. You judge one behavior. Audio stays on this device.")
                }
                PracticeStage.Baseline -> {
                    Text("Your starting answer", style = MaterialTheme.typography.headlineLarge)
                    Text(definition.prompts.getValue(RecordingSlot.Baseline).text, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.Baseline)
                }
                PracticeStage.FirstAttempt -> {
                    Text("Make your point.", style = MaterialTheme.typography.headlineLarge)
                    Section("Your situation", definition.scenario)
                    Text(definition.prompts.getValue(RecordingSlot.First).text, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.First)
                }
                PracticeStage.FirstCheck -> {
                    Text("Listen for one thing", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                    Assessment(controller, session.firstAssessment, false, definition.selfCheck, definition.listeningHint)
                }
                PracticeStage.Lesson -> {
                    if (definition.showsModelExample(session.stage)) {
                        Text(if (session.day == 1) "Put the point first" else "One useful contrast", style = MaterialTheme.typography.headlineLarge)
                        Section("Less effective", definition.weakExample)
                        Section("More effective", definition.correctiveExample)
                        Section("Why it works", definition.exampleExplanation)
                        Section("Change one thing", definition.correction)
                        QuietText("Use your own words. You can take a different position from the example.")
                    }
                }
                PracticeStage.Retry -> {
                    Text("Retry · Attempt 2", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                    Section("Same situation", definition.scenario)
                    Text(definition.prompts.getValue(RecordingSlot.Retry).text, style = MaterialTheme.typography.titleLarge)
                    QuietText(definition.correction)
                    RecordingControls(controller, requestRecording, RecordingSlot.Retry)
                }
                PracticeStage.Compare -> {
                    Text("Compare your attempts", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                    session.recordings[RecordingSlot.First]?.let { Playback(controller, it, "First attempt") }
                    session.recordings[RecordingSlot.Retry]?.let { Playback(controller, it, "Retry") }
                    Text(definition.selfCheck)
                    QuietText("Listening is optional. Focus on the correction, then try a new situation.")
                }
                PracticeStage.Apply -> {
                    Text("Make it work on a new question.", style = MaterialTheme.typography.headlineLarge)
                    Text(definition.prompts.getValue(RecordingSlot.Application).text, style = MaterialTheme.typography.titleLarge)
                    Text("Use your own words. There is no required position.")
                    RecordingControls(controller, requestRecording, RecordingSlot.Application)
                }
                PracticeStage.CheckpointRepeat -> {
                    Text("The same question. A new answer.", style = MaterialTheme.typography.headlineLarge)
                    Text("Answer without replaying Day 1 first. Choose your own structure; no model answer is provided.")
                    Text(definition.prompts.getValue(RecordingSlot.CheckpointRepeat).text, style = MaterialTheme.typography.titleLarge)
                    RecordingControls(controller, requestRecording, RecordingSlot.CheckpointRepeat)
                }
                PracticeStage.CheckpointFresh -> {
                    Text("Now try an unfamiliar question", style = MaterialTheme.typography.headlineLarge)
                    Text(definition.prompts.getValue(RecordingSlot.CheckpointFresh).text, style = MaterialTheme.typography.titleLarge)
                    QuietText("Answer independently. There is no required position.")
                    RecordingControls(controller, requestRecording, RecordingSlot.CheckpointFresh)
                }
                PracticeStage.CheckpointCompare -> {
                    Text("Listen for change", style = MaterialTheme.typography.headlineLarge)
                    controller.baseline()?.let { Playback(controller, it, "Day 1 · First attempt") }
                        ?: QuietText("Your Day 1 recording is unavailable. You can still reflect on today's answers.")
                    session.recordings[RecordingSlot.CheckpointRepeat]?.let { Playback(controller, it, "Familiar question · Day ${session.day}") }
                    Text("Compared with Day 1, your familiar answer felt:", style = MaterialTheme.typography.titleMedium)
                    Comparison.entries.forEach { value -> Choice(comparisonLabel(value), session.comparison == value) { controller.compare(value) } }
                    Assessment(controller, session.retryAssessment, true, definition.selfCheck, definition.listeningHint)
                    QuietText(requireNotNull(definition.checkpoint).reflection)
                    QuietText("Replay both of today's answers before continuing. Your own judgment is not a proficiency score.")
                }
                PracticeStage.Summary -> {
                    Text("Take one habit with you", style = MaterialTheme.typography.headlineLarge)
                    Text(requireNotNull(definition.checkpoint).reflection)
                    Section("Use it today", definition.realWorldApplication)
                }
                PracticeStage.Complete -> {
                    Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Glyph(Symbol.Check, Forest, Modifier.size(30.dp)) }
                    Text("Day ${session.day} complete", style = MaterialTheme.typography.headlineLarge)
                    Text(if (definition.isCheckpoint) "Your familiar and fresh answers are saved. Revisit them in Progress."
                        else "Three answers saved. Listen back to your first attempt, retry and fresh question from Progress.")
                    Section("Use it today", definition.realWorldApplication)
                    Text("Take it into real life", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = reflection, onValueChange = { reflection = it.take(500) },
                        label = { Text("Where could you use today's skill? (optional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                    QuietText("Private to this device. You can leave this blank.")
                }
                PracticeStage.Vocabulary, PracticeStage.Recall -> Text("This step is not part of this lesson.")
            }
        }
        Surface(color = Paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                PracticeAction(controller, requestRecording, onDone, reflection)
                if (session.stage == PracticeStage.Complete) TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Skip reflection") }
            }
        }
        }
    }
}

@Composable
internal fun PracticeAction(controller: PracticeController, requestRecording: () -> Unit, onDone: () -> Unit, reflection: String = "") {
    val state = LocalSessionState.current
    val s = state.progress
    val slot = s.stage.recordingSlot()
    if (state.hasPendingRecording) {
        PrimaryAction("Retry save", controller::retryPendingSave)
        return
    }
    if (state.isRecording) {
        PrimaryAction("Stop and save attempt", { controller.stopRecording() }, symbol = Symbol.Stop)
        return
    }
    if (slot != null && slot !in s.recordings) {
        PrimaryAction("Start recording", { if (controller.session.stage == s.stage) requestRecording() }, symbol = Symbol.Mic)
        return
    }
    val (label, enabled) = when (s.stage) {
        PracticeStage.Welcome -> "Try it myself" to true
        PracticeStage.Baseline -> "Keep this baseline" to true
        PracticeStage.FirstAttempt -> "Listen and check" to true
        PracticeStage.FirstCheck -> "See one useful example" to (s.firstAssessment != null)
        PracticeStage.Lesson -> "Try again" to true
        PracticeStage.Retry -> "Compare my attempts" to true
        PracticeStage.Compare -> "Try a fresh question" to true
        PracticeStage.Apply -> "Finish my practice" to true
        PracticeStage.Summary -> "Complete Day ${s.day}" to true
        PracticeStage.Complete -> "Finish session" to true
        PracticeStage.CheckpointRepeat -> "Answer a fresh question" to true
        PracticeStage.CheckpointFresh -> "Listen and compare" to true
        PracticeStage.CheckpointCompare -> "Finish checkpoint" to (s.comparison != null && s.retryAssessment != null &&
            listOf(RecordingSlot.CheckpointRepeat, RecordingSlot.CheckpointFresh).all { s.recordings[it]?.id in s.listenedRecordingIds })
        PracticeStage.Vocabulary, PracticeStage.Recall -> "Continue" to false
    }
    PrimaryAction(label, { if (s.stage == PracticeStage.Complete) { if (controller.saveWin(reflection)) onDone() } else controller.advance(s.stage) }, enabled)
}

@Composable
internal fun RecordingControls(controller: PracticeController, requestRecording: () -> Unit, slot: RecordingSlot) {
    val state = LocalSessionState.current
    val ref = state.progress.recordings[slot]
    var preparation by remember { mutableStateOf(controller.definition.preparationSeconds) }
    var preparing by remember { mutableStateOf(ref == null) }
    LaunchedEffect(preparing) {
        while (preparing && preparation > 0) { delay(1_000); preparation-- }
        preparing = false
    }
    if (ref == null || state.isRecording) {
        val remaining = ((controller.limitMillis - state.elapsedMillis + 999) / 1000).coerceAtLeast(0)
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (state.isRecording) (state.elapsedMillis.toFloat() / controller.limitMillis).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxSize(), color = Clay, trackColor = Line, strokeWidth = 3.dp)
                Box(Modifier.size(88.dp).background(if (state.isRecording) Forest else Sage, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(Symbol.Mic, if (state.isRecording) Color.White else Forest, Modifier.size(32.dp))
                }
            }
            Text(if (state.isRecording) "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}" else "Ready when you are", style = MaterialTheme.typography.titleLarge)
            QuietText(if (state.isRecording) "Recording · seconds remaining" else if (preparing) "Take ${preparation}s to gather your thoughts." else "Aim for ${minOf(controller.definition.suggestedSeconds.first, (controller.limitMillis / 1000).toInt())}–${controller.limitMillis / 1000} seconds. Use your own words.")
        }
    } else {
        if (controller.definition.isCheckpoint) QuietText("Attempt saved. Replay and compare after both checkpoint answers.")
        else Playback(controller, ref, "Saved attempt")
        TextButton(onClick = { preparing = false; requestRecording() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Record again") }
        QuietText("Your previous attempt stays saved until you finish a new one.")
    }
}

@Composable
internal fun Assessment(controller: PracticeController, selected: SelfAssessment?, retry: Boolean, question: String, clarification: String) {
    val state = LocalSessionState.current
    val progress = state.progress
    val slot = if (retry && controller.definition.isCheckpoint) RecordingSlot.CheckpointFresh else if (retry) RecordingSlot.Retry else RecordingSlot.First
    val recording = progress.recordings[slot]
    val label = if (slot == RecordingSlot.CheckpointFresh) "Fresh question · Day ${progress.day}" else if (retry) "Retry" else "First attempt"
    val openingOnly = !retry && controller.definition.openingOnly
    Text(question, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    recording?.let { Playback(controller, it, label, openingOnly) }
    val listened = recording?.id in progress.listenedRecordingIds
    if (!listened) QuietText("Replay the recording, then choose your self-check.")
    else Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelfAssessment.entries.forEach { value ->
            Choice(assessmentLabel(value), selected == value) {
                controller.assess(value, retry, expectedLessonId = progress.lessonId, expectedRecordingId = recording?.id)
            }
        }
    }
    if (selected == SelfAssessment.Unsure) {
        Surface(color = Sage, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What to listen for", style = MaterialTheme.typography.labelLarge, color = Forest)
                Text(clarification, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium, color = Ink)
                recording?.let { ref ->
                    val playing = state.playingId == ref.id
                    TextButton(
                        onClick = { controller.play(ref, openingOnly) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = if (playing) "Stop $label replay with guidance" else "Replay $label with guidance"
                        },
                    ) { Text(if (playing) "Stop replay" else if (openingOnly) "Replay opening" else "Replay answer") }
                }
            }
        }
    }
}

@Composable
internal fun VocabularyAssessment(controller: PracticeController, selected: SelfAssessment?, question: String) {
    Text(question, style = MaterialTheme.typography.titleMedium)
    SelfAssessment.entries.forEach { value -> Choice(assessmentLabel(value), selected == value) { controller.assessVocabulary(value) } }
}

