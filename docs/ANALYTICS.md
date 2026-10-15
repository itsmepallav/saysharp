# Firebase measurement

Firebase project: `saysharp`; Android package: `com.saysharp.saysharp`.
The supplied Android configuration is installed at `app/google-services.json`.
Firebase Android BoM 35.0.0, Google Services plugin 4.5.0 and Crashlytics plugin 3.0.8 follow the [official Android setup](https://firebase.google.com/docs/crashlytics/android/get-started).
Analytics and Crashlytics collect in debug and release; `build_type` identifies development builds. Use DebugView on development devices to keep test events out of normal reporting.

## Questions this answers

| Question | Events / parameters |
| --- | --- |
| Where do people leave onboarding? | `tutorial_begin`, `welcome_slide_viewed` (`page`, `is_replay`), `tutorial_complete` (`goal`, `skipped`) |
| Do people start and finish practice? | `practice_opened` (`day`, `stage`, `has_saved_attempt`), `practice_step_completed` (`day`, `stage`), `chapter_completed` (`day`, `chapter_type`, `completed_count`, `comparison`) |
| Which screens or steps lose people? | `screen_view` (`screen_name`, `screen_class`, `day`, `stage`), `practice_left` (`reason`: back/save_leave), `app_backgrounded` (`day`, `stage`, `was_recording`) |
| Are recording friction and retakes common? | `recording_started` (`day`, `stage`, `slot`, `is_retake`), `recording_saved` (`day`, `slot`, `duration_seconds`, `is_retake`, `interrupted`), `recording_rejected`, `recording_failed`, `recording_blocked`, `recording_missing` |
| Are users listening and reflecting? | `playback_started` (`day`, `slot`, `opening_only`), `self_assessment_saved` (`day`, `stage`, `choice`), `comparison_saved` (`day`, `choice`), `recall_hint_used` (`day`) |
| Does practice reach real life? | `real_life_win_saved` (`day`, `has_note`); this measures saving a reflection, not independently verified improvement |
| Do reminders bring people back? | `reminder_toggled` (`enabled`), `reminder_time_changed`, `reminder_blocked`, `reminder_posted` (`source`: daily/preview), `reminder_opened` (`source`) |
| Do permissions prevent practice? | `permission_requested`, `permission_result` (`permission`: microphone/notifications, `granted`) |
| Do people return to review or finish the programme? | `chapter_reviewed` (`day`), `milestone_viewed`, `milestone_acknowledged` (`day`), `programme_completed` (`completed_count`) |
| Other deliberate actions | `speaking_goal_changed` (`goal`), `welcome_replayed` (`goal`, `skipped`), `practice_data_deleted` |
| What should improve or be built next? | `feedback_submitted` (`feedback_kind`: general/feature/problem, `feedback_option`, optional `rating`: 1–5, `day`, `completed_count`) |

Use automatic Analytics first-open, sessions and retention reports for return rates. App backgrounding is a pause, not proof of abandonment. Infer drop-off from opened chapters without later completion. A posted notification is an OS notify call, not proof the user saw it. Duration counts are audio duration, not time speaking or time practising. Baseline playback can occur in later sessions; `slot=Baseline` identifies the original recording.

Completion, assessments, notes and onboarding success events follow successful local persistence. Recomposition does not emit screen views repeatedly; welcome views use settled pages. Restoring completed history does not emit new completions. Replaying onboarding uses `welcome_replayed`, not another `tutorial_complete`. Retakes are separate attempts; failed saves never count as saved recordings. No historical completion events are backfilled on upgrade.

## Firebase console

1. Ensure Google Analytics is enabled in Firebase project settings → Integrations. The Android JSON alone does not verify the Analytics property is linked.
2. Open Analytics → DebugView for device verification. For regular reports, create event-scoped custom dimensions for `day`, `stage`, `slot`, `chapter_type`, `choice`, `comparison`, `goal`, `source`, `page`, `skipped`, `is_retake`, `has_saved_attempt`, `interrupted`, `reason`, `permission`, `granted`, `has_note`, `enabled`, `opening_only`, `is_replay`. Register numeric event metrics for `duration_seconds` and `completed_count` if needed. Register the `build_type` user property as a user-scoped dimension.
3. Useful funnel: `tutorial_begin` → `tutorial_complete` → `practice_opened` → `recording_saved` → `chapter_completed`. Split chapter events by `day`; split skipped onboarding separately. Mark `chapter_completed` and `programme_completed` as key events.
4. In Crashlytics, inspect custom keys `screen`, `day`, `stage`, `build_type`, event breadcrumb logs and non-fatal operations. Console configuration requires project access and is not performed by the Android integration.

## Crash diagnostics

Unhandled failures are captured by Crashlytics. Caught storage, audio, playback and reminder failures are reported as non-fatals. Custom reports retain original call sites and exception class, stripping the original message and cause to avoid uploading local paths or user text. Native callbacks with no Throwable report their operation. Repeated operation/type pairs are deduplicated per process to avoid flooding reports. Analytics events also provide explicit Crashlytics logs; [Firebase can include Analytics breadcrumbs](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports#breadcrumb-logs).

Shared UI/controllers depend on a small no-op-by-default telemetry interface. Android supplies the Firebase adapter through Application. A failing adapter cannot block practice, recording, saving or navigation. Future iOS work can supply its own native adapter without adding Android SDK dependencies to shared code.

## Feedback & ideas (v0.6.1)

Settings → Feedback & ideas provides three categories: feedback, feature requests and problems. Users select one predefined option and can optionally rate their overall experience for general feedback. Category changes clear the previous selection and rating. The submit action stays disabled until a topic is selected. After the SDK accepts the event, the app confirms that the response is queued and prevents repeat taps on the same submission. If the SDK is missing or throws, the selection remains and users can retry.

This is structured feedback, not a free-text inbox, support ticket or Google Form. No Google Form URL or backend database is required. Firebase Analytics owns delivery and offline batching; queued does not mean confirmed receipt. Each submission includes the current chapter and completed-session count so feedback can be segmented by experience.

To inspect responses immediately, open Analytics → DebugView → `feedback_submitted` on the development device. For aggregate reporting, register `feedback_kind` and `feedback_option` as event-scoped custom dimensions and `rating` as a custom metric in Analytics → Custom definitions. `day` and `completed_count` reuse existing parameters. Use event counts by feature option to prioritise requests and average rating for general feedback. An omitted rating is not a zero. Feature/problem votes never include a rating. The [Firebase event guide](https://firebase.google.com/docs/analytics/android/events) explains custom parameter reporting.

## Data boundaries

No recordings, transcript, prompt/response text, note text, note length, filenames, exact reminder time, names or email addresses are added to custom events or logs. No explicit user ID is set. Firebase still uses its own installation identifiers and device/app diagnostics, so the telemetry is not described as fully anonymous. Advertising ID permissions and advertising ID collection are disabled. Recordings and notes stay local; usage and crash diagnostics use the Internet. Settings discloses this. Unhandled crash traces remain standard SDK reports; custom non-fatal messages are sanitized.

## Device verification

```sh
adb shell setprop debug.firebase.analytics.app com.saysharp.saysharp
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
adb shell setprop log.tag.FirebaseCrashlytics DEBUG
```

Install with `adb install -r` to preserve recordings. Run instrumentation directly, never the connected-test task on a personal phone. `FirebaseConfigurationTest` checks the supplied project, logs a debug `integration_check` event and records one synthetic non-fatal `integration_check: IllegalStateException`; it never forces a fatal crash. This verifies client integration, not console receipt. Relaunch the app to allow queued Crashlytics reports to upload; confirm the synthetic issue in the Firebase console. No production crash-test UI is shipped.

Disable device DebugView when using a production build:

```sh
adb shell setprop debug.firebase.analytics.app .none.
adb shell setprop log.tag.FA INFO
adb shell setprop log.tag.FA-SVC INFO
adb shell setprop log.tag.FirebaseCrashlytics INFO
```

## V0.6 verification

Debug and release builds and lint passed. Shared tests: 41; Android JVM tests: 4; direct device tests: 17, all passing. Device logs confirm Analytics collection/debug mode enabled, Crashlytics 21.0.0 initialized, and the synthetic report successfully enqueued to DataTransport. Dashboard receipt and Analytics project linking have not been independently verified. The connected development phone is left in Analytics DebugView mode so its events can be inspected without contributing to regular reports.

V0.6.1: debug/release builds and lint passed; 45 shared tests and 4 Android JVM tests passed. Four new shared tests verify feedback payloads, optional ratings, valid category/option pairs and enqueue failures. Three device feedback tests cover Settings navigation, category reset, confirmation and retry. Their device run is pending because the phone was securely locked; the attempted instrumentation run was stopped and the app remains installed with existing data preserved.
