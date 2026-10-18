# R1-03 — Fourteen-day curriculum

## Final curriculum matrix (drafted before implementation)

| Day | Title | Unique objective | Observable success criterion | Initial scenario | Transfer scenario |
|---|---|---|---|---|---|
| 1 | Lead with your point | State the conclusion before context | Main point is in the first sentence | Client demo: rehearse or add a feature | Apartment budget: plants or broken lock |
| 2 | Give one strong reason | Link a position to one relevant reason | The stated reason explains why the recommendation helps | Friends choosing a cheap, distant rental | Community garden: open access or reserved plots |
| 3 | Make it concrete | Illustrate an abstract claim | One specific example makes the claim understandable | Explain what makes a neighborhood welcoming | Explain what makes instructions useful |
| 4 | Cut the extra words | Compress wording without losing meaning | Retry keeps the point with fewer repeated/setup phrases | Explain a delayed delivery to a customer | Give a neighbor a concise parking update |
| 5 | Choose precise words | Replace vague claims with specifics | A listener knows what exactly should change | Ask a roommate to improve shared-kitchen habits | Specify a useful improvement to a local event |
| 6 | Know when to stop | End once a position is supported | No new conclusion loop after the reason/detail | Recommend a restaurant for a friend's birthday | Explain why to keep or cancel an unused subscription |
| 7 | Speak clearly checkpoint | Apply clarity independently | Fresh answer makes its recommendation understandable | Day 1 client-demo prompt, unchanged | Library budget: evening hours or Sunday opening |
| 8 | Weigh trade-offs, then decide | Recognize a cost and make a choice | Names a real downside and still reaches a decision | Choose a quiet, costly apartment or a cheaper noisy one | Neighborhood street: parking or protected bike lane |
| 9 | Disagree without backing down | Challenge an idea respectfully | Explicit disagreement plus a reason, without a personal attack | Friend says unpaid internships should be normal | Club proposes banning all phones at events |
| 10 | Set a clear boundary | Decline a request respectfully | Clearly says what the speaker will not do | Coworker asks you to take over their weekend shift | Friend repeatedly asks to borrow your car |
| 11 | Answer the follow-up | Address the question actually asked | First response answers the specific challenge | Reading-club fee: how would low-income members join? | Proposal to cook together: who handles cleanup? |
| 12 | Hold your ground under pushback | Respond to the substance of an objection | Acknowledges objection and explains defense or revision | Predictable work hours versus an urgent customer incident | Neighborhood cleanup versus limited volunteer time |
| 13 | Think on your feet | Organize a response with little preparation | Clear position and relevant reason despite a short preparation window | Unexpected invitation to defend a life skill for schools | Unexpected choice about what a local museum preserves |
| 14 | Final speaking checkpoint | Independently combine clarity and reasoned assertiveness | Responds directly to a new objection with a reasoned position | Day 1 client-demo prompt, unchanged | Club sponsorship: funding versus restrictions on criticism |

Day 4 targets unnecessary wording; Day 6 targets the decision to stop after sufficient support. Day 11 answers a question; Day 12 evaluates an objection and permits thoughtful revision. Day 13 adds unfamiliar content and a shorter optional preparation window without treating speed as a score.

## Authored content and learning loop

Twelve ordinary lessons use the same eight-stage route as Day 1: technique/scenario → first response → replay/self-check → contrast/why/correction → same-scenario retry → optional comparison playback → fresh transfer → completion. No model answer appears during the first response or self-check. Done, Not yet and Unsure are all valid; no positive rating is required. Unsure reveals only the day's listening clarification.

Each ordinary definition contains a stable ID, day/phase, title, objective, instruction, actionable technique, scenario/question, duration, one listening question, short clarification, weak/strong contrast, explanation, corrective retry instruction, distinct transfer and real-world suggestion. Existing Day 1's scenario, answer examples, first/retry separation and route are retained; US spelling and the missing rationale/metadata were added.

The authored situations mix client work, travel with friends, neighborhoods, customer service, shared housing, birthdays, libraries, apartment decisions, internships, clubs, coworkers, reading groups, communal dinners and public sponsorship. The stronger answers are illustrative positions rather than the answer the user must agree with. Day 12 explicitly permits thoughtful revision and models a limited exception for genuine emergencies.

The old duplicate Programme/Curriculum/PracticeGuidance catalogue was removed. Its additional vocabulary/recall drills are no longer part of the R1 sessions; they would dilute the single-skill loop and lengthen practice. Persistence enums/fields for those old drill roles remain inert to avoid a separate storage/architecture cleanup in this content task. No migration/reset flow was added.

## Checkpoint behavior

Days 7 and 14 begin with the exact Day 1 client-demo situation and question, then a genuinely new prompt. The familiar question is recorded before any comparison playback. Neither checkpoint contains model examples, a retry scaffold, vocabulary drills or an ordinary coaching stage.

The comparison screen offers three separate players: Day 1 First attempt, today's Familiar question, today's Fresh question. The familiar and fresh recordings must both finish replaying before the user can leave the comparison stage. A qualitative familiar-answer comparison (Clearer/About the same/Less clear/Unsure) and a fresh-answer self-check are saved. A Summary step invites an observable reflection; no typed response or numeric proficiency score is required.

Day 7's fresh library decision tests whether a clear recommendation survives a new context. Day 14's sponsorship dilemma adds an explicit affordability objection, testing independent reasoning and assertiveness. The reflection distinguishes familiarity/practice effects from transfer. If the historical Day 1 file is unavailable, the UI says so rather than claiming a valid audio comparison; today's answers remain usable.

## Shared models, repository and integration

SessionCatalogue is the single read-only list of fourteen immutable SessionDefinitions. BundledSessionRepository loads the same definition by day or stable ID. The ordinary builder only supplies common route/prompt structure; scenario text is explicitly authored per lesson, not generated from a generic template at runtime.

SessionDefinition adds objective, actionable technique, example explanation, real-world application, suggested seconds, optional preparation time and opening-only replay policy. ID and phase are derived deterministically. A nullable two-field CheckpointContent supplies the comparison lesson ID and reflection; `isCheckpoint` derives from its presence.

Stable IDs are `saysharp-day-01` through `saysharp-day-14`. Prompt role IDs include the lesson ID. PracticeSession exposes the same computed lessonId. Local persistence retains the canonical day and per-day history keys; it does not store a redundant second lesson ID that could disagree. Saved recording UUIDs remain opaque and unchanged, associated with the lesson by their containing progress/review and RecordingSlot. Today/Programme metadata and Progress labels now come from the same definitions. No audio engine or storage format was rewritten.

The coordinator reuses existing recording/playback/persistence. Ordinary self-checks now all require successful first replay; checkpoint self-checks require fresh replay. Same-scenario retry recordings remain independent from First. New days enter their definition's first stage; no extra baseline recording is inserted. The Day 1 First reference is carried for checkpoint comparison. The full traversal retains 40 distinct files and earlier day reviews across restarts.

## Files created, modified and removed

Created:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionCatalogue.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/lesson/SelfAssessment.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/SessionCatalogueTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/CurriculumFlowTest.kt`
- `docs/R1-03-CURRICULUM.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionContracts.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/DayOneSession.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/BundledSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionTransition.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeController.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeSession.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/programme/ProgrammeOverview.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/PracticeScreen.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/ProgressScreen.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/practice/PracticeControllerTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/SessionFoundationTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/programme/ProgrammeOverviewTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/telemetry/TelemetryTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/PracticeFlowTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/ProgrammeFlowTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/StoreScreenshotCaptureTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/SessionStoreTest.kt`
- `docs/R1-02-TODAY-PROGRAMME.md` (historical report points to this catalogue replacement).

Removed duplicate content/obsolete tests:
- `shared/src/commonMain/kotlin/com/saysharp/shared/lesson/Programme.kt` (SelfAssessment moved without changing its values).
- `shared/src/commonMain/kotlin/com/saysharp/shared/lesson/Curriculum.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/lesson/PracticeGuidance.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/lesson/ProgrammeTest.kt` (superseded by the new catalogue and traversal checks).

Earlier uncommitted SS-070/SS-072/R1-01/R1-02 work was preserved. This inventory identifies this task's edits rather than the full working-tree diff.

## Tests and actual results

Executed from the repository root:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: BUILD SUCCESSFUL. 86 shared host tests + 4 Android unit tests = **90 passed**, zero failures/errors/skips. Eight new catalogue tests replace four obsolete legacy-catalogue tests. Existing regression tests were updated for the common ordinary route and checkpoint replay requirements; Day 1's focused tests remain and pass. The first build had six failures from old route/fixture expectations; those were corrected before the final successful run.

Verified by executed tests: fourteen unique stable IDs; required populated fields; one self-check question per ordinary lesson; distinct transfer; repository day/ID lookup; phase mapping; checkpoint route and unchanged familiar prompt; credible Day 12 pushback/revision; example-stage gating; replay/assessment prerequisites; all fourteen lessons traversed with controller recreation after every stage; 40 separate recording references; earlier review preservation; sequential/terminal progression; Day 1 recording and failure protections.

Debug and instrumentation APKs compile. Lint reports zero errors and 25 dependency/resource/project housekeeping warnings. `git diff --check` passes. Build log: `/tmp/r103-final-build.log`; host results: `shared/build/test-results/testAndroidHostTest/` and `app/build/test-results/testDebugUnitTest/`.

A new real-Compose/fake-audio instrumentation test exercises all twelve ordinary lessons, checking model answers remain absent until correction, hints/retry and fresh transfer. The updated Day 7 UI test and a new all-ID Android JSON round-trip test compile. **Instrumentation tests were not executed.** `adb devices` returned no connected devices; `emulator -list-avds` returned no configured AVD. No real audio, native lifecycle, screenshot export, iOS, release build, install, Notion update, GitHub push or Play publication occurred.

## Content concerns and trade-offs

- Four-to-six minutes is a design target, not measured completion data. Most prompts suggest 30–60 seconds; Day 4's retry is capped at 20 seconds and its transfer at 30. Optional comparison playback and retakes can make a session longer.
- Day 1's deliberately simple budget transfer is less nuanced than later dilemmas. It is preserved as a low-friction first skill rather than replaced with a harder debate.
- Strong examples are possible responses, not objectively correct opinions or scripts to memorize. Self-checks cannot establish proficiency; they ask about the user's observable attempt.
- The repeated checkpoint prompt can improve through familiarity. Fresh prompts are essential to the reflection and should not be skipped when judging transfer.
- Day 13's three-second preparation suggestion is optional and never auto-starts the microphone. Coherence matters, not speed; this is not a separate Pressure Practice mode.
- Young professionals' real contexts vary. Human testing should check whether rental, shared-housing, club and workplace scenarios feel relevant without assuming a particular job or living arrangement.

## Manual Android QA and remaining R1 work

1. Run `./gradlew :app:connectedDebugAndroidTest` on a development device; retain the observed reports, including the all-ordinary example-visibility test and native JSON round trips.
2. Complete all 14 days offline. Verify every Today/Programme title and phase matches this matrix, sessions start/resume the correct ID/day, and no upcoming lesson can skip progression.
3. For every ordinary day: confirm no weak/strong answer before initial recording or during self-check; replay, choose each self-check value (including Unsure), inspect contrast/why, retry the identical situation and record a genuinely different transfer.
4. Verify First and Retry are individually playable before/after restart. Test background interruption and force-stop at first, retry and transfer steps; retained earlier files/reviews must remain safe.
5. On Day 4, verify the 20-second retry and 30-second transfer limits. On Day 6, end naturally before the timer expires. On Day 13, verify the short preparation suggestion never records automatically or forces a timed answer.
6. On Days 7/14, verify the familiar prompt matches Day 1 exactly, playback is offered only after both independent recordings, and the baseline/familiar/fresh players contain the correct separate audio. Check replay gating, Unsure hints and reflection without numerical scores.
7. Test Day 12 with both a reasoned defense and a thoughtful revision. Both should fit the criterion; the UI must not pressure the user to resist every objection.
8. Repeat with small-screen/landscape layouts, 200% font scaling, TalkBack, headphones, permission denial and audio-focus interruption. All text must scroll and primary actions remain reachable.
9. Time representative sessions and have target users paraphrase each criterion. Confirm Days 4/6 and 11/12 feel distinct, examples sound natural, and the programme feels like spoken practice rather than reading. Refine only observed content/usability issues.
10. Finish with Day 14: verify programme-complete state, all earlier reviews/recordings intact, and review-only navigation without a Gym or paywall.

Remaining R1 work is device QA, accessibility review, target-user content testing and measured session timing, then fixes for observed issues. Automated tests do not prove real-world communication improvement. No AI analysis, proficiency scoring, additional programme, billing, cloud sync, migrations/reset or Play publishing was added.
