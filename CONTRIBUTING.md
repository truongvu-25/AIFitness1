# Contributing to TRI FORCE

Follow [README](README.md) setup. Branch from the default branch, currently `Nam2`, and keep changes focused on one problem.

## Workflow

1. Reproduce and record device/API and data conditions.
2. Preserve navigation, exercise IDs and stored data unless a migration is explicitly part of the change.
3. Run `testDebugUnitTest lintDebug assembleDebug`; run `connectedDebugAndroidTest` for persistence, navigation or camera changes.
4. Describe the resulting behavior and validation in the PR. Remove personal data from screenshots/logs.

## Conventions

- Follow `.editorconfig` and Kotlin conventions.
- Clear binding, handlers, media and adapters when their view is destroyed.
- Keep decoding/inference off the main thread.
- Persist completed sessions locally and synchronize through `WorkoutSyncWorker`.
- Use field updates or atomic batches/transactions; do not overwrite unrelated profile fields.
- Add meaningful regression tests for defects.
- Reuse shared UI resources and supply supported translations where practical.

## Adding exercises

Add a stable exercise ID, library/catalogue metadata and licensed tutorial asset. Add/register an analyzer in `analysis/ExerciseAnalyzer.kt` for automatic counting. Validate camera orientations, visibility loss, rep/hold transitions and completion. A library entry alone does not implement pose counting.

Never commit real Firebase configuration, signing keys, credentials, local SDK paths, APKs or personal health data. See [SECURITY.md](SECURITY.md).
