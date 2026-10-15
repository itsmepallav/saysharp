# SaySharp — Google Play graphics

Prepared for Android v0.6.1, English (en-US), 11 October 2026.

Upload the six numbered PNGs to **Main store listing → Graphics → Phone screenshots**, in filename order. Upload `feature-graphic.png` to **Feature graphic**. Do not upload the contact sheet, source SVGs or raw captures.

| File | Size | Suggested alt text |
| --- | --- | --- |
| `01-today.png` | 1080 × 1920 | SaySharp’s first speaking chapter: lead with your point and start a focused practice session. |
| `02-programme.png` | 1080 × 1920 | A 14-chapter speaking programme covering clear answers, reasons, examples and concise expression. |
| `03-lesson.png` | 1080 × 1920 | A speaking lesson compares a rambling answer with a direct answer that states the point first. |
| `04-compare.png` | 1080 × 1920 | Replay two sample speaking attempts and check whether your first sentence answered the question. |
| `05-disagreement.png` | 1080 × 1920 | An assertiveness lesson shows how to disagree clearly while acknowledging another person’s goal. |
| `06-progress.png` | 1080 × 1920 | A practice journal keeps a sample starting-point recording and two attempts ready to replay. |
| `feature-graphic.png` | 1024 × 500 | SaySharp: Make every answer count. Focused speaking practice, illustrated by two conversation prompts. |

All upload images are opaque, 24-bit RGB PNGs in sRGB. Portrait screenshots use 9:16. Headlines occupy less than 20% of the canvas. The feature graphic has no device frame or store badges.

Requirements checked against [Google Play’s preview-asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). These are ready for upload; final acceptance is determined by Google Play review.

## Capture provenance

The images contain the current shared Compose UI, captured on Android with a 400 × 680 dp app viewport. Android system bars are excluded using consumed system insets; the actual screen capture is proportionally fitted into the artwork. Scrolled views use normal UI scroll actions. No interface elements or app copy were replaced in the image.

The capture harness uses an isolated in-memory store and no-op telemetry. The microphone is never opened. Screens 4 and 6 show explicitly labelled sample practice with actual synthetic speech files and measured durations. No completed sessions, self-assessment results, testimonials or improvement scores were fabricated. Sample files live only in the instrumentation APK and never ship in the production app. Personal recordings and practice state are untouched.

## Editable and reproducible files

- `source/`: self-contained SVG artwork with embedded real UI captures.
- `raw/`: original Android UI captures, 1000 × 1700 pixels.
- `contact-sheet.png`: all seven graphics for review only.
- `tools/generate_store_artwork.cjs`: export script; requires Node.js and `sharp`.
- `app/src/androidTest/.../StoreScreenshotCaptureTest.kt`: isolated capture harness.

To refresh captures, assemble the instrumentation APK, install it with `adb install -r`, and invoke only `StoreScreenshotCaptureTest` with `am instrument`. Read PNGs from the app’s `cache/store-screenshots` directory via `adb exec-out run-as com.saysharp.saysharp cat ...` into `raw/`. Run the export script from the project root. Do not use the Gradle connected-test task on a personal device because its cleanup can uninstall the app.

The ZIP contains only the seven upload PNGs and this guide. The existing Play icon is separately available at `assets/brand/saysharp-play-icon.png`.
