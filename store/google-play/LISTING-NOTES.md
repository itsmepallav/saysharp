# SaySharp Google Play launch listing

Prepared for the current Android v0.6 programme, in English. No iOS listing is included because the app is not yet available for iOS. Copy is stored as plain text in `en-US/` for pasting into Play Console.

## Positioning

Speaking practice for people who have something to say but ramble, overexplain or lose their point. Lead with a clearer answer and the ability to hear your own progress. The differentiator is one focused skill per chapter, short recordings, retries, self-assessment and a saved baseline.

Primary keyword hypothesis: **speaking practice**. Secondary phrases used naturally: **communication skills**, **speak clearly**, **articulate**, **speaking skills**, **build confidence**. These are relevance hypotheses, not verified search-volume or ranking findings. The reviewed category includes [Wellspoken: Articulation Coach](https://play.google.com/store/apps/details?id=xyz.carbonstudio.wellspoken), which demonstrates competing articulation positioning but does not establish demand or keyword volume.

Avoid targeting English fluency, pronunciation coaching, live debate coaching or speech therapy: the current product does not teach or assess those capabilities. The curriculum practises the structure and clarity of spoken answers, rather than pronunciation mechanics.

## Metadata

- Recommended title: `SaySharp: Speaking Practice` (27 characters).
- Short description: `Speak clearly, cut rambling and build confidence with daily speaking practice` (77 characters).
- Alternative title to test later: `SaySharp: Communication Skills` (30 characters).
- Alternative short description: `Build a sharper voice with 14 days of speaking drills and personal progress` (75 characters).
- Suggested category: Education. Select only relevant tags available in Play Console.
- English listing describes the current English prompts; do not imply multilingual lessons.

The [official field limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) are 30 characters for title, 80 for short description and 4,000 for full description. The short description intentionally omits a trailing period, following [Google's preview-asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). The title identifies the app and its purpose rather than adding a list of keywords.

## Screenshot sequence

Use real current app screens, with one short headline above each and legible UI. Do not invent scores, testimonials, graphs, recordings or improvement data. Capture sample practice on a separate demo device/profile if completed history is needed; never alter the user's personal recordings.

| Order | Headline | Supporting line | Screen to show |
| --- | --- | --- | --- |
| 1 | Less rambling. More impact. | Make your point from the first sentence. | Today, with the Day 1 card |
| 2 | Build a sharper voice | 14 focused chapters. One skill at a time. | Programme |
| 3 | Turn a thought into a clear answer | See an example. Make it your own. | Lesson, showing the written examples |
| 4 | Listen. Refine. Try again. | Compare two attempts at the same question. | Compare, using real demo recordings |
| 5 | Hold your ground. Keep your cool. | Practise disagreement and tough follow-ups. | Day 9 or 11 lesson, from a real demo session |
| 6 | Hear your progress | Revisit your baseline and saved answers. | Progress, using real demo history |

Prioritise the first three screenshots. Keep headings short and make each screenshot explain a different capability. Follow the [current screenshot requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en); a 1080 × 1920 portrait canvas is a suitable planned layout, with actual UI fitted proportionally rather than stretched. Six screenshots are complete in `en-US/graphics/`, with upload order and alt text in `UPLOAD.md`. Screens 4 and 6 contain labelled sample practice, captured through the real UI using an isolated in-memory store and actual synthetic speech files; no improvement results are invented.

Feature graphic concept: **Make every answer count.** Subline: **Focused speaking practice.** Use the app's forest-green and ivory palette with a restrained speech motif, and keep important content central. Required size: 1024 × 500, JPEG or 24-bit PNG without alpha. The finished feature graphic is `en-US/graphics/feature-graphic.png`. A review contact sheet and a ZIP of upload assets are in the same directory.

Store icon: `assets/brand/saysharp-play-icon.png`, 512 × 512 RGBA PNG, full-bleed green with the speech-bubble S. Unlike the launcher preview, it has no baked-in rounded corners or shadow; Google applies those effects. See [Google Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).

## Test after launch

Begin with the recommended listing. Test the short-description alternative and the opening screenshot independently through Play Console's available listing experiments. Judge store conversion alongside the quality of acquired users: first chapter completion, later chapter return and retention. Do not choose a variant solely because it raises installs while completion falls. No fixed conversion lift is predicted.

Useful Analytics events already present: `practice_opened`, `recording_saved`, `chapter_completed`, `programme_completed`. Firebase's automatic retention reports help assess return behaviour. These events do not automatically attribute outcomes to a specific listing experiment; use Play Console's experiment results for variant conversion and analyse downstream behaviour at the appropriate aggregate/cohort level unless attribution is added.

## Remaining publishing inputs

- Public privacy-policy URL reflecting Firebase Analytics/Crashlytics and local-only recordings.
- Developer support email and accurate Play Console Data safety declarations based on the shipped SDKs.

These are publishing inputs, not additional app features. Do not paste this planning document into the store description. The upload-ready text fields are the three `.txt` files under `en-US/`.
