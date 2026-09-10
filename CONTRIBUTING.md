# Contributing to Tri Force

Start with the [README](README.md), [setup guide](docs/SETUP.md), and [architecture](docs/ARCHITECTURE.md). Use your own Firebase development project for runtime checks; the dummy config supports compilation and JVM tests.

## Workflow

1. Create a focused branch from the repository's current development branch.
2. Make a change with a concrete user-visible outcome or a clearly described maintenance purpose.
3. Run the checks relevant to the change. For Kotlin, resources, or build changes:

   ```powershell
   .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

   On macOS/Linux use `bash ./gradlew` with the same tasks.

4. Run affected device flows and record the device/API level and outcome.
5. Open a pull request describing the problem, resulting behavior, and verification. Update docs when setup or behavior changes.

Documentation-only edits should verify links, branding, and claims against source. Do not include generated APKs or personal configuration in a pull request.

## Code conventions

- Follow Kotlin conventions and the existing package organization.
- Use View Binding and clear fragment bindings in `onDestroyView`.
- Guard asynchronous UI callbacks against detached fragments/destroyed views.
- Put new UI strings in the Vietnamese default resources and English overrides.
- Keep canonical exercise IDs stable: plans, sessions, aliases, and analyzers share them.
- Keep Firestore serialization compatibility in mind, including existing boolean aliases.
- Preserve copyright/license headers from upstream files.
- Keep the current Tri Force name and app logo consistent across documentation and UI.

## Adding an exercise

1. Add a definition in `model/ExerciseCatalog.kt`: stable ID, localized resource references, video URI, timed/repetition behavior, category, and target.
2. Add Vietnamese and English strings and a tutorial asset whose redistribution rights are documented.
3. Implement a `BaseExerciseAnalyzer` subclass in `analysis/ExerciseAnalyzer.kt` and register it in the factory.
4. Connect feedback strings to localization and check camera calibration instructions for the new exercise.
5. Update plan generation/presets where appropriate and remove any UI assumptions about seven exercises.
6. Update catalog tests and add meaningful behavioral checks for new logic. Verify the analyzer on a device with valid/invalid starting poses and poor visibility.
7. Update the supported-exercise tables and [asset notices](THIRD_PARTY_NOTICES.md).

The old startup Firestore-seeding path is no longer used.

## Repository hygiene

Keep Firebase configs, service-account keys, signing keys/passwords, local SDK paths, environment files, user data, IDE state, and generated work logs out of commits. Use the included example config for public build checks.

Use [SECURITY.md](SECURITY.md) for vulnerability reporting. Describe bugs using synthetic data and redact personal information from logs or screenshots.

