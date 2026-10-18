# SS-070 — R1 architectural foundation

Historical SS-070 audit and verification. See [SS-072-DAY-1.md](SS-072-DAY-1.md) for the subsequent Day 1 implementation, androidMain adapters, current state and verification.

## Audit and decisions

The repository already had working R1 restructuring. It contains `app` (Android) and `shared` (KMP with Compose Multiplatform). Shared code currently has Android and host-test targets; there is no configured iOS target or populated androidMain implementation. Android adapters live in `app/src/main`, which is a valid platform boundary. No extra Gradle modules or iOS toolchains are needed now.

Keep:
- Separated Today/Programme, Practice, Progress, Settings and reusable UI files; typed destinations saved by name.
- Authored offline programme, vocabulary recalls, familiar/fresh checkpoints and Done/Not yet/Unsure enum.
- Atomic private JSON storage with read-failure protection, saved recording identifiers, completed-day history and version 1/2 readers.
- Android AAC recording, async playback, resource cleanup, audio focus and private-file validation.
- Existing regression suite, no-op-safe telemetry, platform permission callbacks and optional reminders.

Replace incrementally:
- PracticeController previously exposed multiple Compose mutableState properties and combined transition rules, persistence and audio orchestration. The foundation removes its Compose dependency, extracts the pure transition, and keeps orchestration rather than rebuilding it.
- AudioRecorder previously included playback. Separate recorder/player interfaces now allow independent fakes and future adapters. The current Android implementation intentionally owns both to preserve its audio-focus coordination.
- MainActivity previously constructed dependencies inline. A small activity-owned factory now constructs them explicitly.
- Compose previously ran the recording timer. The coordinator now owns a cancellable timer using its injected scope.

Still legacy: EngagementController and ReminderController use Compose state; navigation is local saveable presentation state; several UI labels still read the existing Programme catalogue; storage I/O is synchronous. These are explicit incremental boundaries, not a claim of a completed app-wide redesign.

## Target structure

Keep two modules:

| Location | Responsibility |
| --- | --- |
| shared/commonMain/session | Session definitions, progress/repository contracts, immutable observable session snapshot, pure transition function |
| shared/commonMain/practice | Coordinator: commands → domain transition/audio → atomic save → StateFlow publication |
| shared/commonMain/lesson | Existing authored lesson catalogue and listening guidance |
| shared/commonMain/platform | Recorder/player ports and opaque recording reference |
| shared/commonMain/ui | Compose rendering, navigation and ephemeral presentation state |
| app/src/main | Android audio, storage, permissions, notifications, Firebase and composition root |
| shared/commonTest | Domain, state stream and coordinator tests with fakes |
| app/src/test / androidTest | Android unit checks, storage/platform integration and UI flows |

A future iOS host can supply storage, audio, permissions and scheduling adapters; the domain and StateFlow contracts contain no Android API. This task does not configure or verify iOS compilation.

## Contracts implemented

- SessionDefinition: authored teaching content, prompts, observable criterion and ordered stages.
- SpeakingPrompt: stable content identifier, text and recording limit.
- Attempt: one kept recording for a role, optionally paired with the user's assessment. No transcript, objective score or invented retake history.
- RecordingReference: opaque file identifier and duration; RecordingRef is a compatibility alias.
- SelfAssessment: reuse the existing Done/NotYet/Unsure enum in lesson. No duplicate enum or serialization conversion.
- SessionProgress: alias of the existing immutable PracticeSession while the storage cutover is deferred. Includes current stage, kept recordings, assessments and completed history. Do not mistake this alias for a new data format.
- SessionState: one runtime snapshot containing progress, recording/playback status, elapsed time and recoverable message.
- SessionRepository: bundled synchronous definition lookup; BundledSessionRepository adapts the existing 14 lessons rather than authoring another catalogue.
- ProgressRepository: atomic load/save of the existing progress model. SessionStore extends it so AndroidSessionStore remains usable unchanged.
- AudioRecorder and AudioPlayer: separate ports. PracticeAudio is a transitional combined interface for the existing adapter/test fixtures; the production coordinator receives recorder and player explicitly.

## State, transitions and use cases

The coordinator exposes read-only StateFlow<SessionState>. UI collects once at SaySharpApp and distributes the immutable snapshot to screens through a composition local. UI sends commands; it cannot write the flow. Compatibility getters are read-only externally and backed by the same flow.

advanceSession is a pure use case: it checks the current stage's recording/assessment requirements, then returns new progress. It accepts Not yet and Unsure; a positive rating is never required. Completion archives the day once and preserves prior history. sessionStages supplies the UI and coordinator from the same definition, including the missing-baseline recovery entry. Existing stored stage names remain unchanged.

Persistence succeeds before new progress is published. Failed saves retain the prior progress and earlier recordings. Errors loading existing data block writes and keep the source file intact. Messages remain simple recoverable UI text; raw exceptions are only passed to the existing sanitised telemetry boundary.

## Audio, lifecycle and dependency injection

AndroidAppDependencies is owned by MainActivity. It creates the existing audio/storage/reminder/engagement adapters, a main-thread SupervisorJob scope and the practice coordinator. It uses application context for native adapters and never makes a global recorder singleton.

Recorder/player calls and callbacks are serialized on the main thread. The adapter owns audio focus, codecs, file operations and native listeners. The coordinator owns progress, save decisions and its cancellable timer. Backgrounding stops/saves an active attempt using the established behaviour and stops playback. Activity destruction repeats safe cleanup and cancels the scope. Rotation restores persisted state rather than retaining a recorder across Activities. Timers use the adapter's monotonic clock, not the number of timer ticks.

Android Activity Result APIs continue to own permission requests. Firebase remains in app; shared logic depends only on the existing telemetry boundary. No DI framework, global service locator, one-class-per-command hierarchy or new navigation dependency is introduced.

## Local persistence and data policy

The existing AtomicFile JSON schema and app-private audio directory are reused. No new database, reset, deletion-at-startup, destructive migration or upgrade flag is implemented. applicationId, versionCode/versionName and signing configuration are unchanged. No app is installed, uploaded or published by this task.

Synchronous storage is an explicit limitation for this small local snapshot. A later measured performance improvement can move operations behind a serialized IO boundary; do not introduce concurrent read-modify-write writers. The separate R1 reset task must implement warning, explicit confirmation and upgrade tests before touching older-version data.

## Verification and remaining Day 1 work

Focused tests exercise pure transition gates, definition matching, checkpoint recovery, completion idempotence, attempt/assessment mapping, StateFlow publication after persistence, unreadable-data protection, retake save failure, and timer/playback lifecycle. Existing full-programme tests continue to cover all 14 days and restoration. Android instrumentation tests are compiled, not treated as executed.

The existing offline Day 1 flow remains connected through the new contracts. Before calling the redesigned Day 1 complete:
- Run real-device recording/playback/permission/background/relaunch and UI checks against the StateFlow wiring.
- Finish the requested learning order: the current First Check screen still displays examples alongside the assessment, rather than a distinct corrective-example step after the assessment. This is intentionally not changed in a foundation-only task.
- Resolve the separate comparison UX requirement about mandatory second assessment.
- Adopt explicit screen-state parameters and migrate reminder/engagement presentation state as their feature tasks proceed.
- Implement storage reset only in its separately authorised warning/confirmation/upgrade task, not as a dependency of running this foundation.

Streaks, Lite Day, Pressure Practice, billing, new lessons, new screen designs, and Play Store operations are out of scope.

## Executed checks — SS-070

Command: `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` with Android Studio's bundled JDK.

Result: BUILD SUCCESSFUL. 55 shared host tests (including 10 new SessionFoundationTest cases), 4 Android unit tests; zero failures/errors. Debug and instrumentation APKs compile; Android lint passes; `git diff --check` passes. Device inventory is empty and no AVD is configured, so instrumentation and real microphone/playback tests did not run. iOS and release-signing builds did not run. No GitHub push or Play upload was performed.

## File inventory

Created:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionContracts.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/BundledSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionTransition.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionState.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/SessionFoundationTest.kt`
- `app/src/main/java/com/saysharp/saysharp/AndroidAppDependencies.kt`
- This architecture report.

Modified:
- `PracticeController.kt`, `PracticePlan.kt`, `PracticeSession.kt`, `platform/AudioRecorder.kt`: flow-backed state, pure transition integration, repository ports and split audio contracts.
- `SaySharpApp.kt`, `HomeScreens.kt`, `PracticeScreen.kt`, `ProgressScreen.kt`, `ScreenComponents.kt`: collect/read the immutable state and use definition prompts in the practice flow. Screen layouts are retained.
- Android `MainActivity.kt`, `audio/AndroidAudioRecorder.kt`: composition root/lifecycle ownership and combined compatibility adapter declaration.
- `PracticeControllerTest.kt`, `TelemetryTest.kt` and Android UI test audio fixtures in EngagementFlowTest, FeedbackFlowTest, PracticeFlowTest, ReminderFlowTest, StoreScreenshotCaptureTest: implement PracticeAudio explicitly; assertions retain their existing behaviour.
- `app/build.gradle.kts`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`: explicit coroutines runtime dependency for the Android composition root and coroutine-test dependency for deterministic foundation tests.

Removed: no files. AndroidSessionStore, JSON serialization, Android permissions, lesson content, app ID and version fields were not changed.
