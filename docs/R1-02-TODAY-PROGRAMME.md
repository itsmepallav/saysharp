# R1-02 — Today and the 14-day programme (SS-011 / SS-012)

Historical R1-02 report. [R1-03](R1-03-CURRICULUM.md) replaces the older lesson catalogue with the full shared curriculum; Today/Programme keep their existing navigation and state projection.

## Inspection and decisions

The checkout already had SS-070's shared StateFlow session controller and local repository, SS-072's offline Day 1 loop, R1-01's onboarding, typed navigation, Today/Programme components and a complete authored offline catalogue for Days 1–14. Existing Curriculum/BundledSessionRepository definitions include ordinary lessons and Days 7/14 checkpoints; a common regression test already traverses the entire programme.

Reused those components, catalogue and sequential progression. No future lesson content was authored in this task. Days 2–14 are existing implementations, not new SS-072-style redesigns or device-validated lessons. The UI does not offer direct access to upcoming lessons. There is no calendar wait between completed lessons.

## Implemented screens

Today contains only the brand eyebrow, current day out of 14, phase, today's skill, one short explanation, one prominent Start today's practice / Resume practice CTA and a lightweight completed count/14-segment progress indicator. The prior motivational headline, goal footer, comparison highlight and decorative practice checklist were removed from Today.

After all 14 days, Today shows Programme complete, a short explanation, 14/14 progress and Review my practice. There is no Day 15 or Speaking Gym action.

Programme shows two sections with day ranges and phase goals:
- Speak Clearly, Days 1–7: express thoughts clearly, concisely and precisely.
- Speak With Conviction, Days 8–14: communicate opinions, disagreements and boundaries with clarity and confidence.

Every row shows its number, short skill title and explicit Completed / Current lesson / In progress / Upcoming status. Current rows have a subtle forest border/sage background. Completed rows can review their saved journal; only the current row has Start/Resume. Upcoming rows have no start, completion or arbitrary selection action.

Both screens use the existing typography, forest/ivory tokens, Page, PrimaryAction and ProgressTrack. Pages scroll, headings expose accessibility semantics, and actionable lesson rows use at least 48dp controls. Native visual/accessibility checks remain outstanding.

## State and navigation

ProgrammeOverview is a pure commonMain projection of the existing PracticeSession. It contains typed phases, metadata, row statuses, current lesson, action and completion count. ProgrammeCatalogue adapts existing authored titles and instructions; it does not duplicate lesson contents.

SessionUiState exposes `programme` as a computed property of its repository-backed progress. SaySharpApp already collects the sole StateFlow; Today and Programme read the same snapshot through the existing composition local. Neither screen owns a repository, cached completion counter, separate controller or writable progress state.

Rules:
- Fresh session: Day 1, Speak Clearly, Start today's practice.
- Unfinished session after leaving its welcome stage, or with saved recording activity: same day and Resume practice.
- Completed day: select the next day, show the completed row, and permit immediate start using beginNextDay.
- Completed Day 7: Day 8 / Speak With Conviction.
- All 14 archived days: programme complete and review only. beginNextDay returns false at terminal Day 14.
- Completion transitions reject a day if any preceding day is absent from completed history. Metadata/navigation alone can never complete a lesson.
- The existing atomic repository remains the sole persistent source of truth. A failed save does not advance either overview; restart restores the saved lesson/stage/references.

Today is now the post-onboarding landing destination as requested by R1-02. The three R1-01 onboarding pages/copy are unchanged; only their completion destination changes from immediate Day 1 to Today. One Start today's practice action then opens the same working Day 1 technique/scenario. Subsequent starts/resumes use existing typed AppScreen.Practice and the existing session flow. Completed row reviews use AppScreen.Progress.

No recording engine, onboarding page implementation, storage format, signing, versioning, waiting rule or other lesson content was changed. Existing global milestones and unrelated settings remain intact; no new gamification was added.

## Files changed in R1-02

New:
- `shared/src/commonMain/kotlin/com/saysharp/shared/programme/ProgrammeOverview.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/programme/ProgrammeOverviewTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/ProgrammeFlowTest.kt`
- `docs/R1-02-TODAY-PROGRAMME.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/HomeScreens.kt` — focused Today, two-phase Programme, progress and row components.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/SaySharpApp.kt` — screen callbacks, completion review and Today landing after onboarding.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionState.kt` — shared computed overview.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionTransition.kt` — sequential completion guard.
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeController.kt` — terminal Day 14 does not start another session.
- `shared/src/commonTest/kotlin/com/saysharp/shared/practice/PracticeControllerTest.kt` — terminal progression expectation.
- `shared/src/commonTest/kotlin/com/saysharp/shared/telemetry/TelemetryTest.kt` — sequential-completion fixtures include prior history.
- `app/src/androidTest/java/com/saysharp/saysharp/PracticeFlowTest.kt` — Today CTA selectors and valid checkpoint history fixture.
- `app/src/androidTest/java/com/saysharp/saysharp/EngagementFlowTest.kt` — Today landing/navigation assertions and CTA selectors.
- `app/src/androidTest/java/com/saysharp/saysharp/StoreScreenshotCaptureTest.kt` — updated Today/Programme/Day 1 selectors; no screenshots exported or published.
- `docs/R1-01-ONBOARDING.md` — notes that R1-02 supersedes the earlier landing destination.

Earlier uncommitted SS-070/SS-072/R1-01 changes were preserved. This inventory identifies R1-02 changes, not the entire working-tree diff.

## Automated verification

Executed from the project root with Android Studio's JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: BUILD SUCCESSFUL. 82 shared host tests + 4 Android unit tests = 86 passing, zero failures/errors/skips. Ten new ProgrammeOverviewTest cases cover all eight requested state scenarios, catalogue/phase boundaries, terminal progression and atomic-save failure/retry. Existing Day 1, onboarding and full-programme controller regressions pass.

Debug and instrumentation APKs compile. Lint: zero errors, 25 warnings for dependency/resource/project housekeeping. `git diff --check` passes. Output is in `/tmp/r102-final-build.log`; JUnit XML is in the usual shared/app build/test-results directories.

Six new ProgrammeFlowTest instrumentation cases cover fresh agreement/upcoming actions, actual fake-audio Day 1 completion and next lesson, partial-session relaunch, phase transition, terminal review and small-screen/2× font layout. Existing onboarding/practice UI fixtures were updated. Instrumentation tests **compiled but did not execute**: adb reports no connected devices and the emulator has no configured AVD.

No installation, real audio/device verification, iOS build, Notion update, GitHub push or Play publication was performed.

## Manual Android QA still needed

1. Fresh install: finish the unchanged three onboarding pages. Confirm Today shows Day 1 of 14, Speak Clearly, Lead with your point, a concise explanation, 0/14 and one practice CTA. Ask a first-time user to identify what/why/how within five seconds; this comprehension target has not been measured.
2. Tap Start today's practice and complete the existing offline Day 1 loop. Confirm first/retry/transfer audio remain separate. Return to Today and Programme: both should show one completion and Day 2 as current immediately.
3. Start Day 2 without waiting. Leave midway, force-stop/relaunch and confirm Resume practice restores the same day/stage/audio. Compare the current/in-progress row with Today.
4. Browse all 14 rows. Only completed days may review and only the current day may start/resume. Tapping or scrolling an upcoming row must not enter or complete it.
5. In isolated test fixtures, load completed Days 1–7 and verify Day 8/Speak With Conviction. Verify the completed first-phase rows and current second-phase row agree with Today.
6. Load a completed 14-day test fixture. Confirm Programme complete, 14 completed rows and Review my practice; no further practice, Day 15 or Gym action.
7. Repeat while offline, after app restart, in landscape, on a small phone and with 200% font scaling. Ensure titles wrap, all content scrolls and actions remain reachable.
8. With TalkBack, verify phase/headline reading order, status text, progress count and identifiable Start/Resume/Review controls. State must be understandable without relying on color.
9. Run `./gradlew :app:connectedDebugAndroidTest` on a connected development device and retain the actual report. Native persistence/microphone checks remain in the SS-072 checklist.

Remaining limitations: device rendering, TalkBack, actual Android lifecycle/persistence and the five-second comprehension goal are unverified. Days 2–14 retain their existing content/flow and still need separate R1 product review. No known host-test or build failure remains.
