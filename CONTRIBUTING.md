# Contributing to Fitness For You

Thank you for your interest in contributing to Fitness For You. We welcome contributions, bug fixes, documentation improvements, and feature proposals.

This guide outlines our development workflow, coding standards, and repository practices.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [How to Add a New Exercise](#how-to-add-a-new-exercise)
- [Code Style & Standards](#code-style--standards)
- [Security & Sensitive Data Rules](#security--sensitive-data-rules)
- [Submitting Pull Requests](#submitting-pull-requests)

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/truongvu-25/AIFitness1.git
   cd AIFitness1
   ```

2. Configure Firebase by placing your own `google-services.json` in the `app/` directory:
   ```text
   app/google-services.json
   ```

3. Build the debug APK:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

4. Open the project in Android Studio, sync Gradle, and run the `app` module on a physical Android device or emulator with camera support.

## Development Workflow

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make focused, incremental commits with clear commit messages in English.
3. Verify that the app builds cleanly before opening a Pull Request.

## How to Add a New Exercise

To add a new exercise to the app:

1. Place an offline MP4 tutorial video in `app/src/main/assets/videos/your_exercise.mp4`.
2. Add an `ExerciseDetails` entry to `initializeExerciseDatabase()` in `FitnessApplication.kt`:
   ```kotlin
   ExerciseDetails(
       id = "lunge",
       name = "Chân Trước Chân Sau (Lunge)",
       description = "Bước chân trước gập gối 90 độ, giữ lưng thẳng.",
       videoUrl = "asset:///videos/lunge.mp4",
       isTimed = false,
       unit = "lần"
   )
   ```
3. Extend `BaseExerciseAnalyzer` in `ExerciseAnalyzer.kt` to calculate joint angles and rep states.
4. Register the new analyzer in `BaseExerciseAnalyzer.create()`.

## Code Style & Standards

- Follow standard Kotlin coding conventions.
- Use View Binding (`FragmentXxxBinding`) instead of `findViewById`. Nullify binding in `onDestroyView()` (`_binding = null`).
- Keep shared Firestore data classes explicit in `Models.kt`.
- Keep screen-specific UI logic inside its corresponding Fragment.
- Avoid non-null assertions (`!!`) where possible; use null-guards (`if (_binding == null || !isAdded) return`).

## Security & Sensitive Data Rules

- Do not commit `app/google-services.json`.
- Do not commit `local.properties` or `.idea/` workspace files.
- Do not commit release signing keys (`.jks`, `.keystore`) or credentials.
- Do not hardcode API keys or personal data in source files.

## Submitting Pull Requests

- Verify the debug build succeeds (`.\gradlew.bat assembleDebug`).
- Ensure no sensitive local configuration files or keys are tracked by Git.
- Update documentation (`README.md`, `docs/`) when app flows or requirements change.
- Ensure end-to-end flows run without errors.
