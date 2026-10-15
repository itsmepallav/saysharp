package com.saysharp.shared.reminder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saysharp.shared.telemetry.Telemetry

data class ReminderSettings(val enabled: Boolean = false, val hour: Int = 19, val minute: Int = 0) {
    init { require(hour in 0..23 && minute in 0..59) }
    val timeLabel: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/** Platform owns local persistence, notification permissions and scheduling. */
interface ReminderPlatform {
    fun load(): ReminderSettings
    fun save(settings: ReminderSettings)
    fun notificationsAllowed(): Boolean
    fun schedule(settings: ReminderSettings)
    fun sendPreview()
}

class ReminderController(private val platform: ReminderPlatform, private val telemetry: Telemetry = Telemetry.None) {
    var message: String? by mutableStateOf(null)
        private set
    private var loadFailed = false
    var settings by mutableStateOf(try { platform.load() } catch (error: Exception) {
        telemetry.error("reminder_restore", error)
        loadFailed = true
        message = "Reminder settings could not be opened. Try reopening the app."
        ReminderSettings()
    })
        private set
    var notificationsAllowed by mutableStateOf(false)
        private set

    init { refresh() }

    fun refresh() {
        try {
            notificationsAllowed = platform.notificationsAllowed()
            if (!loadFailed) platform.schedule(settings)
        } catch (error: Exception) { telemetry.error("reminder_schedule", error); message = "The reminder could not be scheduled. Try again." }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        refresh()
        if (enabled && !notificationsAllowed) {
            telemetry.track("reminder_blocked", "reason" to "notifications_disabled")
            message = "Allow notifications in Android settings to receive practice reminders."
            return false
        }
        return update(settings.copy(enabled = enabled))
    }

    fun setTime(hour: Int, minute: Int): Boolean = update(settings.copy(hour = hour, minute = minute))

    private fun update(next: ReminderSettings): Boolean {
        if (loadFailed) return false
        val previous = settings
        return try {
            platform.save(next)
            settings = next
            platform.schedule(next)
            if (previous.enabled != next.enabled) telemetry.track("reminder_toggled", "enabled" to next.enabled)
            if (previous.hour != next.hour || previous.minute != next.minute) telemetry.track("reminder_time_changed", "enabled" to next.enabled)
            message = when {
                !next.enabled -> "Daily reminders are off."
                !notificationsAllowed -> "Time saved. Reminders are paused because notifications are blocked."
                else -> "Daily reminder saved for around ${next.timeLabel}."
            }
            true
        } catch (error: Exception) {
            telemetry.error("reminder_update", error)
            message = "The reminder could not be saved or scheduled. Try again."
            false
        }
    }

    fun preview() {
        refresh()
        if (!notificationsAllowed) {
            message = "Allow notifications in Android settings to try a notification."
            return
        }
        try {
            platform.sendPreview()
            message = "Test notification sent. Check your notifications."
        } catch (error: Exception) { telemetry.error("reminder_preview", error); message = "The test notification could not be sent. Try again." }
    }
}
