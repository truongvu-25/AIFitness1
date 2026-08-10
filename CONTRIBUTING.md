# Contributing

Thank you for your interest in Fitness For You.
This guide keeps the project structure clear as the app evolves.

## Run The Project

```powershell
.\gradlew.bat assembleDebug
```

Open the project in Android Studio, sync Gradle, and run the `app` module
on an Android device with camera support.

## Code Style

- Follow the existing Kotlin style in the project.
- Keep screen-specific logic inside its corresponding Fragment.
- Keep shared Firestore data models in `Models.kt`.
- When adding a new exercise, update seed data in `FitnessApplication.kt`.
- When adding AI validation for an exercise, add an analyzer in
  `ExerciseAnalyzer.kt`.
- Do not commit local files, release signing files, or personal Firebase
  configuration.

## Suggested Workflow

1. Create a new branch from the main branch.
2. Keep the change focused and scoped.
3. Run a debug build before submitting changes.
4. Document any required Firestore data or Firebase Rules updates.

## Pull Request Checklist

- Debug build passes.
- No personal configuration files are committed.
- README or `docs/` files are updated when app flow changes.
- New images, videos, or models are placed in the correct assets/res folder.
- Login, plan generation, and workout completion flows still work.
