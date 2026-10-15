# SaySharp v0.4 design

The interface should make speaking practice feel calm, focused and easy to return to.

- Warm ivory canvas, deep green actions, white surfaces and a restrained clay accent.
- Serif headlines give the app a distinct voice. Sans-serif body text keeps instructions readable.
- Three main destinations: Today, Programme and Progress. Settings lives in the top bar.
- Today presents one next session with a clear start/continue action and honest completion progress.
- Programme uses numbered rows, two sections and a highlighted current lesson.
- Practice shows the position within the session and keeps the primary action at the bottom. Before a recording exists, that action is Start recording. During recording, it becomes Stop and save. Afterwards, it advances the practice. Retakes are secondary.
- The microphone ring shows elapsed recording time, not an invented audio waveform or quality score.
- Playback uses compact, labelled rows. Assessments expose their selected state to accessibility services.
- Progress keeps the baseline, daily recordings, personal reflections and optional real-life notes together. Empty progress explains the next useful action.
- Settings groups the reminder toggle and time; permissions and the test notification remain accessible.
- All content scrolls; buttons have generous touch areas. The main recording action is checked at a 320 × 568 dp viewport with 160% text size.
- Android back leaves practice safely or returns from another tab to Today. The visual system and screens remain shared Kotlin code.

The app uses an ivory speech bubble with an “S” cutout on forest green for its wordmark and launcher icon. The solid silhouette stays legible at small sizes. Adaptive and monochrome icons share the same transparent cutout; legacy round and square icons and all density fallbacks are included. `tools/generate_launcher.py` keeps Android resources, the shared Compose mark and SVG/PNG brand assets consistent. Microphone icons remain on recording controls.

## V0.5 engagement

Three swipeable welcome pages introduce clear answers, respectful conviction and personal evidence of progress. Skip, Next and a fixed start button keep navigation simple. A selected goal changes the home headline; users can change it or replay the welcome from Settings.

Outcome language highlights direct answers, ideas being heard, handling disagreement and real-life wins. The practice sequence remains explicit so users always know what to do next. Prevalence claims and promises of automatic improvement are excluded.

V0.5.1 gives the programme one concise 14-day goal: sharper, clearer, more articulate speaking. Each day has one concrete outcome, reused on Today, Programme, the lesson introduction and the completion screens. These are practice targets, not measured gains or guarantees. Existing descriptive copy is replaced where possible, keeping instructions and self-assessment intact.

V0.5.2 sharpens the welcome and chapter copy: “Less rambling. More impact.”, “Hold your ground. Keep your cool.” and concrete chapter benefits. Short benefit lines replace the repeated “Today's goal” prefix. The programme invites users to build a sharper voice through focused practice; it does not claim verified results, treatment of stuttering or guaranteed improvement.

Celebrations mark sessions 1, 3, 7 and 14, with persistent acknowledgements. They reward completed work without asserting mastery. A milestone collection is available in Progress, and saving a changed real-life note confirms the win. Popups appear at these meaningful moments or when requested, rather than interrupting recordings.

## V0.6.1 feedback

Settings links to Feedback & ideas. Three category chips reveal short, single-choice lists for general feedback, feature requests or problems. A 1–5 overall rating is optional for general feedback. The form scrolls while Send response remains fixed at the bottom, disabled until a topic is chosen. Radio choices expose their selected state to accessibility services. Successful SDK enqueue replaces the form with a concise confirmation and Back to settings; enqueue failure keeps the choice for retry. System Back and the header Back action return to Settings. User-entered text, audio and contact details are not collected through the feedback event.

## Post-launch R1 core flow

This supersedes the earlier personalised home-headline treatment. The chosen goal remains as a short line; Today leads with one concrete skill and a single start/continue action. Onboarding explains problem → outcome → method. The two programme sections are Speak Clearly and Speak With Conviction.

A first attempt follows the instruction and scenario without a model answer on the lesson screen. First Check introduces contrasting examples after recording. Unsure adds a specific listening criterion rather than repeating the example. Retry shows one correction and a compact scaffold. First attempt and Retry keep distinct playback labels; comparison remains the user's judgment.

Screen files are separate from navigation. PracticePlan owns the ordered stages shared by progression and UI progress; PracticeController enforces required recordings/assessments and persists before advancing. Storage and platform adapter contracts are unchanged.
