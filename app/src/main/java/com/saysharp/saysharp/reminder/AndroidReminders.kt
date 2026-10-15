package com.saysharp.saysharp.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saysharp.saysharp.MainActivity
import com.saysharp.saysharp.R
import com.saysharp.shared.reminder.ReminderPlatform
import com.saysharp.shared.reminder.ReminderSettings
import com.saysharp.shared.telemetry.Telemetry
import com.saysharp.saysharp.telemetry
import java.util.Calendar
import java.util.TimeZone

class AndroidReminders(
    context: Context,
    preferencesName: String = "practice_reminders",
    private val alarmId: Int = 410,
    private val diagnostics: Telemetry = Telemetry.None,
) : ReminderPlatform {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Practice reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your optional daily speaking practice reminder"
            }
        )
    }

    override fun load() = ReminderSettings(
        preferences.getBoolean("enabled", false),
        preferences.getInt("hour", 19), preferences.getInt("minute", 0),
    )

    override fun save(settings: ReminderSettings) {
        check(preferences.edit().putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.hour).putInt("minute", settings.minute).commit())
    }

    override fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun alarmIntent(flags: Int) = PendingIntent.getBroadcast(context, alarmId,
        Intent(context, PracticeReminderReceiver::class.java).setAction(ACTION_REMIND), flags or PendingIntent.FLAG_IMMUTABLE)

    override fun schedule(settings: ReminderSettings) {
        // One identity replaces the previous time. No exact-alarm permission is needed.
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (!settings.enabled || !notificationsAllowed()) {
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            nextReminderMillis(System.currentTimeMillis(), settings.hour, settings.minute),
            requireNotNull(alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT)))
    }

    override fun sendPreview() = postNotification(preview = true)

    fun deliver() {
        val settings = load()
        if (!settings.enabled) { schedule(settings); return }
        try {
            if (notificationsAllowed()) postNotification(preview = false)
        } finally {
            // Recompute local wall-clock time every day, including daylight-saving changes.
            schedule(settings)
        }
    }

    private fun postNotification(preview: Boolean) {
        if (!notificationsAllowed()) return
        val source = if (preview) "preview" else "daily"
        val openApp = PendingIntent.getActivity(context, if (preview) 413 else 411,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SOURCE, source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (preview) "Your SaySharp reminder is ready" else "A clearer answer starts with practice")
            .setContentText(if (preview) "Tap to return to SaySharp." else "Try one speaking drill. Listen, check one thing, and try again.")
            .setContentIntent(openApp).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        // Permission may be revoked between the check and delivery.
        try {
            notifications.notify(if (preview) PREVIEW_ID else NOTIFICATION_ID, notification)
            diagnostics.track("reminder_posted", "source" to source)
        } catch (error: SecurityException) {
            diagnostics.error("reminder_notification", error)
        }
    }

    companion object {
        const val CHANNEL = "practice_reminders"
        const val EXTRA_SOURCE = "reminder_source"
        const val ACTION_REMIND = "com.saysharp.saysharp.PRACTICE_REMINDER"
        const val NOTIFICATION_ID = 410
        const val PREVIEW_ID = 412
    }
}

/** Next occurrence in the device's local timezone; equal/past times belong to tomorrow. */
internal fun nextReminderMillis(nowMillis: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
    require(hour in 0..23 && minute in 0..59)
    val next = Calendar.getInstance(zone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
            // Restore the requested hour after a nonexistent DST time was normalised.
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        }
    }
    return next.timeInMillis
}

class PracticeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminders.ACTION_REMIND) return
        try { AndroidReminders(context, diagnostics = context.telemetry).deliver() } catch (error: Exception) {
            context.telemetry.error("reminder_delivery", error)
            // Do not crash a background receiver; reopening the app retries scheduling.
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        try { AndroidReminders(context, diagnostics = context.telemetry).let { it.schedule(it.load()) } } catch (error: Exception) {
            context.telemetry.error("reminder_reschedule", error)
            // Reopening the app retries scheduling if local storage is temporarily unavailable.
        }
    }
}
