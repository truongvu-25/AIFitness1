# Architecture

TRI FORCE is a single-module, single-activity Android application. `MainActivity` hosts the Navigation graph and shared header/bottom navigation. XML and View Binding implement screens; feature fragments own screen behavior. `MainViewModel` retains pose configuration. There is no separate HTTP backend server in this repository.

## Data ownership

| Data | Persistence |
| --- | --- |
| Account/session | Firebase Authentication SDK |
| Profile and active schedule | Firestore and SDK cache |
| Completed workout and difficulty | Room, then Firestore through sync worker |
| Exercise definitions | Built-in catalogue with optional Firestore overrides |
| Custom templates/settings | SharedPreferences; templates also copied to Firestore |
| Steps | Hardware sensor/preferences and optional Health Connect |
| Camera frames | Device memory only |

Session queries are filtered by Firebase UID. Custom template preferences and some settings remain device-level storage in the existing implementation; they are not a complete account-isolated database.

## Navigation

Welcome routes a persisted session through a profile read. Missing/incomplete profiles go to onboarding; overdue metrics go to BMI update; otherwise the calendar opens. Failed reads must not be treated as missing profiles. Main tabs remain Home, Calendar, Library and Profile. Camera, summary, progress and onboarding hide the main chrome.

The permission detour passes exercise arguments to its replacement camera destination. BMI update has a dedicated calendar action. Logout removes the authenticated navigation stack.

## Workout persistence

1. Camera completion creates one UUID session and commits it locally before showing the summary.
2. WorkManager uses connected-network work and `APPEND_OR_REPLACE`, so an enqueue during upload gets a later pass.
3. A Firestore transaction reads profile, session, day and history before writing.
4. Existing complete sessions are idempotent; legacy difficulty-only documents can be repaired from local sessions.
5. Old sessions cannot complete exercises in a newly reset plan or replace newer history metrics.
6. The DAO acknowledges only the uploaded difficulty value. Concurrent feedback edits remain pending.
7. Health Connect uses session IDs as client record IDs for retry deduplication.

Cloud and Health acknowledgements are separate. Permission refusal does not fail cloud persistence. Retries are bounded; local data remains after failure and later launch/login/enqueue can retry. Firestore transactions require network access; workout completion does not.

Initial profile plus 30 days are one batch. Plan reset updates days, start timestamp and custom-plan metadata together. BMI writes update only metric fields.

## Camera and background work

CameraX uses `KEEP_ONLY_LATEST` and requests 640×480 analysis frames. The helper retains one immutable input until the asynchronous result/error callback; frames arriving during inference are released without allocating another bitmap. MediaPipe runs on one executor, including creation/cleanup needed by the GPU delegate. Camera use cases belong to the view lifecycle. Teardown closes the graph before releasing any remaining input and queues model cleanup before executor shutdown.

Gallery decoding/inference runs off the UI thread. Image decoding is bounded to 2048 pixels on the longest side, including Android 8. Generation checks reject stale results. A cancellable handler draws playback overlays using actual video position; inference result delivery has a separate handler so pausing playback cannot discard a pending completion. Video analysis releases frames/retriever on success and failure.

Auth/profile results capture their original view lifecycle owner. One-shot UI updates wait for `RESUMED` and are canceled if that view is destroyed, so a background response does not leave a loading control stuck or navigate a replacement screen. See Android's [Lifecycle.withResumed contract](https://developer.android.com/reference/kotlin/androidx/lifecycle/Lifecycle).

The step service converts cumulative sensor readings to daily increments and throttles notifications. Android 14+ uses the health foreground-service type. The rest service stops if foreground promotion fails and does not restart a fresh countdown after process death. The reminder receiver bounds asynchronous reads; BootReceiver restores scheduling.
