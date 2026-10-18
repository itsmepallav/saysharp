package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.lesson.SelfAssessment
import com.saysharp.shared.platform.RecordingRef
import com.saysharp.shared.practice.*

@Composable
internal fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
    )
}

@Composable
internal fun Playback(controller: PracticeController, recording: RecordingRef, label: String, openingOnly: Boolean = false) {
    val state = LocalSessionState.current
    val playing = state.playingId == recording.id
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (playing) Forest else Line)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FilledIconButton(onClick = { controller.play(recording, openingOnly) }, enabled = !state.isRecording,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "${if (playing) "Stop playback" else "Play recording"} · $label" },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Sage, contentColor = Forest)) {
                Glyph(if (playing) Symbol.Stop else Symbol.Play, Forest, Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                QuietText(if (playing && openingOnly) "Playing opening · ${(recording.durationMillis / 1000).coerceAtLeast(1)} seconds total" else if (playing) "Playing · ${(recording.durationMillis / 1000).coerceAtLeast(1)} seconds" else if (openingOnly) "Listen to first 10 seconds" else "${(recording.durationMillis / 1000).coerceAtLeast(1)} seconds")
            }
        }
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(selected = selected, onClick = onClick, color = if (selected) Sage else Color.White, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Forest else Line), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.RadioButton }) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (selected) Glyph(Symbol.Check, Forest, Modifier.size(20.dp))
            else Box(Modifier.size(20.dp).background(Paper, CircleShape))
        }
    }
}

@Composable
internal fun Section(title: String, body: String) {
    Panel(color = if (title == "A clearer answer" || title == "Change one thing" || title == "Your task today" || title == "Use it today") Sage else Color.White) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = Forest)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun assessmentLabel(value: SelfAssessment) = when (value) {
    SelfAssessment.Done -> "Done"
    SelfAssessment.NotYet -> "Not yet"
    SelfAssessment.Unsure -> "Unsure"
}

internal fun comparisonLabel(value: Comparison) = when (value) {
    Comparison.Clearer -> "Clearer"
    Comparison.AboutTheSame -> "About the same"
    Comparison.LessClear -> "Less clear"
    Comparison.Unsure -> "Unsure"
}

