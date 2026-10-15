package com.saysharp.saysharp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saysharp.saysharp.reminder.AndroidReminders
import com.saysharp.saysharp.reminder.PracticeReminderReceiver
import com.saysharp.shared.reminder.ReminderSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSchedulingTest {
    @Test fun nativeSettingsPersistAndDisablingRemovesPendingAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "reminder_test"
        val id = 90410
        val adapter = AndroidReminders(context, name, id)
        try {
            assertEquals(ReminderSettings(), adapter.load())
            val settings = ReminderSettings(true, 22, 45)
            adapter.save(settings)
            assertEquals(settings, AndroidReminders(context, name, id).load())
            adapter.schedule(settings)
            val alarm = PendingIntent.getBroadcast(context, id,
                Intent(context, PracticeReminderReceiver::class.java).setAction(AndroidReminders.ACTION_REMIND),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (adapter.notificationsAllowed()) assertNotNull(alarm) else assertNull(alarm)
            adapter.save(settings.copy(enabled = false))
            adapter.schedule(adapter.load())
            assertNull(PendingIntent.getBroadcast(context, id,
                Intent(context, PracticeReminderReceiver::class.java).setAction(AndroidReminders.ACTION_REMIND),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally {
            adapter.schedule(ReminderSettings())
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
