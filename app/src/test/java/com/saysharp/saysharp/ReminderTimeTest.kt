package com.saysharp.saysharp

import com.saysharp.saysharp.reminder.nextReminderMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderTimeTest {
    private fun millis(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun nextLocalOccurrenceHandlesPastEqualAndTimezoneChanges() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val now = millis(india, 2026, 10, 11, 19, 0)
        assertEquals(millis(india, 2026, 10, 12, 19, 0), nextReminderMillis(now, 19, 0, india))
        assertEquals(millis(india, 2026, 10, 11, 20, 30), nextReminderMillis(now, 20, 30, india))
        val london = TimeZone.getTimeZone("Europe/London")
        assertEquals(millis(london, 2026, 10, 11, 19, 0), nextReminderMillis(now, 19, 0, london))
    }

    @Test fun daylightSavingKeepsFollowingDaysAtChosenLocalHour() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val beforeSpring = millis(zone, 2026, 3, 7, 20, 0)
        val spring = nextReminderMillis(beforeSpring, 9, 0, zone)
        assertEquals(millis(zone, 2026, 3, 8, 9, 0), spring)
        val afterGap = millis(zone, 2026, 3, 8, 12, 0)
        assertEquals(millis(zone, 2026, 3, 9, 2, 30), nextReminderMillis(afterGap, 2, 30, zone))
        val beforeFall = millis(zone, 2026, 10, 31, 20, 0)
        assertEquals(millis(zone, 2026, 11, 1, 9, 0), nextReminderMillis(beforeFall, 9, 0, zone))
    }
}
