# R1-06 — Resilient auto-save and optional reflection (SS-021)

## Existing persistence audit and failure-risk assessment

Read the R1-04/R1-05 reports and inspected the actual controller/state machine, repository contracts, AndroidSessionStore, AndroidAudioRecorder, lifecycle wiring, completion and journal UI. The existing 112-test baseline had separate UUID audio files, AtomicFile JSON metadata, save-before-StateFlow publication, callback generations, duplicate recording guards, background finalization, and local assessments/history.

The audit distinguished these boundaries before changing code:

| Boundary | Existing durability | Actual risk found |
|---|---|---|
| Microphone output in progress | Runtime recorder + persisted recordingInProgress flag | A killed, unfinished AAC file is not necessarily playable and cannot be treated as saved. |
| MediaRecorder stopped and duration extracted | Private file finalized, but no recovery reference | Process death before metadata commit leaves audio orphaned; metadata failure deliberately deleted the finalized new file. |
| Recording reference written | Existing AtomicFile JSON save | Atomic within metadata, but not atomic with the audio file. |
| StateFlow/progress updated | Published after successful metadata save | Good ordering retained; unchanged snapshots still caused redundant writes. |
| Lesson marked complete | Stage transition archived a review | Final-stage position alone was trusted; required recordings/assessment were not all rechecked. |
| Restore | Active file-existence checks | Completed/history references were not repaired; active repairs were not persisted. |
| Reflection | Existing optional Progress journal, local realLifeWin field | No invitation on completion; persistence could be reused without a new table or mandatory screen. |

The chosen repair is a small finalized-recording journal plus idempotent metadata commits and explicit completion prerequisites. It does not make two separate files a single filesystem transaction or promise mid-recording recovery.

## Implemented fixes

1. Android audio stop now syncs finalized AAC and publishes one synced pending-recording marker before returning a successful RecordingReference. The marker uses a temporary file and same-directory rename, with a validated private filename/duration on read.
2. Metadata success acknowledges the marker. Metadata failure retains the finalized file, earlier references and first assessment, shows a recoverable message, blocks new capture/advance, and exposes Retry save. Resume from Today remains available to reach that action.
3. Relaunch with a durable recording marker and matching in-progress lesson/slot retries the metadata commit. Relaunch after metadata success but before acknowledgement only acknowledges the marker; it does not create another attempt.
4. Missing active audio references are removed durably and the session routes to the affected recording stage. Missing historical audio references are removed with an unavailable-recording message, retaining other files, reflections and genuinely completed history. Completed lessons are not reopened merely because a file later disappears.
5. Completion now rechecks the full ordinary exercise: First, Retry, transfer, First assessment and replay, preceding-day completion, and no capture still in progress. Checkpoints retain their two recordings, both replays, fresh self-check, familiar comparison and preceding-day requirements. The controller also checks file availability before advancing.
6. Equal metadata snapshots are not rewritten. Existing serialized synchronous commands/callback generations remain; no asynchronous write queue can publish older progress over newer state.
7. Superseded files are deleted only after the new reference commits and only when absent from all current/history references. Known too-short unreferenced pending recordings may be discarded. Journal acknowledgement deletes only its marker, never audio. No directory-wide orphan sweep or automatic broad deletion was added.
8. Native existence checks reject empty audio files as unavailable.

The production engine was not replaced: only finalization/recovery hooks were added to close the demonstrated file-to-metadata window. Recorder/player ports, codecs, permission, audio-focus and lifecycle ownership remain.

## Final save/recovery behavior

- Initial, Retry and transfer files auto-save when recording stops; self-checks auto-save when changed; stage changes and lesson completion commit metadata before UI publication.
- A recording is represented as kept only after its reference commits. If that commit fails, its finalized file stays pending for Retry save or next-launch recovery; no lesson completion is published.
- Normal background/navigation finalizes a playable partial recording through the same path. Under-one-second attempts are rejected. A hard-killed unfinished capture restores the earlier saved state and interruption message; its audio is not promoted.
- A stale recorder/player callback cannot alter later captures; existing generation guards remain. Stable First/Retry identities and A/B playback are unchanged.
- Repeated Finish/advance cannot append duplicate completed days. Today and Programme continue deriving from the one repository-backed StateFlow.
- A metadata repair failure blocks further writes with a reopen/retry message rather than overwriting storage. Unreadable storage/journal keeps files intact and blocks saves.
- No cross-file atomicity is claimed: a kill before journal publication or failure writing the journal itself can leave an untracked finalized file. That file is retained, never swept automatically. Successful stop returns only after journal publication.

## Optional reflection UX

The existing completed-lesson screen now includes Take it into real life and an optional “Where could you use today's skill?” text field. There is no additional screen or required typing/minimum length. Existing 500-character local-note bound is reused.

Practice completion is already durable before this optional invitation. Finish session saves any provided reflection to the existing realLifeWin field and completed review in one AtomicFile metadata write, then opens Progress. Empty text is valid and typically a no-op. Skip reflection leaves completion intact and discards only the uncommitted draft; it does not delete an earlier saved note. Failed reflection saves keep the completed lesson and draft onscreen for retry.

The draft uses saveable presentation state for recreation; it is not promised durable across a hard kill until Finish session succeeds. No lesson is reopened to request reflection. Reflection text/audio/transcripts are not added to telemetry; the existing has_note boolean and sanitized operational errors remain the only related diagnostics.

## Files changed in R1-06

Created:
- `shared/src/androidMain/kotlin/com/saysharp/shared/platform/android/RecordingJournal.kt`
- `shared/src/androidHostTest/kotlin/com/saysharp/shared/platform/android/RecordingJournalTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/PersistenceRecoveryTest.kt`
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/CompletionFixture.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/ReflectionFlowTest.kt`
- `app/src/androidTest/java/com/saysharp/saysharp/PendingSaveFlowTest.kt`
- `docs/R1-06-PERSISTENCE-RECOVERY.md`

Modified:
- `shared/src/commonMain/kotlin/com/saysharp/shared/platform/AudioRecorder.kt` — pending/acknowledgement recovery hooks.
- `shared/src/androidMain/kotlin/com/saysharp/shared/platform/android/AndroidAudioRecorder.kt` — finalized file sync/journal, retain finalized output on marker failure, empty-file detection.
- `shared/src/commonMain/kotlin/com/saysharp/shared/practice/PracticeController.kt` — pending save/recovery, durable missing-file repair, safe supersession and equal-write suppression.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionState.kt` — observable pending-save action state.
- `shared/src/commonMain/kotlin/com/saysharp/shared/session/SessionTransition.kt` — full completion prerequisites.
- `shared/src/commonMain/kotlin/com/saysharp/shared/ui/PracticeScreen.kt` — Retry save and optional completion reflection.
- `shared/src/commonTest/kotlin/com/saysharp/shared/session/DayOneSessionTest.kt`, `SessionFoundationTest.kt`, `RetryComparisonTest.kt` — finalized pending files are retained rather than deleted; complete fixtures include genuine practice requirements.
- `shared/src/commonTest/kotlin/com/saysharp/shared/programme/ProgrammeOverviewTest.kt`, `shared/src/commonTest/kotlin/com/saysharp/shared/telemetry/TelemetryTest.kt` — valid complete-exercise fixtures.
- `shared/src/commonTest/kotlin/com/saysharp/shared/practice/PracticeControllerTest.kt` — completed history is explicit; no legacy reconstruction assumed.
- `app/src/androidTest/java/com/saysharp/saysharp/PracticeFlowTest.kt`, `ProgrammeFlowTest.kt` — Finish session selectors/explicit completed-history fixture.
- `app/src/androidTest/java/com/saysharp/saysharp/SessionStoreTest.kt` — interrupted AtomicFile metadata-write recovery test.

Curriculum, AndroidSessionStore schema, application ID/signing/version fields and previous R1 work were preserved. There is no new persistence framework/module, migration/reset task or publishing work.

## Exact tests and results

Fresh full verification:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain --rerun-tasks
```

After the pending-save Resume integration fix, repeated the same targets without `--rerun-tasks`:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Results: BUILD SUCCESSFUL; **122 shared host tests + 4 Android unit tests = 126 passing**, zero failures/errors/skips. This is 14 tests above R1-05's 112. Debug and instrumentation APKs compile. Lint: zero errors, 25 dependency/resource/project warnings. `git diff --check` passes. Log: `/tmp/r106-final-build.log`.

Eleven new common tests inject metadata/audio/recovery failures, exercise finalized-file recovery before/after commit, last-valid-state restart, durable active/history repair, guarded completion, blank/provided reflection, failed reflection retry, idempotence and checkpoint completion. Existing all-fourteen restart traversal, stale callback, A/B, duplicate-tap, self-assessment and telemetry privacy tests remain passing. Fixtures were corrected where they previously asserted completion with only a final-stage label or expected finalized audio deletion.

Three Android host tests execute the production RecordingJournal against real temporary filesystem files: publication/recreation/acknowledgement, partial temporary-marker interruption, failed writes and protection of valid audio. These are real journal filesystem boundaries, not a mock journal. They do not execute Android MediaRecorder or AndroidSessionStore.

Native instrumentation adds three reflection UI cases, one pending-save Resume case, and interrupted AtomicFile JSON-write recovery. These **compiled but did not run**: adb reports no devices and no AVD is configured. Native JSON round trips, physical audio, font scaling and TalkBack remain device-only checks. No device/instrumentation pass is claimed.

No app installation, Notion update, GitHub push, Play submission, new cloud/AI feature or raw speech/reflection telemetry was performed.

## Manual physical Android QA

1. Entirely offline, record First and select each self-check value. Background/relaunch and force-stop/relaunch after save; confirm the same stage, First reference and selection.
2. Save a clearly different Retry; restart and play both A/B files. Their identities/content and First assessment must remain distinct/intact.
3. Background during capture and interrupt with an incoming call/audio-focus loss. Expect a playable saved partial or an explicit recovery message. Hard-killed unfinished capture must not appear completed.
4. Deny microphone permission, then grant and retry. Earlier files/history must remain usable.
5. Exercise low-storage/metadata failures with deterministic debug fakes/test storage rather than filling the personal device. Confirm pending audio is retained, Resume from Today exposes Retry save, and retry does not capture again or duplicate progress.
6. Verify missing-file recovery using isolated test files only: active steps reopen at the affected capture, historical missing references report unavailable without deleting other recordings or reopening completed lessons.
7. Finish with blank reflection, a one-character reflection and a written reflection. Verify local current/completed-review agreement after relaunch. Skip a draft and confirm practice stays completed; failed reflection saves must allow retry.
8. Rapidly tap Stop/Retry save/Finish/Skip and reopen Compare. No duplicate attempt/completion or new playback-only attempt should appear.
9. Check Today/Programme after each completion and restart, including Days 7/14. Checkpoints still require their two recordings/replays/assessment/comparison before completion.
10. With large text, small screens/landscape and TalkBack, confirm the optional field scrolls, Retry save/Finish/Skip remain reachable, and errors/selected states/player labels are understandable. No typing should be necessary to finish.
11. Run `./gradlew :app:connectedDebugAndroidTest` on the connected development device, retain reports, and review metadata-only Firebase debug events without uploading speech or reflection text.

## Known limitations and remaining R1 tasks

- No guarantee of recovering hard-killed unfinished AAC, hardware failure or power loss. File/journal/metadata are ordered durable checkpoints, not one cross-file transaction.
- If journal publication itself fails, finalized but untracked audio may remain. No directory-wide cleanup is attempted; storage safety is preferred over deleting potentially valid data.
- Small synchronous metadata writes and journal/file sync remain serialized with the existing controller. Duplicate writes are removed, but slow-flash UI latency needs physical-device profiling; no asynchronous save pipeline was introduced that could reorder progress.
- Android native filesystem/AtomicFile/media behavior, lifecycle timing, accessibility and genuine low-storage recovery still need device verification. iOS must provide equivalent recovery hooks when implemented.
- Unsaved reflection drafts are not guaranteed after a hard kill. Saved reflections and completed practice are persisted locally.

Remaining R1 work: run connected instrumentation and this physical checklist; profile save latency; perform targeted content/accessibility/user testing; fix observed integration issues. Curriculum and previous R1-04/R1-05 behavior remain in scope only for regression checks.
