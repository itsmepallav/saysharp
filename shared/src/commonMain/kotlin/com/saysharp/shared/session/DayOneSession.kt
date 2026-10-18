package com.saysharp.shared.session

import com.saysharp.shared.practice.PracticeStage
import com.saysharp.shared.practice.RecordingSlot

/** One skill, three independent recordings. Examples are shown only after self-check. */
val DayOneSession = SessionDefinition(
    day = 1,
    title = "Lead with your point",
    instruction = "Say your main point in your first sentence. Then give one reason and one useful detail. Your listener should not have to wait to discover what you mean.",
    scenario = "Your team has 30 minutes before an important client demo. A colleague wants to add an untested feature; another wants to rehearse the existing demo.",
    selfCheck = "Did I state my main point in the first sentence?",
    weakExample = "We have half an hour, and the new feature could be useful, but the demo matters too… I suppose we should rehearse.",
    correctiveExample = "We should rehearse the existing demo. We only have 30 minutes, and an untested feature could fail in front of the client. Let's save it for the next demo.",
    correction = "Start with your recommendation, then explain why.",
    listeningHint = "Listen to your opening sentence. Does it state your main point before you explain it?",
    prompts = mapOf(
        RecordingSlot.First to SpeakingPrompt("saysharp-day-01-first", "What should the team do, and why? Speak to your colleagues.", 60_000),
        RecordingSlot.Retry to SpeakingPrompt("saysharp-day-01-retry", "What should the team do, and why? Speak to your colleagues.", 60_000),
        RecordingSlot.Application to SpeakingPrompt("saysharp-day-01-transfer", "Your apartment building has a small shared budget. Neighbors disagree between buying plants for the entrance and fixing a frequently broken front-door lock. What should the building prioritize, and why? Speak at the residents' meeting.", 60_000),
    ),
    stages = listOf(PracticeStage.Welcome, PracticeStage.FirstAttempt, PracticeStage.FirstCheck,
        PracticeStage.Lesson, PracticeStage.Retry, PracticeStage.Compare, PracticeStage.Apply, PracticeStage.Complete),
    objective = "State your main point before explaining it.",
    technique = "Put your recommendation in your first sentence.",
    exampleExplanation = "The second answer makes the recommendation clear immediately. Its reason explains the choice instead of making the listener wait for it.",
    realWorldApplication = "In your next conversation, give your answer before your explanation.",
    openingOnly = true,
)
