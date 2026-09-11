# Testing

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

Use `./gradlew` on macOS/Linux. Device tests require a connected device/emulator. Reports: `app/build/reports/`; JVM results: `app/build/test-results/`; device results: `app/build/outputs/androidTest-results/connected/`.

Tests cover chat state, progression targets, daily step rollover/reboot, Room feedback/upload races, UID-scoped queries, navigation arguments/logout and MediaPipe resources. Navigation tests inflate the real graph using a stub fragment navigator; they do not replace screen interaction testing. Room tests use an in-memory database. Tests do not create production Firebase users/workouts.

`ScreenLifecycleTest` additionally opens seven real screens and exercises eight camera open/background/resume/close cycles. `ViewCallbacksTest` checks delayed delivery after resume and discards success/failure results for destroyed views. Screenshot outputs are stored in the test app's external files directory under `audit-screens/`.

## Device acceptance matrix

Record commit, device/API, permissions and result. Include API 26, 33 and 34+, and at least one physical camera device.

| Area | Scenarios | Expected |
| --- | --- | --- |
| Auth | Invalid input, network failure, navigate away, persisted session | Recoverable UI; no stale navigation or overwrite |
| Onboarding | Complete/edit answers, recreate, failed save | Profile and schedule committed together |
| BMI | Weekly prompt, valid save, invalid metrics | Correct calendar route; unrelated data preserved |
| Calendar | Rest/tutorial, reset plan, old pending result | No inherited completion in a new plan |
| Camera | Grant/deny, both lenses, reopen, background, no body | Preserved arguments; resources released |
| Completion | Automatic/manual, repeated taps, offline, exit during save | One durable local session |
| Summary | Rapid feedback during upload, repeat/continue | Latest feedback acknowledged correctly |
| Progress | Slow cloud, cached/empty/cloud-only data | Stable local/remote merge |
| Library | Search/filter, custom template, media dismiss, corrupt/short video | Responsive, recoverable UI |
| Steps | Missing sensor, denied permission, midnight, reboot | Safe fallback and retained recorded totals |
| Timers | Denied notifications, reboot, offline alarm, process death | Bounded receiver; no fresh sticky countdown |
| Health | Missing provider, deny/revoke, reconnect | Optional feature does not block cloud storage |
| UI | Small phone, landscape, large font, keyboard, VI/EN | Readable and reachable controls |
| Accounts | Logout/back, switch account, pending results | Cleared auth stack; UID-scoped sessions |

## Performance

Use Android Studio Profiler or `adb shell dumpsys meminfo PACKAGE` during repeatable camera open/close and media sequences. Use `adb shell dumpsys gfxinfo PACKAGE framestats` for frame timing. Separate model startup from steady-state inference. Emulator results do not establish universal device FPS.

## Backend

The local rule suite uses Node 24, JDK 21 and the Firestore Emulator:

```bash
cd tests/firestore
npm ci
npm test
```

The suite tests unauthenticated/cross-account rejection, owner profile plus 30-day batches, catalogue permissions and session-transaction permissions against the checked-in rules. It uses only `demo-tri-force` on localhost. The first run downloads the emulator; it does not require a Firebase login. CI runs the same suite.

Also run ownership/transaction scenarios in [FIREBASE.md](FIREBASE.md) against an isolated configured project. Emulator tests do not establish the deployed production rules or availability, and the JavaScript transaction-permission test does not execute the Kotlin sync worker.
