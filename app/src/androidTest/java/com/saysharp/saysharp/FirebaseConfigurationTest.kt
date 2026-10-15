package com.saysharp.saysharp

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Assert.*
import org.junit.Test

class FirebaseConfigurationTest {
    @Test fun suppliedFirebaseProjectInitializesAndAcceptsDiagnosticSmokeCheck() {
        assertTrue(BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.saysharp.saysharp", context.packageName)
        assertEquals("saysharp", FirebaseApp.getInstance().options.projectId)
        assertNotNull(FirebaseCrashlytics.getInstance())
        context.telemetry.track("integration_check", "source" to "instrumentation")
        // A deliberate, non-fatal diagnostic. Never crash the user's running app.
        context.telemetry.error("integration_check", IllegalStateException("Synthetic Firebase integration check"))
    }
}
