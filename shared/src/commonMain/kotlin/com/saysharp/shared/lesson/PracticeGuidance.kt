package com.saysharp.shared.lesson

/** Listening criteria, not automated evaluations of the user's speech. */
data class PracticeGuidance(val listenFor: String, val retryFocus: String)

fun PracticeLesson.guidance(): PracticeGuidance = when (day) {
    1 -> PracticeGuidance("Listen to your first sentence. Does it state your answer before any background?", "Start with your answer. Give the reason afterwards.")
    2 -> PracticeGuidance("Can you name the one main reason you gave for your answer?", "Keep your strongest reason. Leave the others for a follow-up.")
    3 -> PracticeGuidance("Can you picture one specific example that supports the point?", "Replace one broad claim with a concrete example.")
    4 -> PracticeGuidance("Did any sentence repeat a point or delay the answer? If so, there is something to cut.", "Give your position and one useful detail, then stop within 20 seconds.")
    5 -> PracticeGuidance("Could the listener identify a specific action or detail, rather than just ‘better’ or ‘soon’?", "Replace one vague word with an action, owner or concrete detail.")
    6 -> PracticeGuidance("After the reason, did you stop or say the same point again?", "Say your point once and your reason once. Then pause.")
    8 -> PracticeGuidance("Did you name a benefit you would give up while keeping your choice clear?", "Keep your recommendation and name one cost of choosing it.")
    9 -> PracticeGuidance("Did you challenge the proposal with a reason, without judging the person?", "Acknowledge their goal, then disagree with the idea and give one reason.")
    10 -> PracticeGuidance("Could the listener tell exactly what you could or could not commit to?", "State your boundary first. Offer an alternative only afterwards.")
    11 -> PracticeGuidance("Did your opening address the specific concern, rather than repeat your pitch?", "Answer the actual follow-up first. Name an uncertainty if needed.")
    12 -> PracticeGuidance("Did you finish with a specific action someone could take next?", "Finish your recommendation with one concrete next step.")
    13 -> PracticeGuidance("Can you hear a choice, a supporting reason and one cost in your own words?", "Make a choice, support it, and name a cost without memorising a script.")
    else -> error("No practice guidance for checkpoint day $day")
}
