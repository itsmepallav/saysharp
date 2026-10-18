# R1-08 — Release candidate preflight

Date: 2026-10-17. Scope: Android internal evaluation, no Play upload or old-data migration.

## Investigation plan and evidence

Read the R1-01 through R1-07 reports and checked the current Gradle setup, typed curriculum, Compose root/navigation, controller guards/lifecycle, native audio/journal, private storage, manifests, telemetry and tests. The checkout contains substantial uncommitted R1 work; it was preserved.

R1-07's historical failure occurred after selecting Unsure in the ordinary curriculum loop: `No compose hierarchies found` while asserting the listening hint. A subsequent unchanged full run passed. That proves intermittent behavior, not its cause.

Current fixture inspection:
- One createComposeRule owns one ComponentActivity/root for each test. setContent runs inside the rule.
- The loop keys lesson-local in-memory storage/controller and root UI by selected day. Changing the day replaces those remembered objects; it does not launch an activity.
- All state-changing test commands are runOnIdle or Compose interactions. Controllers use no coroutine scope in this fixture; fake playback completes synchronously. Their audio methods own no native resources.
- Navigation derives from saveable AppScreen and one controller StateFlow. Hints derive from saved assessment, not a second asynchronous UI flag.
- Preparation effects are canceled with their keyed composition. No demonstrated stale selector/root ownership defect was found. Later awake-device missing-control assertions prompted explicit bounded destination settling described below.
- No arbitrary sleeps, retrying assertions, broad catch, disabled test or root substitution was added to the curriculum test.

Ten separate process/runner invocations of the exact ordinary-loop test all passed (19.769–20.182 seconds each). The first full run passed 55/55 in 60.296s. The second full run failed three SelfAssessmentFlowTest cases with the same missing-hierarchy exception; its ordinary curriculum loop still passed.

**Classification: environmental screen-sleep interference reproduced; historical R1-07 failure attribution remains unproven.** Device evidence shows PowerManagerService “Going to sleep due to timeout” at 13:30:28.609, followed by missing-root failures at 13:30:30.780, 13:30:34.949 and 13:30:39.152. The screen woke at 13:30:39.754 and subsequent tests passed. No application crash was observed. The current failure pattern is explained by the screen being dozed, rather than a demonstrated application root/state defect; do not assume that this conclusively explains R1-07 without its original power logs.

The demonstrated test-environment gap is addressed by a bounded preflight script that temporarily enables plugged-in stay-awake, wakes the display, restores the original setting/permission on exit, and retains every result. It does not change curriculum assertions, ignore failures or inject sleeps. Two additional full runs under that controlled condition produced 55/55 passing (60.633s), then 53/55 passing (48.518s). The latter failures were different: the Day 5 welcome instruction was absent immediately after navigation, and Finish checkpoint was absent after a selection/scroll interaction. No missing-root exception occurred in those two controlled runs. Keeping the screen awake is therefore useful but is not a complete explanation of all instability.

Added explicit bounded destination/control synchronization in CurriculumFlowTest: await exactly one expected node for at most five seconds before acting, and await the requested welcome/footer after navigation. All original assertions and actual touch interactions remain; a wrong destination still fails. This targets an observed settling gap, but the cause is not claimed proven solely by adding a wait. Two further bounded full runs evaluate it. No production navigation/state code was changed.

The first build of the wait helper required ExperimentalTestApi opt-in; the explicit class annotation fixed compilation. A mistakenly started old-APK run was aborted without clearing data and is not counted as a complete suite/pass. Its runner log is retained. The corrected instrumentation APK then compiled successfully in 3s before installation and final runs.

## Build / test commands

From `/Users/rajeevnayandubey/AndroidStudioProjects/SaySharp`, using Android Studio's JDK:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:lintRelease --console=plain --rerun-tasks
```

Observed: **BUILD SUCCESSFUL in 1m 19s; 165 tasks executed.** JUnit XML independently counted **122 shared host + 4 Android unit tests, zero failures/errors/skips**. Both APKs and unsigned release compile. Debug and release lint each report **0 errors, 25 warnings**.

After adding performance coverage only:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Observed: **BUILD SUCCESSFUL in 6s** (8 tasks executed, 78 up-to-date). The new test does not change application code or the curriculum test under investigation.

For device commands, `adb` means `/Users/rajeevnayandubey/Library/Android/sdk/platform-tools/adb`. One Samsung SM-S931B remains attached; Android 16 / API 36, 1080×2340, 480 dpi. Existing microphone grant was false. No uninstall, pm clear or personal-storage fixture was used.

Isolated repeat (execute ten times with a different log filename, not a retry loop):

```sh
adb shell am instrument -w -r -e class 'com.saysharp.saysharp.CurriculumFlowTest#everyOrdinaryLessonHidesExamplesUntilReplaySelfCheckAndCorrection' com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
```

Full checks (two invocations, retain both outputs):

```sh
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.saysharp.saysharp android.permission.RECORD_AUDIO
adb shell am instrument -w -r com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm revoke com.saysharp.saysharp android.permission.RECORD_AUDIO
```

For controlled repeats, capture `adb shell settings get global stay_on_while_plugged_in` (observed **0**), run `adb shell svc power stayon true` plus `adb shell input keyevent KEYCODE_WAKEUP`, and restore the captured global setting after the two runs. The device must be plugged in and manually unlocked; the script never bypasses a secure lock.

The grant is only for isolated native capture tests; it is restored to the original denied state afterward. Direct instrumentation avoids potential connected-task uninstall cleanup. Native tests write/delete only their UUID cache subdirectories, never the user's noBackupFilesDir recordings or progress. Short test capture waits are audio acquisition durations off the main thread, not synchronization workarounds for the flaky UI test.

## Manual physical QA — human results PENDING

No subjective audio quality, spoken accessibility announcement or actual permission-dialog result is inferred from instrumentation. Use a spare device or separate Android test profile with a fresh candidate for the complete Day 1 sequence. If the existing personal installation has progress, **do not reset it** to reach Day 1. No destructive fault injection is needed for this checklist.

Record device/build, date, tester, pass/fail and observations for each row.

| Check | Exact sequence | Expected result | Human result |
|---|---|---|---|
| Spoken Day 1 / offline | On fresh QA profile, enable airplane mode. Finish the three onboarding pages → Today → Start today's practice → Try it myself. Record 30–60 seconds answering the client-demo scenario; begin with “First attempt.” Stop and save → Listen and check. Listen to the full answer and choose Done / Not yet / Unsure honestly. See one useful example → Try again. Respond to the same situation with a noticeably different answer beginning “Retry.” Compare both players. Try a fresh question → record the apartment-budget response beginning “Transfer.” Finish my practice → Skip reflection. Open Progress and play all three. | Three distinct intelligible recordings, correct labels/prompts, examples hidden until self-check, completion retained without typing. No network needed for practice. | PENDING |
| Real permission dialog | Before first recording on the disposable profile, tap Start recording and choose Don't allow. Tap Start recording again, allow microphone while using the app, then record. If Android no longer offers the dialog, use app Settings → Permissions → Microphone → Allow only while using the app, return and explicitly tap Start. | Denial shows a recoverable message, no false saved attempt; explicit retry records. No recording merely from returning from settings. | PENDING |
| Saved restart | After saving First, note stage/answer label. Run the two commands below; Resume and replay First. Repeat after saving Retry. | Same lesson/stage and distinct First/Retry after process restart; no duplicate completion or extra capture. | PENDING |
| Background during capture | In disposable session, record at least five seconds, press Home, wait briefly, reopen and Resume. Repeat during playback. | Capture saves a playable partial or gives an honest interruption message; existing attempts remain. Playback stops and does not auto-resume. | PENDING |
| A/B and focus | On Compare, play First → Retry → First mid-playback. Tap active player to stop. Then play, open another app and start music, return. Try an incoming call if available. Repeat with wired/Bluetooth headset and disconnect it. | Only one answer audible; correct controls recover after focus loss; no mixed, stuck or lost files. Hardware/output type must be recorded. | PENDING; external hardware availability unknown |
| TalkBack | Enable device Settings → Accessibility → TalkBack. On a fresh QA profile navigate all three onboarding pages, Today, recording, first replay, Unsure guidance, correction/retry, A/B, transfer and Finish/Skip. Continue Days 2–6 with the same practice sequence to reach Day 7, then Days 8–13 to reach Day 14; there is no 24-hour gate. At both checkpoints record familiar and fresh answers independently, then verify familiar comparison and fresh self-check as distinct questions. | Sensible focus order; spoken headings/labels, distinct players, selected Done/Not yet/Unsure announcements, reachable recording/footer actions and errors. No typing required. | PENDING |
| 200% text / landscape | Record original text/display/rotation settings. Set system font to 200% (if OEM slider cannot reach this, use the optional command below), enable rotation and turn landscape. Repeat onboarding, Today, capture, Unsure, A/B, checkpoint and completion. Restore original settings afterward. | All essential text scrolls; Start/Stop/Continue/Finish/Skip reachable, no overlapping or clipped essential controls. | PENDING |

Process restart on the disposable QA profile only, **after saving**:

```sh
adb shell am force-stop com.saysharp.saysharp
adb shell am start -W -n com.saysharp.saysharp/.MainActivity
```

For multi-user profiles, select the intended user explicitly with `--user USER_ID`; do not operate on the personal user's instance by mistake. Force-stop does not clear data. Unfinished hard-killed AAC is not guaranteed recoverable and must not be presented as saved.

Optional exact font setting on a dedicated test device: first run `adb shell settings get system font_scale` and write down the result; then `adb shell settings put system font_scale 2.0`. Restore the captured value after QA (if originally null, delete that setting). These commands have **not** been applied to the user's phone by this task. TalkBack, airplane mode and orientation settings were also left unchanged.

## Performance coverage

R1-07 timings were small samples: small AtomicFile p95 ~0.678 ms, native start ~30 ms and stop/finalization ~60 ms. Inspection confirms MediaRecorder prepare/start/stop, metadata extraction, file sync/journal and synchronous progress commits execute on the main thread. Calls longer than a frame budget can delay drawing/input; that is an inference from measured call duration, not human confirmation of a visible freeze.

Expanded native tests now measure:
- Thirty small-snapshot writes and thirty full-programme metadata writes (40 recording references, fourteen 500-character dummy reflections); native load asserts equality after every write.
- Five real short captures through PracticeController. Every start/stop is invoked twice consecutively; counted native operations must remain one per capture. Each kept reference is new, durable and locally available, journal is acknowledged, and only the latest same-slot retake file remains in disposable storage.
- Existing real playback/corruption recovery and adapter/controller recreation are retained.

No latency threshold is used to force a green benchmark, and no asynchronous persistence or recorder rewrite was introduced. Repeated serialized controller calls exercise duplicate guards, not actual double-tap perception during a main-thread stall. Human visible responsiveness, audio quality, lower-end storage and startup/playback onset remain separate checks.

## Lint / configuration / privacy review

Debug and release warning categories are identical: AndroidGradlePluginVersion 3; GradleDependency 8; NewerVersionAvailable 5; UnusedResources 7; UseKtx 2. No reported correctness/security error. Avoided unrelated dependency upgrades/template cleanup. Dependency currency is not a proof of production safety.

Application identity is `com.saysharp.saysharp`; versionCode **10**, versionName **0.6.1**, minSdk **24**, target/compileSdk **37**. Release optimization is currently disabled; release is non-debuggable and has no release signing config. Debug uses the existing Android debug key; no signing credential was created/changed. Release manifest does not contain the debug Compose ComponentActivity host. MainActivity is exported for launcher use; custom receivers and SDK providers/services are non-exported except androidx.profileinstaller.ProfileInstallReceiver, which is protected by android.permission.DUMP.

Source permissions: INTERNET (Firebase diagnostics), RECORD_AUDIO (capture), POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED (optional reminders). Merged release APK additionally contains ACCESS_NETWORK_STATE, WAKE_LOCK, install-referrer binding, ACCESS_ADSERVICES_ATTRIBUTION and the generated non-exported receiver permission from dependencies. AD_ID and ACCESS_ADSERVICES_AD_ID are removed. The remaining attribution permission is an explicit **publication privacy-review gate**, not evidence that speech is uploaded; the app has no ads. No permission was silently removed or newly enabled in this task.

Audio remains private under noBackupFilesDir; backup/device-transfer exclusions remain. Optional reflections are local JSON, and operational telemetry callers use enums/counts/durations/booleans. Sanitized custom crash reports exclude original exception messages/causes. No audio/transcript/reflection upload route or AI/scoring claim was found in the shipped shared UI/session copy. The feedback 1–5 rating is an app-usefulness survey, not speech grading. No live traffic capture was performed. Firebase SDK initialization/diagnostic smoke checks may emit operational test diagnostics.

Tracked filenames were checked for keystore/certificate/env/signing properties and google-services.json: none found. Local Firebase configuration and signing files are ignored. This is a scoped source/config review, not an exhaustive secret-history scan. No secret key material was printed/copied into docs or candidate artifacts beyond ordinary APK public certificates/configuration resources.

## Candidate notes — truthful internal description

SaySharp — Think Sharp. Speak Bold. This internal candidate includes a 14-session programme for articulation and respectful assertiveness: real situations, local recording/playback, a focused self-check, one correction, same-situation retry, fresh transfer and two checkpoints. Progress and recordings stay on this device. No AI evaluation, transcription, accounts, payments or cloud audio backup. Improvement is not guaranteed. Device audio, accessibility and lifecycle QA are still being completed.

This is internal evaluation copy, not an update to the existing Play listing. No unsupported future lessons/modes, Speaking Gym or AI proficiency claims are included.

## Deferred publication gates

These are documented only; no old-version reset/migration or signing/version alteration was implemented:
1. Confirm published applicationId and Play signing/upload-certificate ownership using Play Console before any later submission.
2. Determine the highest versionCode already uploaded and choose a compatible higher production version; current 10 is **not** approved as a new upload.
3. Resolve signing and existing-install upgrade behavior with the actual distributed certificate; debug-signed candidates cannot be assumed compatible with Play installs.
4. Separately specify/test old local-data migration or confirmed reset, warnings and safeguards before replacing an older published version.
5. Review current listing/privacy policy/Data safety/permissions, including merged attribution permission and Firebase diagnostics; no publication changes are made here.
6. Obtain human audio, TalkBack, permission, lifecycle, audio-focus and release-mode smoke approval.

## Files changed / open risks

Modified `app/src/androidTest/java/com/saysharp/saysharp/NativeAudioRegressionTest.kt` for measured native duplicate-command coverage and representative metadata profiling; `app/src/androidTest/java/com/saysharp/saysharp/CurriculumFlowTest.kt` for explicit five-second expected-node settling while retaining all assertions/touch interactions. Created `scripts/r1-device-preflight.sh` for bounded reproducibility and temporary stay-awake/permission restoration, and this report. Generated build/log/candidate files are under ignored build directories. No production code, architecture, curriculum, application identity/version, Gradle signing config or existing recordings changed.

P0/P1: none demonstrated by the investigation so far; this is not a guarantee that manual QA will find none. Historical Compose-root instability, unverified human-dependent checks, main-thread media latency, SDK attribution/privacy review, hard-killed unfinished AAC and R1-06's cross-file durability limitations remain explicit risks. The final counts, timings and readiness recommendation below include every completed run, the aborted old-APK attempt, and all human-dependent gaps.

## Candidate output and signing

- `app/build/outputs/rc/r1-08/SaySharp-R1-debug.apk`: installable debug candidate; this application build is used by the repeated instrumentation suites.
- `app/build/outputs/rc/r1-08/SaySharp-R1-internal-release.apk`: installable, non-debuggable release-mode candidate signed using the **existing Android debug key**, solely for internal sideload evaluation. It is not signed with verified Play/upload credentials and was **not installed on the personal Samsung**. Its compile/manifest/signature checks pass; its release-mode MainActivity human smoke check is PENDING.
- `app/build/outputs/apk/release/app-release-unsigned.apk`: unsigned Gradle release output; not installable as-is.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: instrumentation companion, not a user app.
- `app/build/outputs/rc/r1-08/SHA256SUMS`: candidate checksums. Keystore and credentials were not copied, regenerated or modified.

Existing public signing certificate SHA-256: `97edabf3ee96c727a63a9f3ec38ba0d4d4c8f97a93cb4e0ed105de5834c57240`. apksigner verified the signed internal release copy. Its packaged manifest is non-debuggable and excludes the Compose test host.

Candidate SHA-256:

```text
7ca388014e4cb390a00be6eef7e0bc4751183abbcefebd0f912b17654f39d77e  SaySharp-R1-debug.apk
423eef51988258e7b2d8a175c3e7496a6a92d83fdedf8b7c910928121d837417  SaySharp-R1-internal-release.apk
```

Build copies identify this development candidate by checksum; version 0.6.1/10 was deliberately not incremented. The full checkout is uncommitted, so a base Git commit alone cannot reproduce the candidate; retain the tested working tree/build artifacts. No GitHub push, Play submission or distribution to testers was performed.

## Repeatable device preflight

Build/install matching debug app/test APKs without uninstalling or clearing data. On a plugged-in, unlocked development device:

```sh
# ANDROID_SERIAL may select one intended device when several are attached.
bash scripts/r1-device-preflight.sh 10 2
```

The arguments bound isolated/full counts (maximum 20/5), not retry counts. Every requested run executes once; any failed or skipped run makes the script fail, with logs under app/build/reports/r1-08. Original stay-awake and microphone grant are restored through an EXIT trap. The script does not build, install, uninstall, clear storage, dismiss secure locks or change app data. Full native tests temporarily record disposable local ambient samples; cleanup removes only those test directories. `bash -n scripts/r1-device-preflight.sh` passes. Runtime verification is recorded below.

## Observed run ledger

| Run / condition | Outcome | Elapsed |
|---|---|---|
| Ordinary-loop isolated 1–10, original test | 10/10 passed | 19.769–20.182s each |
| Full 1, original environment | 55/55 passed | 60.296s |
| Full 2, original environment | 52/55 passed; 3 missing-root failures coincident with screen sleep | 71.643s |
| Awake full 1, original test | 55/55 passed | 60.633s |
| Awake full 2, original test | 53/55 passed; 2 missing expected-control assertions | 48.518s |
| New preflight script runtime check (1 isolated, 0 full) | 1/1 passed; exit 0, original setting restored | Runner log retained |
| Old-APK run inadvertently started during helper compile failure | ABORTED, no complete-suite pass claimed | Excluded from complete-run totals |

Final changed-test results: **55/55 passed in 62.338s and 55/55 passed in 61.960s**, zero ignored cases. Script exit 0; `summary.txt` records `isolated=0 full=2 failed_runs=0`. Earlier failures remain part of the ledger rather than being replaced by these successful results. Across the six completed full-suite runs, five individual case failures occurred before the bounded settling change; no application P0/P1 was demonstrated.

Original root-failure classification remains **partly isolated, not conclusively resolved**: the current screen-sleep-associated failures are environmental; the original R1-07 root failure lacks matching historical power evidence. The missing expected controls under awake conditions are investigated as synchronization/fixture behavior; no reproduced application state corruption/crash or P0/P1 product defect has been demonstrated.

## Measured performance (Samsung debug/instrumentation)

Measurements below are per-run samples, not production benchmarks or pooled percentiles. The two final controlled runs use SystemClock.elapsedRealtimeNanos on the main thread:

| Operation | Final run 1 | Final run 2 | Interpretation |
|---|---|---|---|
| Full-programme JSON, 13,163 bytes, n=30 each | p50 1.579 ms; p95 1.621 ms; max 1.632 ms | p50 1.574 ms; p95 1.650 ms; max 1.683 ms | Includes serialization and durable AtomicFile write; every snapshot round-trips. Normal storage, not full/slow flash. |
| Controller start with duplicate call, n=5 each | p50 29.232 ms; max 30.168 ms | p50 27.155 ms; max 34.391 ms | Includes start marker and native prepare/start; native count remains exactly five. |
| Controller stop/save with duplicate call, n=5 each | p50 89.783 ms; max 95.170 ms | p50 79.793 ms; max 85.209 ms | Includes native finalize/sync/journal, progress commit, acknowledgement and safe retake cleanup. Exactly five native stops. |

These synchronous calls can delay drawing/input by tens of milliseconds. No ANR or duplicate native operation was observed. Visible jank, actual queued user double-taps, lower-end storage, startup, session-open and playback-start latency were not directly measured. No async refactor is justified solely by these brief samples; obtain human responsiveness feedback first. Normal background/process-recreation/focus testing remains PENDING despite deterministic recovery tests.

## Final disposition

**Internal evaluation: CONDITIONAL GO.** The installable candidates are ready for the executable human QA sequence, with a fresh 126-test host/unit pass and two consecutive 55-test full-device passes after explicit destination settling. The awake-device assertion failures support a synchronization/fixture hypothesis, but two passing runs do not conclusively prove its cause or eliminate the historical flake. Keep the preflight protocol and track any recurrence with device power/lifecycle logs.

**Production readiness: NO-GO.** Essential subjective audio/TalkBack/permission/lifecycle/audio-focus checks, release-mode MainActivity smoke testing, and the deferred publication gates have not been approved. This is not authorization to upload either candidate.

Actual human observations: **none supplied; all manual checklist outcomes remain PENDING.** Automated native microphone/playback/recovery and repeated-command checks executed; they cannot establish spoken intelligibility, TalkBack announcements or actual permission-dialog behavior.

Evidence retained:
- `app/build/reports/r1-08/preflight-evidence/` — baseline isolated logs, four earlier full-suite logs, power/lifecycle evidence, build logs, per-run metadata and full-summary.json.
- `app/build/reports/r1-08/device-20261017-133527/` — one isolated runtime check of the preflight script, passed.
- `app/build/reports/r1-08/device-20261017-133628/` — aborted old-APK attempt, not a pass.
- `app/build/reports/r1-08/device-20261017-133719/` — final two 55-test passes, native timings, environment/summary logs.

Verified after completion: stay_on_while_plugged_in restored to **0**, RECORD_AUDIO restored to **granted=false**. No personal recordings/progress were reset, cleared or uninstalled. `bash -n scripts/r1-device-preflight.sh` and `git diff --check` pass. App/code/build settings remain unchanged; changes are limited to the two instrumentation test files, the bounded device-preflight script and this report. Next step is human QA and investigation of any observed failure, not additional lessons/features or publication.
