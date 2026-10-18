package com.saysharp.shared.engagement

/** Bundled value proposition. Onboarding never requests permissions or changes session progress. */
data class OnboardingPage(val headline: String, val supportingText: String)

val OnboardingPages: List<OnboardingPage> = listOf(
    OnboardingPage(
        "Know what you mean. Struggle to say it?",
        "Stop rambling, losing your train of thought, or finding the right words too late.",
    ),
    OnboardingPage(
        "Think Sharp. Speak Bold.",
        "Learn to express your ideas clearly, disagree respectfully, and hold your ground.",
    ),
    OnboardingPage(
        "Get better by speaking.",
        "Take on a real scenario. Record your response, listen back, make one improvement, and try again.",
    ),
)

const val StartPracticing = "Start practicing"
