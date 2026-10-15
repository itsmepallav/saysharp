package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saysharp.shared.lesson.Programme
import com.saysharp.shared.practice.*
import com.saysharp.shared.engagement.SpeakingGoal

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayScreen(controller: PracticeController, onOpen: () -> Unit, goal: SpeakingGoal? = null) = Page {
    val day = controller.nextDay
    val outline = Programme.outline[day - 1]
    Eyebrow("Think Sharp. Speak Bold.")
    Text(if (controller.session.completedDays.size == 14) "Your programme,\nready to revisit." else "One clearer\nanswer today.", style = MaterialTheme.typography.headlineLarge)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("Your journey")
            Text("${controller.session.completedDays.size} / 14", style = MaterialTheme.typography.labelLarge, color = Muted)
        }
        ProgressTrack(controller.session.completedDays.size)
    }
    Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Sage, shape = CircleShape) { Text("DAY ${day.toString().padStart(2, '0')}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Forest) }
                Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    Eyebrow(if (Programme.checkpoint(day) != null) "Hear your change" else "Your next win", Clay)
                }
            }
            Text(outline.title, style = MaterialTheme.typography.headlineMedium)
            QuietText(Programme.checkpoint(day)?.let { "Answer a familiar question and a fresh one. Listen for what changed." } ?: Programme.lesson(day).instruction)
            PrimaryAction(when (controller.session.stage) {
                    PracticeStage.Welcome -> "Start practice"
                    PracticeStage.Complete -> if (controller.session.day < 14) "Start Day $day" else "Review your programme"
                    else -> "Continue practice"
                }, onOpen)
    }
    val latest = controller.session.completedDays.maxByOrNull { it.key }?.value
    if (latest?.comparison == Comparison.Clearer) Panel(color = Sage) {
        Eyebrow("A difference you noticed", Forest)
        Text("You marked your latest answer clearer.", style = MaterialTheme.typography.titleMedium)
        QuietText("Try that same clarity in your next real conversation.")
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Symbol.Mic to "Speak", Symbol.Play to "Listen", Symbol.Progress to "Try again").forEach { (icon, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(icon, Muted, Modifier.size(18.dp)); Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
    goal?.let { QuietText("Your goal · ${it.label}") }
}

@Composable
internal fun ProgrammeScreen(controller: PracticeController, onOpen: () -> Unit, onReview: (Int) -> Unit) = Page {
    Eyebrow("14 small steps")
    Text("Say it clearly.\nMake it land.", style = MaterialTheme.typography.headlineLarge)
    QuietText(Programme.goal)
    ProgressTrack(controller.session.completedDays.size)
    Programme.outline.forEach { lesson ->
        if (lesson.day == 1 || lesson.day == 8) {
            Spacer(Modifier.height(4.dp))
            Eyebrow(if (lesson.day == 1) "01—07  ·  Speak Clearly" else "08—14  ·  Speak With Conviction")
        }
        val complete = lesson.day in controller.session.completedDays
        val current = !complete && lesson.day == controller.nextDay
        Surface(color = if (current) Color.White else Color.Transparent, shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(if (current) 18.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(38.dp).background(if (complete || current) Sage else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    if (complete) Glyph(Symbol.Check, Forest, Modifier.size(19.dp))
                    else Text(lesson.day.toString().padStart(2, '0'), style = MaterialTheme.typography.labelLarge, color = if (current) Forest else Muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                QuietText(lesson.outcome)
                when {
                    complete -> TextButton(onClick = { onReview(lesson.day) }, contentPadding = PaddingValues(0.dp)) { Text("Review Day ${lesson.day}") }
                    current -> TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) { Text("Open Day ${lesson.day}"); Spacer(Modifier.width(8.dp)); Glyph(Symbol.Arrow, Forest, Modifier.size(16.dp)) }
                }
                }
            }
        }
    }
}

