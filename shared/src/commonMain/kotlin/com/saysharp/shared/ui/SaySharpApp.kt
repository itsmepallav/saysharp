package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saysharp.shared.practice.*
import com.saysharp.shared.reminder.ReminderController
import com.saysharp.shared.engagement.*
import com.saysharp.shared.telemetry.Telemetry
import kotlinx.coroutines.delay

internal enum class AppScreen(val analyticsName: String) {
    Today("today"), Programme("programme"), Progress("progress"), Settings("settings"), Feedback("feedback")
}

@Composable
fun SaySharpApp(
    controller: PracticeController,
    onRequestRecording: () -> Unit,
    reminders: ReminderController? = null,
    onRequestReminder: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    backHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> },
    engagement: EngagementController? = null,
    telemetry: Telemetry = Telemetry.None,
) {
    var tab by rememberSaveable(stateSaver = Saver<AppScreen, String>(save = { it.name }, restore = { AppScreen.valueOf(it) })) { mutableStateOf(AppScreen.Today) }
    var inPractice by rememberSaveable { mutableStateOf(false) }
    var progressDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var replayWelcome by rememberSaveable { mutableStateOf(false) }
    val welcoming = engagement != null && (!engagement.state.welcomed || replayWelcome)
    backHandler(!welcoming && (inPractice || tab != AppScreen.Today)) {
        if (inPractice) telemetry.track("practice_left", "day" to controller.session.day, "stage" to controller.session.stage.name, "reason" to "back")
        controller.onBackground()
        if (inPractice) inPractice = false else tab = if (tab == AppScreen.Feedback) AppScreen.Settings else AppScreen.Today
    }
    LaunchedEffect(controller.isRecording) {
        while (controller.isRecording) { delay(100); controller.tick() }
    }
    val screenName = when {
        welcoming -> null // WelcomePager tracks the settled page, not prefetched pages.
        inPractice -> "practice_${controller.session.stage.name.lowercase()}"
        else -> tab.analyticsName
    }
    val screenDay = if (inPractice) controller.session.day else if (tab == AppScreen.Progress) progressDay ?: controller.session.day else 0
    val screenStage = if (inPractice) controller.session.stage.name else "none"
    LaunchedEffect(screenName, screenDay, screenStage) {
        screenName?.let { telemetry.screen(it, screenDay, screenStage) }
    }
    SaySharpTheme {
        if (welcoming) {
            WelcomePager(requireNotNull(engagement), backHandler, replayWelcome, onClose = { replayWelcome = false }, telemetry = telemetry)
            return@SaySharpTheme
        }
        val milestone = engagement?.pendingMilestone(controller.session.completedDays.keys)
        var hiddenMilestone by rememberSaveable { mutableStateOf<Int?>(null) }
        if (milestone != null && milestone.sessions != hiddenMilestone && !controller.isRecording) {
            val milestoneEngagement = requireNotNull(engagement)
            LaunchedEffect(milestone.sessions) { telemetry.track("milestone_viewed", "day" to milestone.sessions) }
            MilestoneDialog(milestone, onDismiss = {
                milestoneEngagement.acknowledge(milestone)
                hiddenMilestone = milestone.sessions // Failed writes retry next launch, without trapping the user.
            }, onReview = {
                milestoneEngagement.acknowledge(milestone)
                hiddenMilestone = milestone.sessions
                controller.stopPlayback(); inPractice = false; tab = AppScreen.Progress; progressDay = milestone.sessions
            })
        }
        Scaffold(bottomBar = {
            if (!inPractice) NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                val tabs = listOf(AppScreen.Today, AppScreen.Programme, AppScreen.Progress)
                tabs.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        selected = tab == screen,
                        onClick = { controller.stopPlayback(); tab = screen; progressDay = null },
                        icon = { Glyph(listOf(Symbol.Home, Symbol.Programme, Symbol.Progress)[index], if (tab == screen) Forest else Muted) },
                        label = { Text(screen.name) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Sage, selectedTextColor = Forest, unselectedTextColor = Muted),
                    )
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        BrandMark()
                        Text("saysharp", style = MaterialTheme.typography.titleMedium, letterSpacing = (-0.5).sp)
                    }
                    if (inPractice) TextButton(onClick = {
                        telemetry.track("practice_left", "day" to controller.session.day, "stage" to controller.session.stage.name, "reason" to "save_leave")
                        controller.onBackground(); inPractice = false
                    }) { Text("Save & leave", style = MaterialTheme.typography.bodyMedium) }
                    else if (tab == AppScreen.Feedback) TextButton(onClick = { tab = AppScreen.Settings }) { Text("Back") }
                    else if (reminders != null) IconButton(onClick = { controller.stopPlayback(); tab = if (tab == AppScreen.Settings) AppScreen.Today else AppScreen.Settings },
                        modifier = Modifier.semantics { contentDescription = if (tab == AppScreen.Settings) "Close settings" else "Settings" }) {
                        Glyph(if (tab == AppScreen.Settings) Symbol.Close else Symbol.Settings)
                    }
                }
                controller.message?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(message, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                engagement?.message?.let { QuietText(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                val openPractice = { if (controller.beginNextDay()) {
                    telemetry.track("practice_opened", "day" to controller.session.day, "stage" to controller.session.stage.name,
                        "has_saved_attempt" to controller.session.recordings.keys.any { it != RecordingSlot.Baseline || controller.session.day == 1 })
                    inPractice = true
                } }
                if (inPractice) PracticeFlow(controller, onRequestRecording, onDone = { inPractice = false; tab = AppScreen.Progress; progressDay = controller.session.day })
                else key(tab) { when (tab) {
                    AppScreen.Today -> TodayScreen(controller, openPractice, engagement?.state?.goal)
                    AppScreen.Programme -> ProgrammeScreen(controller, onOpen = { openPractice() }, onReview = { telemetry.track("chapter_reviewed", "day" to it); progressDay = it; tab = AppScreen.Progress })
                    AppScreen.Progress -> ProgressScreen(controller, progressDay, onSelectDay = { telemetry.track("chapter_reviewed", "day" to it); controller.stopPlayback(); progressDay = it }, onPractice = { openPractice() })
                    AppScreen.Settings -> reminders?.let { ReminderScreen(it, onRequestReminder, onOpenNotificationSettings, engagement, onReplayWelcome = { replayWelcome = true }, onFeedback = { tab = AppScreen.Feedback }) }
                    AppScreen.Feedback -> FeedbackScreen(telemetry, controller.session.day, controller.session.completedDays.size, onBack = { tab = AppScreen.Settings })
                } }
            }
        }
    }
}

