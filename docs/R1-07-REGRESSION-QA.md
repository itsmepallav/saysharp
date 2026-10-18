# R1-07 — Regression QA (SS-023)

## Test-first risk matrix — recorded before new tests

| Priority | Risk | Existing evidence to re-run | Required device / added evidence |
|---|---|---|---|
| P0 | Lost or wrong lesson audio after save failure/restart | PersistenceRecovery, filesystem RecordingJournal, catalogue restart traversal | Native AtomicFile round trips; isolated native audio/journal test |
| P0 | Invalid/duplicate completion or cross-day state | SessionFoundation, SessionCatalogue, ProgrammeOverview | Whole curriculum Compose flows, checkpoint and programme tests |
| P0 | Private speech/reflections uploaded | Telemetry + CrashSanitization tests; source audit | Source verification only unless actual traffic captured |
| P1 | Recorder/player start/stop, stale callbacks, rapid taps | DayOneSession, RetryComparison, PracticeController | Native recorder/player against disposable cache, real playback failures |
| P1 | Pending-save Resume unreachable | PersistenceRecovery + PendingSaveFlow | Execute actual Compose test on Samsung |
| P1 | Essential controls unreachable / blocked by scale | SelfAssessment, RetryComparison UI tests | Execute 2x font/small viewport checks; manual TalkBack remains separate |
| P1 | Incorrect resume/navigation/example gating | Catalogue + onboarding/progression suites | Execute existing instrumentation before modifying assertions |
| P2 | Main-thread storage stalls | Duplicate-write suppression, synchronous I/O source review | Measure AtomicFile + journal timings on device, state measurement limits |
| P2 | Layout, labels, subjective content quality | Content tables and semantics assertions | Screenshots/large text plus human listening/content/TalkBack checklist |

## Initial audit

Read R1-04, R1-05 and R1-06 reports. Existing architecture uses one controller StateFlow, typed 14-day catalogue, pure transitions, local AtomicFile progress and finalized-audio journal. Reuse these boundaries. Test fixtures mostly use fake audio/in-memory stores: this proves coordinator/UI behavior, not native audio quality or process death. Native store tests use unique cache directories. Hard-killed unfinished AAC remains explicitly unrecoverable.

Connected device: Samsung SM-S931B, Android 16 / API 36. Installed app and local debug APK share SHA-256 signing certificate `97edabf3ee96c727a63a9f3ec38ba0d4d4c8f97a93cb4e0ed105de5834c57240`. Use replacement install and direct instrumentation, without uninstall/clear, to retain existing app data. Do not run Gradle connected cleanup that may uninstall the personal app.

Results pending; no test pass claimed at matrix creation.

## Coverage and architecture review

| Area | Executed automated evidence | Boundary / remaining manual evidence |
|---|---|---|
| Onboarding | Three pages, back/swipe, failed completion save, recreation, completed launch, no microphone requests | TalkBack focus and physical first-install permission dialog |
| Today / Programme | Fresh/current/resume, completion, phase transition, Day 14 end state, 2x font | System Back and task switching with actual MainActivity |
| Curriculum | Shared sequential traversal of all 14, restart after every stage, 40 distinct retained files; Compose ordinary-loop and both checkpoints | Audio in curriculum UI tests is fake; human content review remains |
| Self-check | Replay gate, all three choices, changing/unchanged choice, stale context, contextual Unsure hint, persisted selections | Spoken selected-state announcements |
| Correction / retry | Hidden examples, same scenario, separate IDs, stale callback rejection, optional comparison, switching A/B, fresh transfer | Actual spoken A/B intelligibility and output routing |
| Completion / reflection | Required recordings/replays/assessment, no premature completion, sequential history, duplicate completion, blank/typed/skip/failure reflection | Real-world usefulness is not measured |
| Persistence | Actual Android AtomicFile round trips and interrupted write; actual temporary-filesystem journal; fake failed metadata commits and retry | Real process-kill sequence and power-loss are not simulated by object recreation |
| Native audio | Actual microphone AAC, journal recreation, real AtomicFile/controller recovery, valid playback completion, corrupted-file failure, valid replay afterward | Calls/focus loss, Bluetooth, permission UI, background capture and subjective quality |
| Privacy | Metadata-only telemetry assertions, sanitized exception tests and source review | No live network interception / Firebase payload capture |

The architecture remains small: two Gradle modules, one StateFlow-backed session controller, typed definitions/transitions, one repository-backed progress projection and Android audio/storage/permission adapters. No second progress state or navigation system was added. Capture/playback generations reject stale callbacks. UI advance commands capture the expected stage; capture and pending-save guards reject repeated actions. Save precedes publication. Completed history is retained when individual audio references disappear.

Main-thread synchronous store/journal operations remain a performance risk, not a reason for an unproven asynchronous rewrite. StateFlow elapsed-time updates occur at up to 10 Hz while recording; no recomposition trace or heap/leak profiling was performed. Backgrounding stops the ticker/player through activity-owned dependencies. Media operations release the recorder/player and audio focus in normal/error paths; native corrupt-file recovery exercises a subsequent valid playback. Focus-loss and hard process-death behavior still require the physical checklist.

## Fourteen-day regression

Shared catalogue tests verify stable IDs, correct phase, populated content, a single listening question, same First/Retry prompt, distinct transfer, post-assessment-only examples, checkpoint-specific gates and reasonable Day 12 pushback that allows revising a position. The full sequential controller traversal recreates the controller after every stage, verifies unchanged prior reviews and retains 40 unique recordings (12 × 3 ordinary + 2 × 2 checkpoint).

Device curriculum tests now finish every ordinary lesson through transfer, assert three different lesson-prefixed IDs in the completed review and finish the optional reflection step without typing. Both Day 7 and Day 14 run their independent familiar/fresh route, reject finishing before replay/assessment, then complete with two recordings. These UI fixtures preload preceding completion history; the shared traversal, not this fixture, proves sequential history creation.

| Days | Phase / route | Coverage |
|---|---|---|
| 1–6 | Speak Clearly / ordinary | Shared full traversal + device full ordinary loop |
| 7 | Speak Clearly / checkpoint | Shared full traversal + device checkpoint route |
| 8–13 | Speak With Conviction / ordinary | Shared full traversal + device full ordinary loop; Day 12 pushback assertions |
| 14 | Speak With Conviction / checkpoint | Shared full traversal + device checkpoint route + programme-complete UI |

Two device-rendered artwork captures (Programme and A/B comparison, 400×680 dp fixture) were visually inspected: readable hierarchy and reachable controls with no obvious overlap in those captures. This is limited visual evidence, not a full layout or accessibility sign-off.

## Defects and test quality

- **Test defect (P2):** Programme completion test expected title-case `Day 14 · Completed` in Progress; the existing Eyebrow component renders uppercase. Corrected the assertion to `DAY 14 · COMPLETED` and explicitly scrolls it into view. Programme row expectations remain unchanged.
- **Test defect (P2):** Artwork disagreement fixture starts Day 9 at Welcome but expected Resume. Corrected it to the existing Start today's practice CTA. No product behavior change was needed.
- **Coverage gap:** Curriculum UI test previously ended at transfer prompt without completing transfer or checking archived references. Extended the existing loop instead of adding twelve duplicate tests; IDs now carry the day so cross-lesson mistakes cannot hide behind identical fake IDs.
- **Coverage gap:** Added both checkpoint routes and isolated real Android media/storage checks. Existing fake tests remain useful for deterministic denial, start/stop failure, insufficient-storage-shaped failures, save failure, stale callbacks and rapid taps. They are not hardware/storage-exhaustion tests.
- **New-test setup correction:** First compilation used a material3 Text import unavailable to the app test compilation classpath; removed the unused text. A subsequent storage test stalled because no Compose root was created before runOnIdle. Aborted that instrumentation attempt with force-stop (no data clear), added the root, and reran. The first expanded full run then passed 53/54: the new checkpoint test selected an ambiguous Unsure label shared by familiar comparison and fresh self-check. Scoped it to the latter option and reran the full suite. These were test setup/assertion defects, not application regressions. A subsequent run passed 53/54 with a different `No compose hierarchies found` exception during the ordinary-loop hint assertion; no application crash was reported. The unchanged suite was repeated to investigate. This intermittent instrumentation instability is retained as a risk, not hidden by a retry.

No verified product P0/P1 defect was identified in the executed checks. No production code, architecture, recording codec, applicationId, signing/version configuration, migrations or lesson content was changed in R1-07. Passing tests do not prove the absence of hardware/lifecycle defects.

## Files changed by this task

- Created `app/src/androidTest/java/com/saysharp/saysharp/NativeAudioRegressionTest.kt` — real disposable-cache audio, journal/controller/AtomicFile recovery, corruption and storage timings.
- Modified `app/src/androidTest/java/com/saysharp/saysharp/CurriculumFlowTest.kt` — complete ordinary loops, day-specific reference assertions, both checkpoint loops.
- Modified `app/src/androidTest/java/com/saysharp/saysharp/ProgrammeFlowTest.kt` — actual Progress capitalization/scroll assertion.
- Modified `app/src/androidTest/java/com/saysharp/saysharp/StoreScreenshotCaptureTest.kt` — correct fresh-lesson CTA.
- Created this report. Prior uncommitted R1 changes were preserved; the full Git working-tree diff is larger than this task's inventory.

## Commands and observed build results

Repository: `/Users/rajeevnayandubey/AndroidStudioProjects/SaySharp`.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain --rerun-tasks
```

Fresh baseline: **BUILD SUCCESSFUL in 42s**, 111 tasks executed. JUnit XML totals: **122 shared host + 4 Android unit = 126**, zero failures/errors/skips. This independently reproduces the reported baseline. Debug app and instrumentation APKs compile.

After test additions/corrections:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

**BUILD SUCCESSFUL in 4s**; unchanged host/unit tests were up-to-date, not claimed as a second fresh execution. Final instrumentation APK compiled. Initial/new-test compilation failure and correction are disclosed above. Logs: `/tmp/r107-build.log`, `/tmp/r107-final-build.log`; host/unit XML under each module's `build/test-results/`.

Device commands use `/Users/rajeevnayandubey/Library/Android/sdk/platform-tools/adb` (abbreviated below). A direct runner avoids connected-task uninstall cleanup:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm grant com.saysharp.saysharp android.permission.RECORD_AUDIO
adb shell am instrument -w -r -e class com.saysharp.saysharp.NativeAudioRegressionTest com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
```

First existing device suite: **51 executed, 49 passed, 2 failed** (the stale selectors described above). Isolated corrected native class: **2 passed in 5.807s**, including actual capture/playback, not skipped. A full intermediate run was aborted at the new storage fixture stall. **Final unchanged full-suite rerun: 54 passed, zero failures or ignored tests, in 53.131s.** Log `/tmp/r107-device-confirmation.log`. This includes both native tests actually executed with microphone permission granted. Earlier intermittent failure remains documented; one clean run is not proof of zero test flakiness. Final build/lint recheck using the same full Gradle target list succeeded in 4s (4 executed, 107 up-to-date); log `/tmp/r107-verified-build.log`. `git diff --check` passes. No uninstall, pm clear, broad file deletion or Play submission was performed. Disposable native samples are deleted in test cleanup; real app recordings/progress are not used as fixtures. The microphone permission is restored to its original denied state after testing.

## Lint review

**0 errors, 25 warnings:** AndroidGradlePluginVersion 3; GradleDependency 8; NewerVersionAvailable 5; UnusedResources 7; UseKtx 2. These concern newer tool/dependency versions, unused template color resources and style suggestions. No reported correctness/security regression requires a dependency upgrade or unrelated cleanup in this task. The report is `app/build/reports/lint-results-debug.txt`. Lint is not a substitute for native device QA.

## Privacy review

Production audio goes to UUID files in private noBackupFilesDir/recordings. Session JSON/reflections are private local storage and backup is disabled. There is no transcription, AI, audio HTTP upload or account/backend feature. Operational telemetry callers pass day/stage/slot, enum choices, durations/counts and booleans, not audio IDs/paths or reflection strings. CrashSanitization verifies custom non-fatal reports omit original exception messages/causes. FirebaseConfigurationTest emits an existing synthetic integration diagnostic; it does not transmit speech. This is source/test verification: no traffic capture or backend-console inspection was performed.

## Physical QA still required

Use a disposable QA session/profile for destructive failure injection; never clear a user's recordings to perform these checks.

- [ ] Complete a spoken Day 1 offline; listen to genuinely different First/Retry/transfer answers and verify intelligibility, identity and volume.
- [ ] Deny microphone in the actual Android dialog, retry, deny permanently, visit settings, grant and return. No unintended recording after leaving the lesson.
- [ ] Background while recording, rotate, switch tasks and return. Confirm saved partial or explicit interruption, playable earlier files, correct Resume and no automatic replay.
- [ ] Force-stop/relaunch after each saved boundary and during unfinished capture. Saved attempts/history must persist; unfinished AAC must not falsely appear saved/completed.
- [ ] Interrupt capture/playback with a call or another app taking audio focus; test wired/Bluetooth output and disconnect. Confirm controls recover and only one player is active.
- [ ] Use TalkBack throughout onboarding, capture, Unsure, A/B, checkpoints and completion. Verify focus order, headings, selected-state speech, distinct play/stop labels and error announcements.
- [ ] Use system 200% text, landscape and display zoom on physical hardware. Automated 320×568/2x Compose tests cover reachability, not every system configuration or spoken semantics.
- [ ] Test lower-end storage and full 14-day metadata under realistic load; do not fill the personal phone to simulate low space. Deterministic save-failure tests already cover the coordinator path.
- [ ] Review all lesson examples with target users for comprehension and respectful assertiveness. Self-checks are subjective, not proficiency scores.
- [ ] Capture actual Firebase diagnostic payloads if desired; do not upload audio or reflection to do so.

## Actual device performance

Samsung SM-S931B; Android 16 / API 36; physical display 1080×2340 at 480 dpi; USB-connected debug/instrumentation build, foreground test activity. Normal available storage, not a slow-storage or low-end-device simulation. Timings use elapsedRealtimeNanos; logging contains timings/counts only, no speech or recording IDs.

Final clean run measurements:

| Operation | Measurement | Limits |
|---|---|---|
| AtomicFile metadata save | n=30, p50 **0.431 ms**, p95 **0.678 ms**, max **2.059 ms** | Small one-recording snapshot; each value read back through a recreated native store. Not full-programme worst case. |
| Native recording start | **30.154 ms** | One sample; synchronous main-thread adapter call |
| Native stop + metadata extraction + audio sync + journal | **60.149 ms** | One sample; synchronous main-thread call; progress JSON recovery happens afterward |
| Playback request through completion | **1645.491 ms** for **1556 ms** AAC | Includes instrumentation dispatch and full playback. This is not playback-start latency. |

Earlier native checks observed start 28.7–49.8 ms and stop 53.4–80.0 ms. These small samples demonstrate brief main-thread blocking above a frame budget, not a startup/performance benchmark. No observed ANR in these runs. Lower-end/loaded storage, full history, real startup/session-open latency, playback onset, memory and recomposition traces remain unmeasured. Moving durable commits asynchronously would need ordered state handling and a separate targeted proposal, not a speculative rewrite here. Native test timing lines are available with `adb logcat -d -s R107_PERF:I '*:S'`.

After final instrumentation, `adb shell pm revoke com.saysharp.saysharp android.permission.RECORD_AUDIO` restored the original denied permission; dumpsys confirmed granted=false. Published applicationId/versionCode remain unchanged and the replacement debug app stays installed.

## Release risks and recommendation

**CONDITIONAL GO for human device QA and release-candidate preparation; not production release approval.** The host suite/build and executed device checks establish a useful regression baseline. Before declaring an RC ready to ship, finish real spoken/audio-focus/permission/lifecycle and TalkBack checks above, and inspect any failures.

Hard-killed unfinished AAC, cross-file power-loss atomicity and finalized audio whose journal publication itself fails remain the documented R1-06 limitations. A corrupt progress/journal read deliberately blocks saving rather than guessing or deleting files. No broad orphan cleanup is performed. Main-thread storage/media finalization can briefly block input; no slow-device guarantee, startup benchmark, playback-start latency, heap-leak proof or recomposition benchmark is claimed.
