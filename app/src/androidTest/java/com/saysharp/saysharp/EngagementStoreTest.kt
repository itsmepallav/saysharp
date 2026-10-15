package com.saysharp.saysharp

import androidx.test.platform.app.InstrumentationRegistry
import com.saysharp.saysharp.storage.AndroidEngagementStore
import com.saysharp.shared.engagement.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

class EngagementStoreTest {
    @Test fun welcomeGoalAndAcknowledgementsRoundTrip() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engagement-test-${UUID.randomUUID()}")
        try {
            val store = AndroidEngagementStore(directory)
            assertEquals(EngagementState(), store.load())
            val expected = EngagementState(true, SpeakingGoal.Meetings, setOf(1, 3, 7))
            store.save(expected)
            assertEquals(expected, AndroidEngagementStore(directory).load())
        } finally { directory.deleteRecursively() }
    }
}
