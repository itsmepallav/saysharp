# SaySharp

Android-first Kotlin Multiplatform speaking-practice app. No AI, accounts, or backend in the initial scope. Users assess their own recordings against concrete behaviours.

## Current status — free v0 programme

V0.5 adds a three-page swipeable welcome, a saved speaking goal, outcome-focused copy, milestone celebrations and a real-life-win confirmation to the v0.4 visual redesign. See [design notes](docs/DESIGN.md).

V0.5.1 adds a concise 14-day speaking goal and one practical outcome per session, shown consistently across the programme and practice screens. Outcomes are goals users can assess through their recordings, not guaranteed gains.

V0.5.2 makes welcome slides and chapter benefits more direct and concise, while preserving the drills, private recordings and user-led assessment.

V0.5.3 introduces the SaySharp speech-bubble “S” mark for the launcher and in-app logo, with adaptive, monochrome, round and density fallback icons. Reusable artwork lives in `assets/brand`; regenerate it with `tools/generate_launcher.py` using Python and Pillow.

V0.6 integrates Firebase Analytics and Crashlytics for onboarding, practice funnels, recordings, self-assessment, reviews, reminders and operational failures. See [event catalogue and Firebase console setup](docs/ANALYTICS.md).

V0.6.1 adds Settings → Feedback & ideas: structured general feedback with optional ratings, feature votes and problem categories. Responses queue through Firebase Analytics without sending personal notes or audio. No Google Form or free-text inbox is configured.

- `app`: Android launcher and platform integration entry point.
- `shared/src/commonMain`: Compose Multiplatform screens, lesson models, a shared session controller, and all 14 sessions.
- `shared/src/commonTest`: progression, recovery, limits, deletion, and content checks.
- Android microphone permission, AAC recording, asynchronous playback, audio focus and resource cleanup.
- Welcome → baseline → first lesson → attempt → self-check → retry → comparison → fresh question → completion.
- Atomic local session storage, process-restart recovery, saved attempts, self-assessments and a real-life note.
- Progress playback, per-day assessments and notes, and confirmed deletion of all practice, including orphan audio files.
- Distinct day 7/14 checkpoints with baseline and unfamiliar prompts; vocabulary use and later retrieval drills.
- Settings with optional daily reminders, a 24-hour time input, notification permission handling, Android settings link and a test notification.

All 14 sessions are available sequentially, free in v0, at the user's own pace. Narrated examples are not implemented; examples are written. Completion requires the recording and assessment steps and final confirmation; no improvement score is invented. Topic scenarios are fictional exercises, not sourced news briefs. Expressions return two and seven session numbers after introduction, rather than on calendar dates; all scheduled recalls fit within the programme.

Completed days retain their recordings, assessments and notes while the original baseline remains available for comparison. Existing version 1 Day 1 sessions are migrated without replacing their recording references or note.

Audio and metadata live in Android's private `noBackupFilesDir`, with application backup disabled. Internet access is used for Firebase usage and crash diagnostics; recordings and note text are not uploaded. Recordings stop when the Activity leaves the foreground. Retakes preserve the previous file until the replacement and metadata save successfully. Recordings shorter than one second are rejected. An unreadable session is preserved and blocks new saves; explicit Delete all practice allows a reset.

## Daily reminders

Open Settings, choose a time and enable Daily reminder. Android 13+ asks for notification permission only when enabling the reminder. Reminders default to off, are stored locally with backup excluded, and continue until disabled, including after completing the programme. Send test notification previews the notification without enabling daily reminders. Tapping a notification opens SaySharp.

Android uses one inexact idle-compatible alarm, replaced when the time changes and cancelled when disabled. No exact-alarm access is requested. Delivery can be delayed by battery restrictions or Doze; force-stopping the app prevents delivery until it is opened again. The next alarm uses local wall-clock time and is rescheduled after reboot, timezone/clock changes and app updates. If notifications or the channel are blocked, reminders pause; reopening SaySharp after restoring permission reschedules them.

## Welcome and milestones

New installs show three welcome pages, navigable by swiping or Next, with Skip intro available. Users choose a speaking goal on the third page; it personalises the home headline, while the curriculum remains the same. Welcome completion and goal are stored atomically in a separate private, backup-excluded file. Existing practice remains intact. Settings offers Change my goal and See welcome again.

Completing sessions 1, 3, 7 and 14 earns milestones. Celebrations are acknowledged once across restarts; existing history shows only the latest unacknowledged milestone, not a queue of old popups. Milestones require all preceding sessions to be completed. Progress includes a milestone collection, and explicitly saving a changed, nonempty real-life note shows a confirmation. Completing sessions is not treated as proof of speaking ability. Clearer-answer messaging only reflects the user's own comparison. No unsupported prevalence statistics or guaranteed improvement deadlines are used.

## Build

Use Android Studio's bundled JDK (or another compatible JDK) and the existing Android SDK 37 setup.

```sh
./gradlew :app:assembleDebug :shared:testAndroidHostTest :app:testDebugUnitTest
```

For tests on a personal Android device, keep the phone unlocked and install/run the instrumentation directly. This leaves the app installed and avoids the Gradle connected-test task's uninstall cleanup:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
```

Use `:app:connectedDebugAndroidTest` only on a disposable emulator/test device. Its cleanup removed the installed app during the v0.4 run; previous local-only data may have been lost. The app was reinstalled afterwards. Do not use that task on a phone containing recordings to preserve.

Screen tests use simulated audio and do not open the microphone. Before shipping, manually test actual speech capture/playback, microphone denial, audio-focus interruption, backgrounding/rotation, storage failure, and the 45/60-second limits on real hardware. Runtime Android permissions and audio adapters are separate from the shared UI.

### Verification of this implementation

- Android debug and release APKs: build passed.
- Shared tests: 45 passed, including 6 telemetry checks and 4 feedback checks for payloads, validation, optional ratings and unavailable SDKs.
- Android JVM tests: 4 passed, including reminder scheduling and custom crash-report sanitization.
- Android lint: passed with warnings for dependency updates, unused template colours and optional KTX style suggestions.
- V0.5 direct instrumentation run: 15 device tests passed, including swipe/Next/Skip onboarding, goal persistence, milestone dismissal/collection, the small-screen/large-text recording action, practice flows, reminders and native storage. Welcome and home screens were inspected visually on the phone.
- V0.6 direct instrumentation run: 17 device tests passed, adding settled-page analytics and Firebase project/SDK initialization with a synthetic non-fatal check. Analytics debug collection was enabled and the Crashlytics report was successfully queued to DataTransport. Firebase console receipt is not independently verified.
- V0.6.1 debug/release builds, lint and all 49 shared/Android JVM tests passed. The update is installed. Device UI checks, including the three new feedback flows, remain pending: the connected phone was securely locked and the attempted run was stopped without uninstalling the app.
- Real notification delivery/tap, permission prompts, channel blocking, reboot and timezone rescheduling still require device verification.
- Actual microphone capture/playback: manual hardware verification remains outstanding.

If the shell cannot locate Java on this Mac:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :shared:testAndroidHostTest
```

The existing Gradle daemon configuration selects JDK 25. Gradle 9.5.0 and AGP 9.3.3 are retained from the supplied project. Kotlin 2.3.20 and Compose Multiplatform 1.10.3 are pinned in the version catalog. Android uses AGP's built-in Kotlin support; do not add the legacy Kotlin Android plugin.

## iOS boundary

No iOS target, framework task, or Xcode project is configured yet. Shared UI and lesson code contain no Android APIs. When iOS work begins, add device/simulator targets and a framework, an iOS host, and native audio/storage/notification implementations. Shared code is prepared for that work; iOS compatibility has not yet been compiled or tested.

## Product constraints

- Free 14-session programme in v0. No Pro tier, paywall, billing or entitlements.
- Self-assessment: Done / Not yet / Unsure. Never gate progress on a positive answer.
- No AI scores, subscriptions, social leaderboards, or speech transcription.
- Preserve baseline; compare repeat and unfamiliar prompts on days 7 and 14.
- Keep recordings local; explain no sync and uninstall loss.
- Baseline and lesson attempts are stored locally with backup excluded.

See [the implementation roadmap](docs/ROADMAP.md).

## Repository setup

Use JDK 21 and an Android SDK matching the Gradle configuration. Set `sdk.dir` in your untracked `local.properties` (or use Android Studio's SDK setup). Download the Android Firebase configuration for `com.saysharp.saysharp` from your Firebase project and place it at `app/google-services.json` before building; this local configuration is intentionally not committed. Signing keys, release packages and machine configuration are also excluded.

```sh
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The current source includes the first post-launch R1 restructuring slice. It separates screens and typed navigation, shares practice progression between controller and UI, and refines onboarding, Today, listening guidance and retries. Saved session versions and recording identifiers are preserved. See `docs/ROADMAP.md` for remaining R1 work. Verification: 45 shared tests and 4 Android unit tests passed; debug and instrumentation APKs compile; Android lint passes. Device instrumentation and visual/audio checks remain pending.
