# SS-072 — Offline Day 1

## Scope and audit

The checkout already contained SS-070's shared definitions/repository ports, pure transitions, one StateFlow controller, atomic Android JSON storage, private AAC audio and explicit activity-owned dependencies. These were reused. The previous Day 1 route included an extra baseline, exposed examples before self-check, and required extra comparison ratings. It did not persist recording-in-progress or listening status, and coordinator callbacks could affect later operations.

This task replaces only Day 1's content/route and closes those demonstrated gaps. Existing later lessons, reminders, onboarding, optional journal and milestones remain; no new later lessons or engagement features were implemented. No migration/reset, destructive upgrade, signing/version change or Play operation was performed.

## Implemented experience

1. Technique: lead with your recommendation, then one reason and one useful detail (approximately 20 seconds to read). A client-demo scenario supplies context without an answer.
2. First attempt: spontaneous response, aiming for 30–60 seconds; maximum 60 seconds. Early stopping is allowed, with recordings under one second rejected as unusable.
3. Listen and self-check: replay the first ten seconds (or the whole file if shorter). Only successful playback completion unlocks Done / Not yet / Unsure. The sole criterion is whether the first sentence stated the main point. Unsure reveals a brief concrete hint.
4. Contrast and correction: weak and strong openings are revealed after the self-check. The instruction is to replace background with the actual recommendation in the opening sentence.
5. Retry: the identical client-demo situation, a separate recording, without overwriting First. Both attempts can be played on the comparison screen; no extra rating is mandatory.
6. Transfer: a different residents' budget decision, without an answer scaffold. Save the third recording and complete Day 1.
7. Completion and Progress: all three kept attempts, self-check and completion history persist locally. No mandatory typing, AI grading or numerical scores.

The forest/ivory styling, scrollable pages, fixed action bar and 48dp playback controls are retained. Section titles have heading semantics; recoverable errors use a polite accessibility announcement. Runtime visual/TalkBack verification remains outstanding.

## Architecture and recovery

- `shared/commonMain`: typed DayOneSession, SessionDefinition, SessionProgress, pure ordered transitions and PracticeController. The sole immutable runtime snapshot is SessionUiState (SessionState is an alias to the same model).
- `shared/androidMain`: AndroidAudioRecorder implements the recorder/player ports using MediaRecorder and MediaPlayer. AndroidMicrophonePermission encapsulates permission/settings policy; MainActivity owns its activity-result launcher and lifecycle callbacks.
- `app`: existing AtomicFile JSON storage and activity-owned dependency wiring. No additional modules or DI framework.
- Writes succeed before progress changes are published. A recording-in-progress marker is saved before opening the microphone. Failed marker persistence prevents recording from starting.
- Saved references use separate recording slots and UUID filenames. An explicit same-slot retake replaces only that slot after successful storage; the previous file survives failed replacement. Replacing a recording clears its previous listening/assessment state.
- Backgrounding saves a playable partial recording when MediaRecorder can finalize it, and stops playback. Audio-focus loss or platform error cancels an interrupted recording with a recovery message; an earlier kept attempt survives.
- Process death during an unfinished recording restores the last kept reference and shows an interruption message. An unfinished file is not promoted to a usable attempt. Such a file can remain orphaned in private storage; automatic orphan cleanup is deferred.
- Operation generations ignore stale recorder/player callbacks. Stage-bound UI advance commands ignore repeated taps from an old screen. Permission requests are not launched twice, and a result cannot start recording after its activity/stage has gone away. Permanent denial routes the next recording request to app settings.
- Listening status is persisted only after successful playback completion; interrupted/failed playback cannot unlock the self-check. No assumption is made about the user's actual comprehension.
- Existing Firebase operational telemetry is retained; lesson execution does not require it. No audio files, transcripts, examples of user speech or free-form journal text are sent. There is no new backend or upload path.

Small synchronous JSON writes remain on the main thread, as in SS-070. iOS adapters/targets are not implemented or verified. Recording recovery and native audio quality still need hardware verification.

## Files changed in SS-072

New:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/DayOneSession.kt`
- `shared/src/androidMain/kotlin/com/saysharp/shared/platform/android/AndroidMicrophonePermission.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/DayOneSessionTest.kt`
- `docs/SS-072-DAY-1.md`

Moved and modified:
- `app/src/main/java/com/saysharp/saysharp/audio/AndroidAudioRecorder.kt` → `shared/src/androidMain/kotlin/com/saysharp/shared/platform/android/AndroidAudioRecorder.kt` (package/imports; playback focus loss reports interruption rather than successful listening).

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionContracts.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/BundledSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionTransition.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionState.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeSession.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeController.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/PracticeScreen.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/ProgressScreen.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/ScreenComponents.kt`
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/SaySharpApp.kt`
- `app/src/main/java/com/saysharp/saysharp/MainActivity.kt`
- `app/src/main/java/com/saysharp/saysharp/AndroidAppDependencies.kt`
- `app/src/main/java/com/saysharp/saysharp/storage/AndroidSessionStore.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/practice/PracticeControllerTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/SessionFoundationTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/telemetry/TelemetryTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/PracticeFlowTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/SessionStoreTest.kt`
- `docs/ARCHITECTURE-R1.md` (links this newer report).

Earlier uncommitted SS-070 changes are preserved. The file list above identifies this task's changes rather than attributing the entire Git diff to SS-072.

## Automated verification

Executed from the repository root using Android Studio's JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: BUILD SUCCESSFUL; 68 shared host tests plus 4 Android unit tests, zero failures/errors/skips. Thirteen new DayOneSessionTest tests cover the offline loop with recreation at partial stages, distinct files/history, persisted listening, permission denial, synchronous and stale callbacks, repeated taps, background save, process-death marker, failed replacement and failed storage/playback. Existing regression assertions were updated for the three-recording Day 1 route.

Debug and instrumentation APKs compile. Lint: zero errors, 25 warnings (dependency updates and existing resource/project housekeeping). `git diff --check` passes. Build output: `/tmp/ss072-final-build.log`; JUnit XML: `shared/build/test-results/testAndroidHostTest/` and `app/build/test-results/testDebugUnitTest/`.

Android UI/storage instrumentation tests were updated and compiled, **not executed**. `adb devices` reported no device, and `emulator -list-avds` returned no configured AVD. No actual microphone, speaker, permission dialog, process recreation or visual device check has passed yet. No release build, iOS build, install, GitHub push or Play upload was performed.

## Manual Android verification still required

Use a development device with this new version. Build/install with `./gradlew :app:installDebug` when ready. Run instrumentation with `./gradlew :app:connectedDebugAndroidTest` and retain the report.

1. **Offline lesson:** enable airplane mode before launch. Enter Day 1. Verify technique and client-demo context, with no example answer visible.
2. **Permission:** deny microphone access. Confirm a recoverable message and unchanged recording step. Try again and grant access. Also test permanent denial: next request should open app settings; grant permission, return and tap record again. Rapid taps must not launch multiple requests.
3. **Real microphone:** speak for 30–45 seconds and stop. Check that the saved file plays intelligibly through speaker and headphones. Try a sub-second recording and a 60-second automatic stop.
4. **Self-check:** enter Listen and check. Choices should appear after the opening replay finishes. Stopping playback early or interrupting it must not count. Select Unsure and verify the concrete hint. Continue; only now should the weak/strong example appear.
5. **Retry separation:** record the same client-demo situation with a clearly different spoken opening. On Compare, play First attempt and Retry independently. Verify each contains the expected audio. An explicit Retry retake must not change First.
6. **Normal background/relaunch:** background while recording for several seconds, return, and confirm saved-partial or recovery status. Relaunch at FirstCheck, correction, Retry, Compare and transfer. Confirm the same stage, references and self-check are retained; no duplicate completion.
7. **Force-stop recovery:** while recording a retake, force-stop the app (`adb shell am force-stop com.saysharp.saysharp`). Relaunch: verify interruption text, recording stopped, and the previous kept attempt remains playable. The unfinished retake need not survive.
8. **Audio interruption/failure:** interrupt playback with another app taking audio focus. Confirm playback stops, a recoverable message appears, and the first self-check remains locked until a successful replay. Interrupt recording similarly and verify the earlier file remains safe.
9. **Transfer/completion:** answer the residents' budget prompt without displayed scaffolding. Finish and open Progress. Verify three different recordings, the chosen self-check and completed Day 1. Restart and play First, Retry and Fresh question again, still in airplane mode.
10. **Accessibility/rapid input:** repeat with large text and TalkBack; ensure all content scrolls, actions remain reachable, playback roles are announced and errors are spoken. Repeated Start/Stop/Continue taps must not skip required stages or create duplicate recordings.

## Next separate R1 prompt

“Verify SS-072 on a connected Android development device. Run connectedDebugAndroidTest and the manual checklist in docs/SS-072-DAY-1.md. Fix only reproducible Day 1 microphone, permission, playback, lifecycle, resume or accessibility failures, and add regression coverage where practical. Record device/Android version and observed results. Do not expand the programme, implement SS-071/073/074, alter signing/versioning or publish anything.”
