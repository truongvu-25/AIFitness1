# Stabilization audit — 2026-09-11

This record describes the stabilization change on `Nam2`, starting from `76e8f4f`. It is a verification record for the tested configuration, not a guarantee that every device, account or deployed Firebase environment is error-free.

## Scope and compatibility

The existing onboarding → calendar → permission/calibration → workout → summary flow, exercise analyzers, exercise identifiers, application ID and Room schema are retained. Main tabs, bundled demonstrations, pose-model selection and optional Health Connect integration remain available.

| Area | Changes |
| --- | --- |
| Navigation | Preserve exercise arguments through permission screens; correct BMI return action; clear authenticated history on logout |
| Auth/profile | Defer results until the original view resumes; discard destroyed-view callbacks; keep failed profile reads retryable |
| Profile/plan writes | Create profile and 30 days atomically; reset days and plan metadata together; update only relevant BMI fields |
| Workout storage | Persist locally before summary; route difficulty through the same sync writer; retain feedback changed during upload; import without replacing pending edits |
| Cloud history | Repair legacy partial session documents; avoid duplicate session increments; protect newer history and newly reset plans from older uploads |
| Camera | Bind CameraX to the view; close analyzer/model/executor resources; retain one immutable input until native inference finishes |
| Gallery | Decode and infer off the UI thread; bound image dimensions; handle corrupt/short videos; reject stale results; separate playback cancellation from result delivery |
| Background features | Handle daily step rollover/reboot and missing permissions/sensors; throttle step notifications; handle foreground-start failures; bound reminder reads |
| UI/resources | Use shared BMI button styling, enlarge camera touch targets, add camera accessibility labels, avoid density scaling of logos; remove verified unreferenced resources |
| Build/repository | Consolidate AGP configuration; correct a JPEG stored with a PNG extension; package both locales; provide example config, CI, contributor templates and current documentation |

## Verification results

Local environment: Windows, JDK 21.0.10, Gradle 8.14.3, AGP 8.11.0, compile SDK 36. Device tests ran on the Pixel 10 Pro XL Android 17 x86_64 emulator with 16 KB pages. CI uses JDK 17 for Android builds and JDK 21/Node 24 for Firestore rules.

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | 15 passed: daily steps (7), progression (7), chat state (1) |
| `connectedDebugAndroidTest` | 12 passed: Room (4), navigation contracts (3), MediaPipe resources (2), screen/camera lifecycle (1), deferred view callbacks (2) |
| Firestore Emulator suite | 5 passed: unauthenticated denial, cross-account denial, owner batch, catalogue permissions, transaction permissions |
| `assembleDebug` | Passed |
| `assembleRelease` | Passed; unsigned APK |
| `lintDebug` | 0 errors, 457 warnings; no new blanket suppression or error baseline |
| Example Firebase config | Debug and release Google Services processing passed in a separate temporary project |
| Repository checks | No detected private-key/token patterns or tracked local Firebase/signing files; relative Markdown links and whitespace checked |

The screen test creates Home, Library, Custom Plan, Profile, Progress, Gallery and BMI views, then performs eight camera open/background/resume/close cycles. Clean screenshots were visually reviewed; two anonymous captures are included in the README. The tests deliberately avoid submitting production profiles or workouts.

A repeated camera run exposed an intermittent native crash during the audit. Input ownership was changed so submitted bitmaps remain alive through inference, and the camera cycle coverage was expanded. A tiny single-frame test alone was insufficient to establish streaming stability.

The Firestore JavaScript suite verifies the checked-in rules and transaction access on `demo-tri-force` at localhost. It does **not** execute the Android Kotlin worker or verify deployed production rules. Room race tests cover the local acknowledgement/import safeguards.

Reports are generated under `app/build/reports/`, `app/build/test-results/` and `app/build/outputs/androidTest-results/connected/`. They are excluded from Git. Reproduction commands and the full acceptance matrix are in [TESTING.md](TESTING.md).

## Remaining validation and limitations

- Production Firebase authentication, existing account data, deployed rules and full offline-to-cloud/Health Connect synchronization still require an isolated configured test account/project.
- Physical-device camera accuracy, both lenses/GPU delegates, real exercise repetitions, sensor behavior and sustained frame timing need device acceptance testing. Emulator success does not establish a universal FPS or memory bound.
- API 26/33/34 devices, large-font/landscape layouts and full Vietnamese/English localization are not exhaustively certified by this run. Remaining lint warnings include hardcoded/generated text, small text, layout suggestions, retained resources and dependency updates.
- The APK remains large (about 284 MiB debug) because demonstration videos and all selectable pose models are bundled. Media was not transcoded or removed from functional flows.
- Custom-plan preferences and some settings remain device-level storage. Profile/schedule reads still depend on Firebase and its cache.
- Release signing, target-SDK/store compliance and deployment are separate from this source stabilization. The release APK is unsigned; no Firebase rules were deployed by this audit.

## Implementation references

- [Android lifecycle result delivery](https://developer.android.com/reference/kotlin/androidx/lifecycle/Lifecycle)
- [MediaPipe bitmap input contract](https://ai.google.dev/edge/api/mediapipe/java/com/google/mediapipe/framework/image/BitmapImageBuilder)
- [App architecture and data ownership](ARCHITECTURE.md)
- [Firebase setup and rule scope](FIREBASE.md)
