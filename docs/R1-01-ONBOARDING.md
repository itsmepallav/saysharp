# R1-01 — Onboarding and value proposition (SS-009 / SS-010)

Historical R1-01 report. R1-02 subsequently makes Today the post-onboarding landing destination; see [R1-02-TODAY-PROGRAMME.md](R1-02-TODAY-PROGRAMME.md). The three onboarding pages remain unchanged.

## Repository assessment and scope

The current checkout already contained SS-070's KMP foundation and SS-072's complete offline Day 1 lesson. It also contained a three-page Compose welcome pager, an AtomicFile-backed engagement store, an onboarding completion flag named `welcomed`, and typed application destinations alongside a separate practice boolean.

Reused the pager, design system, engagement store/controller, telemetry and existing Day 1 screen. Replaced the onboarding copy/layout and removed its goal questionnaire and skip action. Renamed the existing flag to `onboardingCompleted` in the shared model and local JSON. No second preferences store, platform dependency, navigation library or new Gradle module was introduced. No migration or compatibility reader was added.

Day 1's content, state machine, recording/playback code and progress storage were not modified. Existing goal settings, reminders, optional journal and milestones retain their behavior. The explicit Settings intro replay remains available; completed onboarding is never shown automatically.

## Final copy

**Screen 1 — The problem**

Headline: “Know what you mean. Struggle to say it?”

Supporting text: “Stop rambling, losing your train of thought, or finding the right words too late.”

Primary CTA: “Next”

**Screen 2 — The outcome**

Headline: “Think Sharp. Speak Bold.”

Supporting text: “Learn to express your ideas clearly, disagree respectfully, and hold your ground.”

Primary CTA: “Next”

**Screen 3 — The method**

Headline: “Get better by speaking.”

Supporting text: “Take on a real scenario. Record your response, listen back, make one improvement, and try again.”

Primary CTA: “Start practicing”

No questions, assessments, accounts, permission prompts or purchase steps are included.

## UI, navigation and state

- Exactly three HorizontalPager pages use the existing forest-green/ivory palette, serif headline, body typography, brand mark, line glyphs, 24dp spacing and primary button component.
- Each page contains one headline and one supporting sentence. Small purpose-related glyphs replace the old explanatory panels. No generic imagery, decorative animation or dense copy.
- Next/swipe advance; a secondary 48dp Back control and Android system Back move to the previous page. A three-dot indicator exposes “Welcome page N of 3” to accessibility services. Headlines carry heading semantics, and storage errors use a polite live region.
- Content scrolls independently of the bottom CTA. No fixed text heights; the primary action stays outside the pager viewport. Pager movement supplies the only transition.
- OnboardingPages is bundled platform-neutral copy. Pager state is saveable presentation state; advancing pages never marks onboarding complete or writes session progress.
- `finishWelcome()` can now complete without a selected goal. It preserves the existing goal preference. The AtomicFile write must succeed before the controller publishes `onboardingCompleted = true` or navigation occurs. Failed writes remain on screen 3 with a retryable message.
- Practice is now an explicit `AppScreen.Practice` destination, using the existing saveable typed navigation architecture. The previous parallel `inPractice` mutable boolean is replaced by a derived property of the destination. Existing Day 1 renders through the same PracticeFlow; it is not rebuilt.
- “Start practicing” opens the current Day 1 technique/scenario immediately, without visiting Today or requesting microphone permission. Permission remains attached to the existing recording action.
- Rotation/saved-instance recreation retains the current page or practice destination. A fresh cold launch of unfinished onboarding restarts at screen 1. A completed cold launch skips onboarding and uses the existing Today/Continue practice experience; saved lesson progress is retained.
- Pager navigation is disabled while scrolling/completing. A completion guard prevents duplicate actions while the completion callback runs.

## Files created/modified in this task

Created:
- `shared/src/commonMain/kotlin/com/saysharp/shared/engagement/Onboarding.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/engagement/OnboardingTest.kt`
- `docs/R1-01-ONBOARDING.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/WelcomePager.kt` — exact three-page copy/layout, Back/Next/CTA, accessibility and completion guard; milestone components below it remain unchanged.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/SaySharpApp.kt` — typed Practice destination and direct completion navigation.
- `shared/src/commonMain/kotlin/com/saysharp/shared/engagement/EngagementController.kt` — renamed completion flag and default goal argument; other engagement behavior retained.
- `app/src/main/java/com/saysharp/saysharp/storage/AndroidEngagementStore.kt` — persists/reads the named onboardingCompleted flag in the existing local preferences file.
- `shared/src/commonTest/kotlin/com/saysharp/shared/engagement/EngagementControllerTest.kt` — assertions use the renamed flag.
- `app/src/androidTest/java/com/saysharp/saysharp/EngagementFlowTest.kt` — onboarding copy/navigation, restart/restoration, save-failure, large-font and partial-session checks; existing analytics/milestone regression coverage retained.

The checkout already had uncommitted SS-070/SS-072 work. Those changes were preserved; this inventory does not attribute their files to R1-01.

## Verification

Executed using Android Studio's bundled JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: BUILD SUCCESSFUL. 72 shared host tests plus 4 Android unit tests: 76 total, zero failures/errors/skips. This includes four new OnboardingTest tests and all existing Day 1 shared regression tests. Debug APK and instrumentation APK compile. Lint reports zero errors and 25 warnings for dependency/resource/project housekeeping. `git diff --check` passes.

New host checks verify exact three-page copy, incomplete restart semantics, questionnaire-free completion while preserving other preferences, and failed-write/retry/restart behavior.

The nine EngagementFlowTest instrumentation cases cover exact content and headings, Next/Back/swipe, direct Day 1 navigation with zero recording permission requests, fresh/complete relaunch, failed completion retry, partial Day 1 preservation, 320×568dp at 2× font scaling, saved-instance restoration, settled-page analytics and existing milestones. They are **compiled, not executed**. Existing AndroidEngagementStore instrumentation round-trip coverage also compiles but was not run.

`adb devices` returned no connected device; `emulator -list-avds` returned no AVD. Therefore native UI, font scaling, TalkBack, system Back and Android file persistence still require device verification. No install, release/signing change, Notion update, GitHub push or Play submission was performed.

## Manual Android QA checklist

1. On a fresh development installation, launch offline. Confirm screen 1 appears and no microphone, account, notification, assessment or payment prompt is shown.
2. Verify all three headlines and supporting sentences exactly match this document. Ask a first-time tester to describe the problem, skills and method after reading them, ideally within 45 seconds; this comprehension target has not been measured yet.
3. Use Next, visible Back, swipe in both directions and Android system Back. Confirm the indicator follows the settled page, no fourth page exists and rapid taps do not skip past the flow.
4. Force-stop/close halfway and cold-launch again: expect screen 1 and incomplete onboarding. Rotate on screen 2: expect screen 2 to remain selected.
5. Tap Start practicing: expect the existing Day 1 “Lead with your point” technique/scenario immediately. No Today detour or microphone prompt. Verify the request appears only when the user explicitly starts recording.
6. Relaunch after completing onboarding: expect onboarding to be skipped. Continue Day 1 from the saved stage. Repeat after an actual device process recreation.
7. Complete the existing Day 1 loop offline. Play First attempt, Retry and Fresh question, then restart and confirm all remain retrievable. Onboarding must not alter attempts, assessment or completion history.
8. Test the smallest supported phone/landscape, large display size and 200% font scaling. Scroll long text; verify no clipped/overlapping controls and a reachable CTA on every page.
9. Enable TalkBack. Check headline navigation, reading order, page-count announcement, button names and focus across page changes. Confirm dots/glyphs do not create noisy empty focus targets.
10. Use the existing Settings intro replay explicitly: closing/reaching the final CTA should return to Settings without restarting lesson progress. Relaunch must not replay the intro automatically.

Remaining issues: no known build or host-test failures; instrumentation/device accessibility, visual rendering, native persistence and the 45-second comprehension target remain unverified. iOS is not configured or tested. No other lessons were built.
