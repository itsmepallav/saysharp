package com.saysharp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saysharp.shared.reminder.ReminderController
import com.saysharp.shared.engagement.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderScreen(controller: ReminderController, onRequestReminder: () -> Unit, onOpenSettings: () -> Unit, engagement: EngagementController?, onReplayWelcome: () -> Unit, onFeedback: () -> Unit) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showGoals by remember { mutableStateOf(false) }
    if (showGoals && engagement != null) AlertDialog(
        onDismissRequest = { showGoals = false },
        title = { Text("Where do you want to feel clearer?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            SpeakingGoal.entries.forEach { goal ->
                TextButton(onClick = { if (engagement.changeGoal(goal)) showGoals = false }) { Text(goal.label) }
            }
        } },
        confirmButton = { TextButton(onClick = { showGoals = false }) { Text("Close") } },
    )
    if (showTimePicker) {
        val time = rememberTimePickerState(controller.settings.hour, controller.settings.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Daily reminder time") },
            text = { TimeInput(time) },
            confirmButton = { TextButton(onClick = {
                if (controller.setTime(time.hour, time.minute)) showTimePicker = false
            }) { Text("Save time") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
    Page {
        Eyebrow("Make it a habit")
        Text("Make clarity\na daily win.", style = MaterialTheme.typography.headlineLarge)
        QuietText("A reminder to make yourself heard, at a time that works for you.")
        if (engagement != null) Panel {
            Eyebrow("Your clarity goal", Forest)
            Text(engagement.state.goal.label, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showGoals = true }) { Text("Change my goal") }
            TextButton(onClick = onReplayWelcome) { Text("See welcome again") }
        }
        Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                QuietText(if (controller.settings.enabled) "Every day" else "Off for now")
            }
            Switch(modifier = Modifier.semantics { contentDescription = "Daily reminder" },
                checked = controller.settings.enabled, onCheckedChange = { enabled ->
                if (enabled) onRequestReminder() else controller.setEnabled(false)
            })
        }
        HorizontalDivider(color = Line)
        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Reminder time · ${controller.settings.timeLabel}")
        }
        QuietText(when {
            controller.settings.enabled && controller.notificationsAllowed -> "On · every day around ${controller.settings.timeLabel} in your local timezone."
            controller.settings.enabled -> "Paused · notifications are blocked by Android."
            else -> "Off · no daily reminder is scheduled."
        })
        }
        if (!controller.notificationsAllowed) {
            QuietText("To receive reminders, allow SaySharp notifications in Android settings.")
        }
        OutlinedButton(onClick = controller::preview, enabled = controller.notificationsAllowed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Send test notification") }
        TextButton(onClick = onOpenSettings) { Text("Android notification settings") }
        controller.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        QuietText("Times follow your local timezone. Android may deliver a little later to save battery. Reminders continue until you turn them off.")
        Panel {
            Text("Make SaySharp better", style = MaterialTheme.typography.titleMedium)
            QuietText("Share feedback, request a feature or report a problem.")
            OutlinedButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Feedback & ideas") }
        }
        Section("Private by design", "Recordings and notes stay on this device. Usage, feedback choices and crash diagnostics go to Firebase. No account or recording backup. Uninstalling removes your practice.")
    }
}

