# Technical overview

## Dependencies

| Library | Version | Use |
| --- | --- | --- |
| CameraX | 1.4.2 | Preview and analysis |
| MediaPipe Tasks Vision | 0.10.29 | Pose landmarks |
| Firebase BoM | 32.8.0 | Authentication and Firestore |
| Room | 2.6.1 | Local sessions |
| WorkManager | 2.9.1 | Background sync |
| Health Connect | 1.1.0 | Steps and exercise writes |
| Navigation | 2.5.3 | Fragment navigation |
| Material Components | 1.7.0 | XML UI |

The audit does not perform a broad dependency upgrade. Kapt remains; KSP migration and target-SDK changes need separate validation.

## Exercise analysis

`analysis/ExerciseAnalyzer.kt` contains exercise state machines. Inputs are normalized landmarks; outputs contain progress, feedback, completion and overlay lines. Camera calibration checks body visibility and ready positioning before counting. Lite, Full and Heavy models remain because model selection is exposed in the UI. No fixed FPS is guaranteed.

## UI and persistence conventions

Use shared colors, dimensions and Material styles. Branding uses `drawable-nodpi` to avoid implicit density upscaling. Both locales remain packaged in app bundles. Async callbacks must still belong to a live view/destination; resource cleanup must not block the UI thread.

Exercise IDs and Firestore paths are compatibility contracts. Room remains version 1 with no destructive migration. `WorkoutSyncWorker` is the single cloud writer for completed sessions/feedback; imported cloud data must not replace pending local edits.

## Existing limitations

- Some UI and runtime coaching text remains Vietnamese-only.
- Custom templates and some settings are device-level preferences.
- Health Connect, TTS voices and hardware sensors vary by device.
- Profile/schedule access still depends on Firebase and its cache; the entire app is not offline.
- Signing, backend deployment and store policy validation require external setup.

See [testing](TESTING.md) for acceptance scope.
