# R1-05 — Corrective retry and A/B playback (SS-016 / SS-017)

## Existing functionality reviewed

Read R1-04-SELF-ASSESSMENT.md and inspected SessionDefinition/SessionCatalogue, PracticeController, pure session transitions, PracticeScreen, Playback, local AtomicFile storage and the Android media adapter. The existing ordinary route already required a saved first recording and its replay/self-check before correction, kept First/Retry in separate slots, required a saved Retry before Compare, and allowed optional comparison without a second assessment. Checkpoints use a separate familiar/fresh route.

Android recording already creates UUID-named private local files. The coordinator saves references atomically before replacing a same-slot file; failed replacement retains earlier attempts. Callback generations protect later operations. Playback already stops the previous player before starting another, stops on navigation/background, and has recoverable errors. Normal backgrounding finalizes a playable partial recording; a process-death marker restores an unfinished retry without promoting the interrupted file. These mechanisms were reused, not rebuilt.

The R1-04 baseline was verified from current code/test results, then the full relevant build/tests were run for this task. No production recorder, player port, repository, storage schema, coordinator state machine or checkpoint flow needed replacement.

## Changes implemented

- Correction has one prominent **Try again** CTA.
- Retry is explicitly headed **Retry · Attempt 2**, with the same original scenario/question and its one corrective instruction.
- Comparison is headed **Compare your attempts**, avoiding an unsupported claim that the retry improved.
- First attempt and Retry keep independent existing play/stop controls. Duration remains visible while playing; opening-only replay is identified as an opening with the full recording's total duration.
- The focus remains the day's existing observable criterion. Copy explicitly says listening is optional; the existing Try a fresh question CTA is enabled without replaying both files or adding any rating.
- Five wordy/multi-sentence correction instructions were shortened to one action/sentence. The other seven ordinary corrections were retained.
- Focused state/playback and real-Compose/fake-audio tests were added. Existing UI selectors follow the new CTA/title.

No waveform, editing, pause/resume engine, score, reflection form, extra mandatory attempt or new repository was added. Play/stop is the existing supported media interaction.

## Final ordinary lesson flow

1. Save First attempt, replay and select Done / Not yet / Unsure using R1-04.
2. Reveal the lesson's weak/strong examples, why the difference matters, and exactly one correction from SessionDefinition.
3. Tap Try again. Record Attempt 2 against the identical scenario. First's audio and self-check remain retained.
4. Save Retry, then open Compare your attempts. Independently play either file; starting one stops the other. Optional listening and the day's criterion guide reflection without claiming a better result.
5. Continue to the distinct existing transfer prompt without a model answer or second correction loop.
6. Save transfer and complete through the existing progression rules.

Save & leave/system Back use the existing background cleanup and exit to Today. Resume restores Retry or Compare as saved. Returning to Compare does not create a recording. Finishing the programme still uses existing review-only navigation.

## Correction-copy refinements

| Day | Final corrective instruction | Why refined |
|---|---|---|
| 1 | Start with your recommendation, then explain why. | Replaces three sentences with the actual opening correction. |
| 4 | Keep the cause and next step, and remove information you have already given. | Targets repeated information; the existing 20-second retry limit remains in the prompt data. |
| 6 | Stop after your last useful supporting detail, even if the timer has time left. | One action about stopping rather than another planning step. |
| 11 | Make your first sentence answer the specific gap raised by the follow-up question. | Directly identifies the next response to change. |
| 13 | Develop one defensible position instead of restarting when another idea occurs to you. | Targets coherence without multiple instructions or a speed score. |

Days 2/3/5/8/9/10/12 already had specific single-sentence corrections; they were kept. All twelve ordinary corrections were reviewed for technique alignment; tests verify a nonempty single sentence of no more than 25 words, the same first/retry question and distinct transfer. Their weak/strong examples and rationale remain unchanged. Days 7/14 content and checkpoint stages were not changed.

## State and persistence

No new writable state or persistence fields. First and Retry remain separate RecordingSlot entries with stable saved references. Successful Retry save changes only Retry-related state; First's self-assessment and earlier reviews remain intact. A failed save cannot advance Retry without a kept retry reference. An explicit optional retry retake may replace the previous Retry only after successful persistence; it never replaces First. Full retake history is not stored.

Recording/start/stop and stage-bound advance guards already make repeated taps safe. Playback generation checks ignore stale completion/error callbacks. Runtime playback state is cleared on stop, navigation and background; relaunch restores references, not automatic playback. The R1-04 stale lesson/recording assessment guard, Unsure guidance replay, selection persistence and idempotent listening saves are unchanged.

Only a successfully finalized partial file is retained on normal backgrounding; a hard process kill cannot guarantee recovery of unfinished audio. The earlier First attempt and saved session remain safe. This task adds regression coverage rather than claiming new native-device evidence.

## Files created or modified in R1-05

Created:
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/RetryComparisonTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/RetryComparisonFlowTest.kt`
- `docs/R1-05-RETRY-COMPARISON.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/DayOneSession.kt` — Day 1 correction sentence.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionCatalogue.kt` — Days 4/6/11/13 correction copy.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/PracticeScreen.kt` — Try again, Attempt 2, neutral optional comparison copy.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/ScreenComponents.kt` — readable playing state plus duration.
- `app/src/androidTest/java/com/saysharp/saysharp/CurriculumFlowTest.kt` — new CTA selector and correct correction-copy assertion for every ordinary lesson.
- `app/src/androidTest/java/com/saysharp/saysharp/PracticeFlowTest.kt` — CTA selector.
- `app/src/androidTest/java/com/saysharp/saysharp/EngagementFlowTest.kt` — explicit Retry heading selector.
- `app/src/androidTest/java/com/saysharp/saysharp/ProgrammeFlowTest.kt` — CTA/Retry heading selectors.

Earlier uncommitted R1 changes are preserved. This inventory identifies this task's edits, not the full Git diff. Production recording, controller, navigation and storage implementations were not changed in R1-05.

## Exact verification and results

Executed from the repository root using Android Studio's JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain --rerun-tasks
```

Result: **BUILD SUCCESSFUL; 108 shared host tests + 4 Android unit tests = 112 passing**, zero failures/errors/skips. This is 12 new host tests above R1-04's 100-test baseline. Existing Day 1, all-fourteen traversal, R1-04 Unsure/assessment and Day 7/14 checkpoint tests remain passing. Both debug APK and instrumentation APK compile. Lint remains **zero errors, 25 warnings**, the same count as R1-04. `git diff --check` passes.

New host coverage: one correction/same scenario/distinct transfer; saved and assessed First before correction; distinct Retry identity; A→B/B→A stop order; stale playback callbacks; stop/navigation cleanup; optional comparison without replay/rating; duplicate start/stop/advance; failed save blocking progression; interrupted/background retry; process-death recovery; returning to Compare without new attempts; playback-to-recording exclusion.

An initial focused test failed because its fake completion callback cleared the newer fake player's resource; the fake was corrected to match the Android adapter's identity ownership. The actual coordinator's generation protection was retained and passes the stale-callback assertions.

Three new Compose/fake-audio tests check labels/durations/switching/navigation, optional comparison/fresh transfer and large-text controls. The existing all-ordinary UI test now asserts the displayed correction equals the definition. **Instrumentation tests compiled but did not execute:** adb reports no devices and there is no configured emulator AVD. No physical microphone, audio-focus or TalkBack pass is claimed.

Log: `/tmp/r105-final-build.log`. JUnit XML: `shared/build/test-results/testAndroidHostTest/` and `app/build/test-results/testDebugUnitTest/`.

No app install, Notion update, GitHub push, Play upload, migration, new telemetry payload or AI/transcription feature was performed.

## Physical Android QA checklist

1. In airplane mode, record First saying a recognizable phrase and Retry saying a distinctly different phrase. Confirm the correction is hidden before First/self-check and the Try again action opens the same scenario as Attempt 2.
2. On Compare, play First and Retry in both orders, including switching mid-playback. Only one should be audible. Check labels, durations and Playing/Stop state. Tap the active control to stop; replay should begin from the start.
3. Leave while playing using Save & leave, system Back, Home and another app taking audio focus. Confirm audio releases and Resume returns to the saved stage/files without automatic playback or new attempts.
4. Deny/revoke microphone permission before Retry. Confirm a recoverable state and that First remains playable; grant permission and retry. Do not uninstall/clear data as part of this check.
5. Background during Retry and interrupt it with an audio-focus loss/call. Confirm a saved playable partial or recovery message, with First and its self-check unchanged. Test failed-save paths with fakes/test storage; never falsely advance a retry without a kept file.
6. Kill/relaunch after First, mid-Retry, after saved Retry and at Compare. Confirm correct stage/identities, retained assessment, and no duplicate file created merely by reopening playback.
7. Use slow repeated and rapid double taps on Start/Stop/Try again/Compare/Continue. They must not create duplicate attempts, skip required recording or overwrite First. An explicitly chosen Record again may replace Retry only after the new save succeeds.
8. Skip comparison playback and continue directly to transfer. Verify no second assessment is required, the prompt is genuinely different and no model response is shown; complete normally.
9. Repeat on a small phone, landscape and 200% font scaling. Ensure all text and both players scroll; the fixed primary action remains reachable.
10. With TalkBack, verify distinct First attempt/Retry play-stop labels, duration reading, heading/order and R1-04 Unsure guidance/replay announcements. No automatic ‘improved’ outcome should be announced.
11. Recheck Days 7/14: their familiar/fresh comparison, replay prerequisites and checkpoint self-check remain separate and unchanged.
12. Run `./gradlew :app:connectedDebugAndroidTest` on the connected development device and retain the actual result/report.

## Known limitations and next steps

Native device audio quality, permission/lifecycle behavior, font scaling and TalkBack remain unverified. Stop/restart is supported; pause/resume and waveform editing are intentionally absent. A hard-killed unfinished recording is not guaranteed recoverable; previously saved recordings are retained. Only the kept latest optional Retry is stored, not every voluntary retake.

Next: execute connected instrumentation and the physical checklist, then fix observed integration/accessibility failures. A/B reflection is subjective and cannot prove speaking proficiency or that Retry is better. No broader architecture, curriculum, checkpoint, payment or publishing work is needed for this task.
