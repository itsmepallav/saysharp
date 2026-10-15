package com.saysharp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.engagement.*
import kotlinx.coroutines.launch
import com.saysharp.shared.telemetry.Telemetry

@Composable
internal fun WelcomePager(controller: EngagementController, backHandler: @Composable (Boolean, () -> Unit) -> Unit, replay: Boolean = false, onClose: () -> Unit = {}, telemetry: Telemetry = Telemetry.None) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf(controller.state.goal) }
    LaunchedEffect(Unit) { if (!replay) telemetry.track("tutorial_begin") }
    LaunchedEffect(pager, replay) {
        snapshotFlow { pager.settledPage }.collect { page ->
            telemetry.screen("welcome_${page + 1}")
            telemetry.track("welcome_slide_viewed", "page" to page + 1, "is_replay" to replay)
        }
    }
    backHandler(pager.currentPage > 0 || replay) {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onClose()
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("saysharp", style = MaterialTheme.typography.titleMedium, color = Forest)
            TextButton(onClick = { if (controller.finishWelcome(goal, skipped = true)) onClose() }) { Text("Skip intro") }
        }
        HorizontalPager(pager, Modifier.weight(1f).semantics { contentDescription = "Welcome slides" }, verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page < 2) Box(Modifier.size(48.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(listOf(Symbol.Arrow, Symbol.Mic)[page], Forest, Modifier.size(24.dp))
                }
                Eyebrow(listOf("Find your point", "Think Sharp. Speak Bold.", "Short, focused practice")[page], Clay)
                Text(listOf("Less rambling.\nMore impact.", "Hold your ground.\nKeep your cool.", "Record. Listen.\nTry again.")[page], style = MaterialTheme.typography.headlineLarge)
                if (page < 2) QuietText(listOf(
                    "Know what you mean, but lose your point halfway through? Practise giving the answer before the explanation.",
                    "Practise clear reasons, respectful disagreement and firm boundaries for everyday conversations.",
                )[page])
                when (page) {
                    0 -> Panel {
                        Eyebrow("An answer that lands", Forest)
                        QuietText("“There are lots of things we could do, and I was thinking…”")
                        HorizontalDivider(color = Line)
                        Text("“I recommend starting with one small trial. Here's why.”", style = MaterialTheme.typography.titleLarge)
                    }
                    1 -> Panel {
                        Eyebrow("Bring it into real conversations", Forest)
                        listOf("Make your ideas easier to follow", "Back your opinion with a clear reason", "Sound firm without sounding hostile").forEach { text ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Glyph(Symbol.Check, Forest, Modifier.size(20.dp)); Text(text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    else -> {
                        QuietText("One skill. A short answer. Listen for one thing, retry, then answer a fresh question.")
                        Text("Where would clarity help you most?", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SpeakingGoal.entries.forEach { value ->
                            FilterChip(selected = goal == value, onClick = { goal = value },
                                label = { Text(value.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage))
                        } }
                        QuietText("You judge your own attempts. Work through 14 sessions at your own pace.")
                        QuietText("All 14 sessions are included, free. Your recordings stay private on this device.")
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Welcome page ${pager.currentPage + 1} of 3" }, horizontalArrangement = Arrangement.Center) {
                repeat(3) { index -> Box(Modifier.padding(horizontal = 4.dp).size(if (index == pager.currentPage) 10.dp else 8.dp).background(if (index == pager.currentPage) Forest else Line, CircleShape)) }
            }
            controller.message?.let { QuietText(it) }
            PrimaryAction(if (pager.currentPage == 2) "Start practice" else "Next", {
                if (pager.currentPage == 2) { if (controller.finishWelcome(goal)) onClose() }
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
    }
}

@Composable
internal fun MilestoneDialog(milestone: Milestone, onDismiss: () -> Unit, onReview: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Glyph(Symbol.Check, Forest, Modifier.size(30.dp)) } },
        title = { Text(milestone.title, style = MaterialTheme.typography.headlineMedium) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("Milestone · ${milestone.sessions} / 14", Forest)
            Text(milestone.detail)
            Text(milestone.next, style = MaterialTheme.typography.bodyMedium, color = Muted)
        } },
        confirmButton = { TextButton(onClick = onReview) { Text("Hear my progress") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}

@Composable
internal fun MilestoneCollection(completedDays: Set<Int>) {
    Panel {
        Eyebrow("Wins you've earned", Forest)
        Milestones.forEach { milestone ->
            val earned = (1..milestone.sessions).all { it in completedDays }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).background(if (earned) Sage else Paper, CircleShape), contentAlignment = Alignment.Center) {
                    if (earned) Glyph(Symbol.Check, Forest, Modifier.size(16.dp))
                    else Text(milestone.sessions.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(when(milestone.sessions) { 1 -> "Your starting point"; 3 -> "Three sessions completed"; 7 -> "First checkpoint"; else -> "Programme complete" }, style = MaterialTheme.typography.bodyMedium)
                    Text(if (earned) "Earned" else "At session ${milestone.sessions}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
