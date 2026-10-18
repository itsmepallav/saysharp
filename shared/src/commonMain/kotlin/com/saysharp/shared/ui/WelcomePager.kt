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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.engagement.*
import com.saysharp.shared.telemetry.Telemetry
import kotlinx.coroutines.launch

@Composable
internal fun WelcomePager(
    controller: EngagementController,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    replay: Boolean = false,
    onClose: () -> Unit = {},
    telemetry: Telemetry = Telemetry.None,
) {
    // Pager state survives activity recreation. An incomplete cold launch starts on page one.
    val pager = rememberPagerState(pageCount = { OnboardingPages.size })
    val scope = rememberCoroutineScope()
    var completing by remember { mutableStateOf(false) }
    val page = pager.settledPage
    fun moveTo(target: Int) {
        if (!pager.isScrollInProgress && !completing) scope.launch { pager.animateScrollToPage(target) }
    }
    LaunchedEffect(Unit) { if (!replay) telemetry.track("tutorial_begin") }
    LaunchedEffect(pager, replay) {
        snapshotFlow { pager.settledPage }.collect { index ->
            telemetry.screen("welcome_${index + 1}")
            telemetry.track("welcome_slide_viewed", "page" to index + 1, "is_replay" to replay)
        }
    }
    backHandler(page > 0 || replay) {
        if (page > 0) moveTo(page - 1) else onClose()
    }
    Column(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BrandMark()
                Text("saysharp", style = MaterialTheme.typography.titleMedium, color = Forest)
            }
            if (page > 0 || replay) TextButton(
                onClick = { if (page > 0) moveTo(page - 1) else onClose() },
                enabled = !pager.isScrollInProgress && !completing,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (page > 0) "Back" else "Close") }
        }
        HorizontalPager(
            pager,
            Modifier.weight(1f).semantics { contentDescription = "Welcome slides" },
            verticalAlignment = Alignment.Top,
        ) { index ->
            val content = OnboardingPages[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(listOf(Symbol.Arrow, Symbol.Shield, Symbol.Mic)[index], Forest, Modifier.size(30.dp))
                }
                Text(content.headline, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, color = Ink)
                Text(content.supportingText, style = MaterialTheme.typography.bodyLarge, color = Ink)
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "Welcome page ${page + 1} of 3" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(OnboardingPages.size) { index ->
                    Box(Modifier.padding(horizontal = 4.dp).size(8.dp).background(if (index == page) Forest else Line, CircleShape))
                }
            }
            controller.message?.let {
                Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            PrimaryAction(
                if (page == OnboardingPages.lastIndex) StartPracticing else "Next",
                onClick = {
                    if (page == OnboardingPages.lastIndex) {
                        if (!completing) {
                            completing = true
                            if (controller.finishWelcome()) onClose() else completing = false
                        }
                    } else moveTo(page + 1)
                },
                enabled = !pager.isScrollInProgress && !completing,
            )
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
