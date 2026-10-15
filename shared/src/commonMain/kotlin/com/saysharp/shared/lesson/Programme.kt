package com.saysharp.shared.lesson

enum class LessonKind { Practice, Checkpoint }
enum class SelfAssessment { Done, NotYet, Unsure }

data class LessonOutline(
    val day: Int,
    val title: String,
    val focus: String,
    val outcome: String,
    val kind: LessonKind = LessonKind.Practice,
)

data class PracticeLesson(
    val day: Int,
    val instruction: String,
    val brief: String,
    val prompt: String,
    val firstExample: String,
    val clearerExample: String,
    val selfCheck: String,
    val scaffold: String,
    val applicationPrompt: String,
    val realLifeTask: String,
    val vocabulary: VocabularyItem? = null,
    val openingOnly: Boolean = false,
)

data class VocabularyItem(val phrase: String, val meaning: String, val example: String, val prompt: String, val recallPrompt: String)
data class Checkpoint(val day: Int, val freshPrompt: String, val realLifeTask: String)

object Programme {
    const val goal = "Build a sharper, more articulate voice in 14 days of focused practice."
    fun lesson(day: Int): PracticeLesson = if (day == 1) firstLesson else Curriculum.lessons.getValue(day)
    fun checkpoint(day: Int): Checkpoint? = Curriculum.checkpoints[day]
    fun recalls(day: Int): List<VocabularyItem> = Curriculum.vocabularyByDay.filterKeys { day - it == 2 || day - it == 7 }.values.toList()
    val outline: List<LessonOutline> = listOf(
        LessonOutline(1, "Lead with your point", "Direct answers", "Sound decisive from your first sentence."),
        LessonOutline(2, "Give one clear reason", "Structure", "Turn an opinion into a case people can follow."),
        LessonOutline(3, "Make it concrete", "Useful examples", "Make an abstract idea click with one sharp example."),
        LessonOutline(4, "Say it in fewer words", "Concision", "Cut the rambling. Keep the impact."),
        LessonOutline(5, "Choose precise words", "Vocabulary", "Replace vague language with words that land."),
        LessonOutline(6, "Know when to stop", "Remove repetition", "Finish strong instead of talking in circles."),
        LessonOutline(7, "Hear your first week", "Repeated and fresh prompts", "Put your first week to the test. Hear the difference for yourself.", LessonKind.Checkpoint),
        LessonOutline(8, "Explain the trade-off", "Balanced reasoning", "Sound considered without sitting on the fence."),
        LessonOutline(9, "Disagree with clarity", "Assertiveness", "Hold your ground without sounding hostile."),
        LessonOutline(10, "Set a clear boundary", "Decisive answers", "Say no with clarity. Leave the long justification behind."),
        LessonOutline(11, "Handle a follow-up", "Composure", "Handle a tough question without losing your thread."),
        LessonOutline(12, "Make your case briefly", "Structure and concision", "Make a strong case in fewer words."),
        LessonOutline(13, "Speak without a scaffold", "Independent practice", "Own your answer. Speak clearly without a script."),
        LessonOutline(14, "Hear what has changed", "Repeated and fresh prompts", "Day 1 meets Day 14. Hear how your voice has changed.", LessonKind.Checkpoint),
    )

    const val baselinePrompt = "Suggest one change that would improve how your team works. Explain why."

    // Fictional discussion scenario: no unsupported statistics or current-affairs claims.
    val firstLesson = PracticeLesson(
        day = 1,
        instruction = "State your answer in the first sentence. Explain it afterwards.",
        brief = "Imagine a team considering one meeting-free afternoon each week. " +
            "Supporters want uninterrupted work time. Others worry that urgent decisions could be delayed. " +
            "There is no required position: practise making yours clear.",
        prompt = "Should this team introduce a meeting-free afternoon?",
        firstExample = "There are a few things to think about. Meetings can be useful, " +
            "but sometimes people need time to work, so it depends on the team…",
        clearerExample = "I would trial a meeting-free afternoon. It would protect focused work time, " +
            "while urgent issues could still use a separate contact channel.",
        selfCheck = "Did your first sentence answer the question?",
        scaffold = "My view is __. The main reason is __.",
        applicationPrompt = "Your team can launch on Friday with a minor issue or delay until Monday. " +
            "Choose an option, state your assumption, and explain why.",
        realLifeTask = "In your next conversation, give your answer before your explanation.",
        openingOnly = true,
    )
}
