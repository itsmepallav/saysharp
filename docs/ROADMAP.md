# SaySharp implementation roadmap

Build in small, reviewable parts. Android launches first; shared screens and business rules stay in commonMain. Keep Android APIs in the app or androidMain adapters. Do not create per-screen Gradle modules.

## Part 1 — Foundation (implemented)

KMP shared module, thin Android launcher, Compose Multiplatform theme and Today / Programme / Progress navigation. Lesson models, first lesson text preview, 14-day outline and content-contract tests. No iOS setup.

Acceptance: Android debug build and common tests pass; launcher and sample lesson can be opened. Emulator/device smoke test is tracked separately from compilation.

## Part 2 — One complete practice session (core implemented)

Implemented: welcome, baseline capture, microphone permission, Android recorder/player, private audio files, 45/60-second limits, first attempt, one self-check, retry, comparison, fresh application prompt and completion. Baseline is preserved after confirmation. One authored lesson with written examples. Still needed: bundled human-recorded examples and manual hardware-audio verification. Simulated audio is confined to tests.

Implement shared session state and platform boundaries for audio/permissions; native recorder must release resources on interruption, navigation, cancellation, and errors. Timer derives from elapsed time, not UI ticks. Denied permission, low storage, and interrupted recordings need recoverable states.

Acceptance: complete the session on a real Android device; play both recordings; test denial, interruption, and cancellation. Never mark an unfinished recording as saved or a lesson completed prematurely.

## Part 3 — Save, resume, and measure progress (core implemented)

Implemented: atomic private session storage, resume after restart, historical recordings and assessments for all completed days, per-day notes, day 7/14 checkpoint comparison, and confirmed deletion of all practice. Version 1 Day 1 data remains readable and migrates on save. Individual recording deletion is deferred.

Persist session metadata, self-check answers, recording references, and completion locally. Storage behind a shared repository contract; Android file implementation initially. Resume unfinished sessions after app restart. Day 7/14 repeated and unfamiliar prompts, comparison view, personal wins, and individual/all-data deletion. On-device storage and no-backup behaviour verified.

Acceptance: process death restores a coherent session; original baseline is immutable; deleting audio removes its references; comparison labels self-assessments honestly. No account or cloud sync.

## Part 4 — Complete the curriculum (written content implemented)

Implemented all 14 sessions with fictional opinion scenarios, contrasting written examples, focused self-checks, scaffolds, fresh application prompts and real-life tasks. Vocabulary introductions have recorded use and retrieval two and seven sessions later, with explicit hint tracking. All recalls fit within session 14. Checkpoints are a distinct flow with repeated baseline and unfamiliar prompts. Still needed: bundled human-narrated examples and human editorial review.

Acceptance: every session is usable offline and fits the intended duration. Human editorial review for clarity, fairness, and progressive difficulty. Date-sensitive briefs have reviewed dates. Missing recall dates beyond day 14 must have an explicit product decision.

## Part 5 — Reminders and settings (core implemented)

Implemented: optional daily local reminders (off by default), time selection, permission handling, blocked-channel status, Android notification settings link, test notification and storage explanation. One inexact alarm follows local time and is replaced on edits, cancelled on opt-out, and restored after reboot, timezone/clock changes and app updates. Preferences persist locally with backups excluded. Shared reminder controller and platform contract keep the future iOS scheduling adapter separate. Reminders continue until disabled, including after the programme ends.

Still needed: real notification-delivery checks, help, privacy and terms. Reminder screen/native tests pass on the phone, alongside shared opt-in/permission/cancellation/failure tests and JVM timezone/DST tests.

Acceptance: opt-out cancels scheduled reminders; changes replace rather than duplicate reminders; verify timezone changes and Android restart behaviour. Never upload recordings as analytics.

## Part 6 — Free v0 pilot and release

All 14 sessions are free in v0. No Pro tier, purchase flow, billing integration or entitlement system. Validate whether users return, finish practice and notice useful changes in daily communication before deciding on monetisation.

Release work: app signing, policy disclosures, recording/backup privacy checks, accessibility (TalkBack/font scaling), background/resume tests, small-screen testing, offline and storage-failure tests. Consent-aware minimal funnel analytics may record events, never speech content. No guaranteed day-3 improvement claims.

Acceptance: the full programme is accessible without payment; accessibility and real-device audio checks pass; pilot feedback informs the next version.

## Future improvements — Encouragement during difficult practice

Add brief, contextual affirmations that help users follow the exercise when speaking, listening to themselves, or changing a familiar habit feels uncomfortable. This is planned work, not part of the current release.

- Before the first recording: “It’s supposed to feel uncomfortable at first. Give it a try.”
- Before listening back: “Hearing yourself can feel awkward. Listen for just one thing.”
- Before a retry: “It’s supposed to be hard. You’re practising a new habit.”
- At counterintuitive instructions, such as stopping after one clear reason: “Try it as written, even if it feels counterintuitive. Listen back and judge for yourself.”
- After a “Not yet” self-assessment: “Not yet is a useful observation. Pick one thing to change.”

Use one short inline message at the relevant moment, with restrained frequency. Avoid extra required dialogs, repeated slogans, or promises that discomfort guarantees improvement. Keep the exercise and next action clear.

Acceptance: encouragement appears at appropriate practice moments without adding steps or making screens harder to scan. Users can assess their own attempts without being pressured to report improvement.

## Model choice for development

Default: GPT-6.1 Sol, Medium reasoning. Increase effort for Gradle, persistence and recording lifecycle debugging. Astra is an optional second opinion for a difficult architecture/review question; it is not required for routine implementation. This is a development-tool choice, not an AI integration in SaySharp.

## Post-launch R1 — Core restructuring (first slice implemented)

Started directly at the user's request; a separate R0 validation phase is not a prerequisite for this work. Notion release-board scope is the reference, with implementation delivered in reviewable slices.

Implemented in the first slice:
- Separate app navigation, home/programme, settings, practice, progress and reusable render components. Everything remains in commonMain; Android audio, storage and notification adapters remain separate.
- Typed app destinations saved by name, retaining existing analytics screen names.
- One shared practice sequence for controller progression and the UI step counter, with recording and self-assessment gates retained.
- Three welcome pages explain the problem, desired outcome and practice method. Today shows one concrete instruction and a start/continue action; the chosen goal stays visible.
- Programme sections are Speak Clearly (1–7) and Speak With Conviction (8–14).
- Contrasting examples appear after the initial attempt. Unsure reveals one specific listening criterion, and retries focus on one correction. Playback labels consistently distinguish First attempt and Retry.

Compatibility: stored stage/slot names, session JSON versions, recording IDs, lesson prompts, self-check questions and completed-day history are unchanged. Existing in-progress sessions resume at their saved stage. No user audio was opened, deleted or uploaded.

Remaining R1 work: refine Days 8–13 and replace Day 12 with Hold Your Ground Under Pushback, with curriculum versioning so historical recordings retain their original meaning; review all instruction wording; device UX/audio/accessibility checks; refresh store assets against the final build; internal-test and production release. R2 streaks/Lite Days, R3 Pressure Practice and R4 monetisation remain separate releases.

R1 first-slice verification: 45 shared host tests and 4 Android unit tests passed. Debug app and instrumentation APKs compile; Android lint passes. The full-programme regression visits every planned step, preserves the original baseline and all 14 completed-day records, and verifies restored state. UI regression assertions now check that model answers are absent before the first attempt and present afterwards. Instrumentation execution and visual/device validation remain pending because no device is connected and no AVD is configured. Source backup before restructuring: `/tmp/saysharp-r1-backup/source-before-r1.tar.gz` (temporary local backup).
