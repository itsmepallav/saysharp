package com.saysharp.shared.feedback

import com.saysharp.shared.telemetry.*
import kotlin.test.*

class FeedbackSenderTest {
    private val sink = FeedbackBackend()
    private val sender = FeedbackSender(Telemetry(sink))

    @Test fun sendsStructuredResponseWithOptionalRatingAndProgrammeContext() {
        assertTrue(sender.submit(FeedbackKind.General, "useful_practice", 4, 3, 2))
        assertEquals("feedback_submitted", sink.events.single().first)
        assertEquals(mapOf("feedback_kind" to "general", "feedback_option" to "useful_practice",
            "rating" to 4, "day" to 3, "completed_count" to 2), sink.events.single().second)
    }

    @Test fun absentRatingsAndFeatureVotesDoNotPolluteRatingMetrics() {
        assertTrue(sender.submit(FeedbackKind.General, "needs_variety", 0, 1, 0))
        assertTrue(sender.submit(FeedbackKind.Feature, "audio_examples", 5, 1, 0))
        assertTrue(sink.events.all { "rating" !in it.second })
    }

    @Test fun invalidOrCrossCategoryChoicesNeverEmitFeedback() {
        assertFalse(sender.submit(FeedbackKind.Problem, "audio_examples", 0, 1, 0))
        assertFalse(sender.submit(FeedbackKind.General, "A private message", 0, 1, 0))
        assertFalse(sender.submit(FeedbackKind.General, "useful_practice", 6, 1, 0))
        assertFalse(sender.submit(FeedbackKind.General, "useful_practice", 1, 0, 0))
        assertFalse(sender.submit(FeedbackKind.General, "useful_practice", 1, 1, 15))
        assertTrue(sink.events.isEmpty())
    }

    @Test fun unavailableTelemetryAndFailedEnqueueCannotShowFalseSuccess() {
        assertFalse(FeedbackSender(Telemetry.None).submit(FeedbackKind.Problem, "recording", 0, 1, 0))
        sink.fail = true
        assertFalse(sender.submit(FeedbackKind.Problem, "recording", 0, 1, 0))
        assertTrue(sink.events.isEmpty())
    }
}

private class FeedbackBackend : TelemetryBackend {
    var fail = false
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun event(name: String, parameters: Map<String, Any>) { check(!fail); events += name to parameters }
    override fun context(parameters: Map<String, Any>) {}
    override fun error(operation: String, error: Throwable?) {}
}
