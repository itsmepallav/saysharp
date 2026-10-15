package com.saysharp.saysharp.telemetry

import org.junit.Assert.*
import org.junit.Test

class CrashSanitizationTest {
    @Test fun customReportsPreserveCallSitesWithoutMessagesOrCauses() {
        val original = IllegalStateException("Private note at /private/user/recording.m4a", Exception("Sensitive cause"))
        val report = sanitizedException("recording_save", original)
        assertEquals("recording_save: IllegalStateException", report.message)
        assertNull(report.cause)
        assertArrayEquals(original.stackTrace, report.stackTrace)
        assertFalse(report.toString().contains("Private note"))
        assertFalse(report.toString().contains("recording.m4a"))
    }
}
