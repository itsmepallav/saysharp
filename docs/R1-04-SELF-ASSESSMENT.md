# R1-04 — Observable self-assessment and Unsure guidance (SS-014 / SS-015)

## Repository assessment

R1-03 already supplied one criterion/hint per definition, a three-choice self-check, replay gating, local persistence, correction after assessment and the checkpoint comparison route. These were reused. No separate assessment engine, score, storage table or navigation flow was needed.

The verified content gaps were multi-part criteria, hints that referenced a hypothetical listener's reaction, hints longer than two short sentences, and scenario-specific explanations that risked becoming miniature answers. The UI offered the hint far from playback on a small viewport. The coordinator already skipped unchanged assessment choices, but replay completion redundantly saved an already-listened recording; commands also lacked an explicit expected lesson/recording context.

## Final fourteen-day criterion and guidance table

Checkpoint rows describe their fresh-answer criterion; their separate familiar-answer comparison and three recording players remain intact.

| Day | Technique | Primary observable criterion | Unsure clarification |
|---|---|---|---|
| 1 | Put your recommendation in your first sentence. | Did I state my main point in the first sentence? | Listen to your opening sentence. Does it state your main point before you explain it? |
| 2 | State your choice, then connect it to one reason using ‘because’. | Did I give one clear reason that directly supports my point? | Check whether your reason explains why your main point makes sense. An extra opinion is not the same as a supporting reason. |
| 3 | After your claim, describe one specific situation that illustrates it. | Did I give a specific example to explain my point? | Listen for one concrete situation or action that illustrates your claim, rather than another general description. |
| 4 | Keep the point and its useful detail; remove phrases that repeat or delay them. | Did I avoid repeating information I had already given? | Listen for places where you explained the same idea twice without adding anything new. |
| 5 | Replace a vague description with an observable action and a clear context. | Did I replace vague wording with a specific action or detail? | Listen for what exactly should happen or change. A specific action or detail gives meaning to words like ‘better’ or ‘soon’. |
| 6 | Give your recommendation, support it, and end without restating it. | Did I finish without repeating my conclusion? | Listen to your ending. After your final supporting detail, did you stop or begin the conclusion again? |
| 7 | Independent familiar and fresh answers; compare with Day 1 | Did my fresh answer communicate a clear recommendation without repeating the same point? | On the fresh question, identify your recommendation. Check whether the following sentences support it rather than repeat it. |
| 8 | Name the trade-off, make your choice, and explain what tips the balance. | Did I make a clear choice after naming its trade-off? | Listen for the downside you considered and the choice you made. Weighing both sides still needs a conclusion. |
| 9 | State where you disagree, then explain one substantive reason. | Did I state my disagreement and explain why? | A clear disagreement identifies the point you see differently and explains why. Listen for both in your own answer. |
| 10 | State what you cannot agree to in a direct, respectful sentence. | Did I clearly say what I would or would not do? | Listen for a definite commitment or limit. An uncertain promise can leave the other person unsure whether you agreed. |
| 11 | Answer the exact follow-up in your first sentence, then add the needed detail. | Did I answer the specific follow-up question? | Identify what the question asks you to resolve. Check whether you answered that gap instead of repeating your original explanation. |
| 12 | Acknowledge the objection, then explain what you would keep or change and why. | Did I respond to the objection with a reasoned position? | Listen for how you addressed the objection's concern. Keeping or changing your position can both count if you explain why. |
| 13 | Take a short pause, then give your position and its strongest relevant reason. | Did I keep my response organized around one central point? | Listen for whether your sentences develop the same central point. A pause is fine; switching between unrelated answers loses the thread. |
| 14 | Independent familiar and fresh answers; compare with Day 1 | Did I address the objection directly with a reasoned position? | On the new challenge, listen for a direct response to the affordability concern. Your reason may defend or change your position. |

## Content changes and reasons

All twelve ordinary lessons retain exactly one primary question and a unique hint of one or two short sentences (no more than 30 words). Definitions remain the sole source of truth; no duplicate UI copy table was introduced.

- Days 1/3/5 now point to what can actually be heard: opening point, specific example and specific wording. They do not require guessing whether a listener understood or pictured something.
- Day 2 checks the reason-to-claim link, rather than the travel scenario alone. The same criterion can guide reflection on the transfer answer.
- Day 4 checks repeated information anywhere in the answer; Day 6 checks whether the ending repeats the conclusion. This preserves the word-economy versus stopping distinction.
- Days 8/9 assess the integrated behavior of making a reasoned choice/disagreement, without adding personality or voice-quality judgments.
- Day 10 checks the stated limit, rather than simultaneously judging directness, apologies, hostility and listener interpretation.
- Day 11 checks the actual follow-up question. Day 12 checks the actual objection, explicitly allowing a reasoned change of mind. Day 13 checks a coherent central point rather than speed or fluency.
- Days 7/14 keep their distinct multi-prompt checkpoint design and existing fresh-answer questions. Only their hints were shortened and kept appropriate to the new library/sponsorship prompts.

Hints explain where to listen; they do not provide a scripted response, reveal the strong example, or judge accent, attractiveness, confidence or personality. Done is a subjective claim about this attempt, not proof of proficiency. Not yet/Unsure do not reduce progress or trigger a failure screen.

## UI changes

The ordinary self-check now reads: Listen for one thing → question → existing playback control → grouped Done / Not yet / Unsure → contextual guidance when selected → existing continue action.

The hint appears in a compact sage panel below the options. It stays rendered while replay starts, stops or completes. A contextual Replay opening (Day 1) / Replay answer control sits beside the guidance, so users can listen without scrolling back to the main player. Both controls use the same existing player/state; no second playback engine is created. Deselecting Unsure removes the hint. Selecting Unsure again restores the same lesson-specific clarification.

Choices expose selected/radio semantics in a selectable group, preserve the forest border/check selected state, and have an explicit minimum 48dp touch target. The question has heading semantics; newly shown guidance has a polite live region. No celebratory animation, red failure page, forced tutorial, extra mandatory step or numerical rating was added.

Checkpoint assessment keeps baseline/familiar players and qualitative familiar comparison before the fresh-answer question/player/choices. Its guidance replay targets the fresh answer, not the familiar recording.

## State, persistence and privacy

Existing PracticeSession fields and ProgressRepository/AndroidSessionStore persist selections. A First assessment belongs to RecordingSlot.First; a checkpoint assessment belongs to RecordingSlot.CheckpointFresh through the existing retryAssessment field. Retry files remain separate and First's assessment/history remain unchanged. No storage schema change was made.

The UI passes expected lesson ID and recording ID with assessment commands. Stale callbacks cannot write a choice to a different lesson or replacement attempt. Stage/replay guards remain. Unchanged selections do not save again; failed writes preserve the previous selection and allow retry. Replay of an already-listened recording no longer rewrites identical progress. Starting a valid replay clears a stale playback message without altering the saved selection.

Existing StateFlow drives rendering. No separate hint-visible state is stored: visibility derives from the saved selection being Unsure. Users can change it before continuing, or continue honestly with Unsure. Advancing stops playback through the existing session mechanism.

No audio/transcript/reflection upload path was added. Source review found the Android audio adapter writes private local files; existing Firebase events for assessment contain only day, stage and enum choice. A focused test captures event/context boundaries and rejects audio identifiers and private reflection text. Existing CrashSanitizationTest verifies custom reports omit exception messages/causes. This is source/unit verification, not a live Firebase traffic capture.

## Files created or modified in R1-04

Created:
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/SelfAssessmentTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/SelfAssessmentFlowTest.kt`
- `docs/R1-04-SELF-ASSESSMENT.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/DayOneSession.kt` — shorter observable opening hint.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionCatalogue.kt` — refined ordinary criteria/hints and shorter checkpoint hints.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/PracticeScreen.kt` — question/player order, grouped choices and contextual replay guidance.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/ScreenComponents.kt` — explicit accessible choice role/touch minimum.
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeController.kt` — lesson/attempt command guard and idempotent listening persistence.

Earlier uncommitted R1 work is preserved. This inventory identifies this task's edits, not the entire working-tree diff.

## Executed verification

From the repository root, using Android Studio's JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain --rerun-tasks
```

Result: BUILD SUCCESSFUL. **96 shared host tests + 4 Android unit tests = 100 passing**, zero failures/errors/skips. The rerun explicitly executes tests instead of relying on earlier cached results. Debug and instrumentation APKs compile. Lint reports zero errors and 25 dependency/resource/project warnings. `git diff --check` passes.

Ten new host tests cover unique concise criteria/hints, all three choices across all ordinary lessons, selection changes/idempotence, replay with Unsure, correct attempt/history retention during retry, stale-command rejection, stage/replay gating, failed writes/retry, checkpoint fresh-assessment association and metadata-only telemetry. Existing Day 1, full fourteen-day traversal and checkpoint regressions remain passing.

Four new Compose/fake-audio instrumentation tests cover visible guidance during playback and after deselection, saved-instance restoration/continuing with Unsure, failed-write UI, and all-fourteen lesson switching/checkpoint context. They compile but **were not run**. `adb devices` reports no connected device; `emulator -list-avds` returns no configured AVD. Existing native JSON round-trip tests also compile but were not executed.

Build log: `/tmp/r104-final-build.log`. JUnit XML: `shared/build/test-results/testAndroidHostTest/` and `app/build/test-results/testDebugUnitTest/`.

No install, recording-engine rewrite, cloud feature, AI/transcription, proficiency score, migration, Notion update, GitHub push or Play submission was performed.

## Manual Android QA checklist and unresolved issues

1. Enter each ordinary self-check after a real recording. Confirm the question/criterion matches the table and no model example is visible. Replay to unlock the three options.
2. Select Done, Not yet and Unsure. Check clear selected state and that only Unsure reveals guidance. Re-selecting the same choice must not cause navigation or a duplicate assessment event.
3. While Unsure, start/stop replay from the guidance panel. The hint must remain visible; the correct First attempt must play. Day 1 uses its opening replay; other lessons replay the answer.
4. Change Unsure to Done and Not yet; verify the panel disappears and selection persists. Return to Unsure and continue: the correction must open without any penalty or forced re-evaluation.
5. Rotate and relaunch at self-check. Confirm the saved choice, correct hint, correct file and current lesson restore. Test failed persistence with test fakes; the previous selection should remain rather than falsely appearing saved.
6. Retry the same situation. Compare both files and confirm First's assessment is unchanged. Complete later lessons and review earlier choices to catch cross-day overwrites.
7. On Days 7/14, retain the Day 1/familiar/fresh players and familiar comparison. Unsure guidance/replay must apply to the fresh prompt, with both current recordings replayed before continuing.
8. Repeat on a small phone, landscape, 200% font scale and TalkBack. Text/choices must scroll, contextual replay and the fixed Continue action must remain reachable, and selected states must be announced without relying on color.
9. Run `./gradlew :app:connectedDebugAndroidTest` on a connected development device and retain observed results. Verify offline playback/interruption behavior and review Firebase debug events for metadata-only assessment payloads; do not upload user audio to perform this check.

Unresolved: actual Compose rendering, TalkBack/large-text behavior, Android process/lifecycle persistence and live Firebase traffic remain unverified on a device. Target-user testing is still needed to establish whether each question is easy to apply to a recording. There is no known build or host-test failure; subjective self-checks are deliberately not proficiency measurements.
