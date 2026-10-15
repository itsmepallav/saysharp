package com.saysharp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.feedback.*
import com.saysharp.shared.telemetry.Telemetry

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackScreen(telemetry: Telemetry, day: Int, completedCount: Int, onBack: () -> Unit) {
    var kindName by rememberSaveable { mutableStateOf(FeedbackKind.General.name) }
    var selectedOption by rememberSaveable { mutableStateOf<String?>(null) }
    var rating by rememberSaveable { mutableStateOf(0) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val kind = FeedbackKind.valueOf(kindName)
    val sender = remember(telemetry) { FeedbackSender(telemetry) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (submitted) {
                Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(Symbol.Check, Forest, Modifier.size(30.dp))
                }
                Eyebrow("Your voice matters", Forest)
                Text("Thanks for\nspeaking up.", style = MaterialTheme.typography.headlineLarge)
                QuietText("Your response is queued to send. It helps shape what comes next.")
                Panel {
                    Eyebrow(kind.label, Clay)
                    Text(kind.options.first { it.id == selectedOption }.label, style = MaterialTheme.typography.titleMedium)
                    if (kind == FeedbackKind.General && rating > 0) QuietText("Your rating · $rating / 5")
                }
            } else {
                Eyebrow("Feedback & ideas", Clay)
                Text("Help shape\nSaySharp.", style = MaterialTheme.typography.headlineLarge)
                QuietText("Choose what matters most. No personal details needed.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeedbackKind.entries.forEach { value ->
                        FilterChip(selected = kind == value, onClick = {
                            if (kind != value) {
                                kindName = value.name; selectedOption = null; rating = 0; failed = false
                            }
                        }, label = { Text(value.label) }, modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage))
                    }
                }
                Panel {
                    Text(kind.question, style = MaterialTheme.typography.titleMedium)
                    kind.options.forEach { option ->
                        Surface(
                            color = if (selectedOption == option.id) Sage else Paper,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(
                                selected = selectedOption == option.id,
                                role = Role.RadioButton,
                                onClick = { selectedOption = option.id; failed = false },
                            ).semantics {
                                contentDescription = option.label
                            }) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedOption == option.id, onClick = null)
                                Spacer(Modifier.width(10.dp))
                                Text(option.label, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (kind == FeedbackKind.General) Panel {
                    Text("Overall experience · optional", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { score ->
                            FilterChip(selected = rating == score, onClick = { rating = if (rating == score) 0 else score },
                                label = { Text(score.toString()) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Rate $score out of 5" },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage))
                        }
                    }
                    QuietText("1 · Needs work     5 · Excellent")
                }
                if (failed) Text("Your response couldn't be queued. Please try again.", color = MaterialTheme.colorScheme.error)
                QuietText("Your choices help prioritise improvements. Recordings and personal notes stay on your device.")
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryAction(if (submitted) "Back to settings" else "Send response", onClick = {
                if (submitted) onBack()
                else selectedOption?.let { option ->
                    submitted = sender.submit(kind, option, rating, day, completedCount)
                    failed = !submitted
                }
            }, enabled = submitted || selectedOption != null, symbol = if (submitted) Symbol.Check else Symbol.Arrow)
        }
    }
}
