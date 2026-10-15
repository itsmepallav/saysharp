package com.saysharp.shared.reminder

import kotlin.test.*

class ReminderControllerTest {
    @Test fun deniedPermissionDoesNotEnableOrSend() {
        val platform = FakeReminders().apply { allowed = false }
        val controller = ReminderController(platform)
        assertFalse(controller.setEnabled(true))
        controller.preview()
        assertFalse(platform.saved.enabled)
        assertEquals(0, platform.previews)
        assertNotNull(controller.message)
    }

    @Test fun changingTimeReplacesScheduleAndDisableCancelsIt() {
        val platform = FakeReminders()
        val controller = ReminderController(platform)
        assertTrue(controller.setEnabled(true))
        assertTrue(controller.setTime(8, 35))
        assertEquals(ReminderSettings(true, 8, 35), platform.scheduled)
        assertEquals("08:35", controller.settings.timeLabel)
        assertTrue(controller.setEnabled(false))
        assertNull(platform.scheduled)
        assertEquals(ReminderSettings(false, 8, 35), platform.saved)
    }

    @Test fun restartRestoresPreferenceAndPermissionRevocationPausesScheduling() {
        val platform = FakeReminders().apply { saved = ReminderSettings(true, 9, 15) }
        val controller = ReminderController(platform)
        assertEquals(platform.saved, controller.settings)
        assertNotNull(platform.scheduled)
        platform.allowed = false
        controller.refresh()
        assertNull(platform.scheduled)
        assertTrue(controller.settings.enabled)
        platform.allowed = true
        controller.refresh()
        assertEquals(platform.saved, platform.scheduled)
    }

    @Test fun saveFailurePreservesPreviousPreferenceAndSchedule() {
        val platform = FakeReminders().apply { saved = ReminderSettings(true, 9, 15) }
        val controller = ReminderController(platform)
        platform.failSave = true
        assertFalse(controller.setTime(10, 30))
        assertEquals(ReminderSettings(true, 9, 15), controller.settings)
        assertEquals(platform.saved, platform.scheduled)
    }

    @Test fun previewDoesNotEnableDailyReminders() {
        val platform = FakeReminders()
        val controller = ReminderController(platform)
        controller.preview()
        assertEquals(1, platform.previews)
        assertFalse(controller.settings.enabled)
        assertNull(platform.scheduled)
    }

    @Test fun unreadableSettingsAreNotOverwritten() {
        val platform = FakeReminders().apply { failLoad = true }
        val controller = ReminderController(platform)
        assertFalse(controller.setEnabled(true))
        assertFalse(controller.setTime(10, 0))
        assertEquals(0, platform.saves)
        assertEquals(0, platform.schedules)
    }
}

private class FakeReminders : ReminderPlatform {
    var saved = ReminderSettings()
    var scheduled: ReminderSettings? = null
    var allowed = true
    var failSave = false
    var failLoad = false
    var saves = 0
    var schedules = 0
    var previews = 0
    override fun load(): ReminderSettings { check(!failLoad); return saved }
    override fun save(settings: ReminderSettings) { check(!failSave); saves++; saved = settings }
    override fun notificationsAllowed() = allowed
    override fun schedule(settings: ReminderSettings) { schedules++; scheduled = settings.takeIf { it.enabled && allowed } }
    override fun sendPreview() { previews++ }
}
