# Contributing to Fitness For You

Thank you for your interest in contributing to **Fitness For You**! We welcome contributions, bug fixes, documentation improvements, and feature proposals.

This guide outlines our development workflow, coding standards, and repository practices.

---

## 📋 Table of Contents

- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [How to Add a New Exercise](#how-to-add-a-new-exercise)
- [Code Style & Standards](#code-style--standards)
- [Security & Sensitive Data Rules](#security--sensitive-data-rules)
- [Submitting Pull Requests](#submitting-pull-requests)

---

## 🚀 Getting Started

1. **Fork & Clone the Repository**:
   ```bash
   git clone https://github.com/truongvu-25/AIFitness1.git
   cd AIFitness1
   ```

2. **Configure Firebase**:
   Place your own demo `google-services.json` in the `app/` directory:
   ```text
   app/google-services.json
   ```

3. **Build the Debug APK**:
   ```bash
   # On Windows (PowerShell)
   .\gradlew.bat assembleDebug

   # On macOS / Linux
   ./gradlew assembleDebug
   ```

4. Open the project in **Android Studio**, sync Gradle, and run the `app` module on a physical Android device or emulator with camera support.

---

## 🔄 Development Workflow

1. **Create a Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make focused, incremental commits with clear commit messages.
3. Verify that the app builds cleanly before opening a Pull Request.

---

## 🏋️ How to Add a New Exercise

To add a new exercise (e.g., *Lunge* or *Burpee*) to the app:

1. **Add Asset Video**:
   Place an offline MP4 tutorial video in `app/src/main/assets/videos/your_exercise.mp4`.

2. **Register Seed Data in `FitnessApplication.kt`**:
   Add an `ExerciseDetails` entry to the `initializeExerciseDatabase()` list:
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

3. **Implement Analyzer in `ExerciseAnalyzer.kt`**:
   Extend `BaseExerciseAnalyzer` and implement the joint angle calculation and state machine:
   ```kotlin
   class LungeAnalyzer(
       exerciseName: String,
       targetCount: Int,
       isTimed: Boolean,
       unitStr: String
   ) : BaseExerciseAnalyzer(exerciseName, targetCount, isTimed, unitStr) {
       override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
           // Calculate joint angles and evaluate rep state (UP/DOWN)
       }
   }
   ```

4. **Register in Factory**:
   Add a branch to `BaseExerciseAnalyzer.create()` in `ExerciseAnalyzer.kt`:
   ```kotlin
   "lunge" -> LungeAnalyzer(exerciseName, targetCount, isTimed, unitStr)
   ```

---

## 🎨 Code Style & Standards

- **Kotlin Idioms**: Follow standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **View Binding**: Use View Binding (`FragmentXxxBinding`) instead of `findViewById`. Nullify binding in `onDestroyView()` (`_binding = null`).
- **Data Models**: Keep shared Firestore data classes explicit in `Models.kt`.
- **Scoping**: Keep screen-specific UI logic inside its corresponding Fragment.
- **Null Safety**: Avoid non-null assertions (`!!`) where possible; use null-guards (`if (_binding == null || !isAdded) return`).

---

## 🔒 Security & Sensitive Data Rules

To keep the public GitHub repository clean and secure:

- **DO NOT commit** `app/google-services.json`.
- **DO NOT commit** `local.properties` or `.idea/` workspace files.
- **DO NOT commit** release signing keys (`.jks`, `.keystore`) or passwords.
- **DO NOT hardcode** secret keys or real user data in source files.

---

## ✅ Submitting Pull Requests

Before opening a Pull Request, verify the following checklist:

- [ ] Debug build succeeds (`.\gradlew.bat assembleDebug`).
- [ ] No local configuration files or keys are committed (`git status`).
- [ ] Documentation (`README.md`, `docs/`) is updated if features or flows change.
- [ ] New asset files (videos, models) are placed in the correct `assets/` or `res/` folders.
- [ ] End-to-end flows (Login, Profile, 30-day plan, Camera AI, Rest Timer) run without crashes.
