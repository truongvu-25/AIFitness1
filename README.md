# Fitness For You

Fitness For You is a native Android fitness app that builds a
personalized workout experience around BMI, real-time pose detection,
daily movement tracking, and Firebase-backed progress storage.

![Fitness For You](app/src/main/res/drawable/fitness_for_you_banner.png)

## Key Features

- Email/password sign up and login with Firebase Authentication.
- Health profile input for name, age, height, and weight.
- Automatic BMI calculation and body-type classification.
- 30-day workout plan generation based on BMI category.
- Workout calendar with rest days, daily progress, and pending exercises.
- Exercise tutorial videos loaded from bundled app assets.
- Real-time body landmark detection with CameraX and MediaPipe.
- Rep counting or hold-time tracking for supported exercises.
- Cloud Firestore progress updates after workout completion.
- 5-minute rest timer between exercises using a Foreground Service.
- Daily step and calorie tracking using a Foreground Service.
- Daily workout reminder scheduled for 8:00 AM.
- BMI refresh prompt after 7 days.

## Technical Highlights

- Processes live camera frames on-device with CameraX and MediaPipe
  Pose Landmarker.
- Keeps exercise-analysis logic separate from camera and MediaPipe setup.
- Uses Firebase Authentication and Cloud Firestore for account,
  profile, plan, and progress data.
- Uses Foreground Services for background step counting and rest timing.
- Uses AlarmManager and BroadcastReceiver for daily reminders and reboot
  recovery.
- Removes local IDE files, Firebase config files, and hardcoded Firebase
  keys from the public repository.

## Tech Stack

- Kotlin.
- Android Native SDK.
- XML layouts.
- Android Jetpack Navigation.
- View Binding.
- CameraX.
- MediaPipe Tasks Vision.
- Firebase Authentication.
- Cloud Firestore.
- Foreground Service.
- AlarmManager and BroadcastReceiver.
- Material Components.

## Project Structure

```text
AIFitness1/
+-- app/
|   +-- src/main/java/.../poselandmarker/
|   |   +-- fragment/                 # Main app screens.
|   |   +-- ExerciseAnalyzer.kt        # Exercise analysis logic.
|   |   +-- PoseLandmarkerHelper.kt    # MediaPipe setup.
|   |   +-- OverlayView.kt             # Skeleton overlay drawing.
|   |   +-- StepCounterService.kt      # Step and calorie tracking.
|   |   +-- RestTimerService.kt        # Rest timer service.
|   |   +-- NotificationHelper.kt      # Workout reminder scheduling.
|   |   +-- FitnessApplication.kt      # Firebase and seed data setup.
|   |   +-- Models.kt                  # Firestore data models.
|   +-- src/main/assets/
|   |   +-- pose_landmarker_*.task     # Pose detection models.
|   |   +-- videos/                    # Exercise tutorial videos.
|   +-- src/main/res/                  # Layouts, drawables, menu, nav.
+-- docs/
|   +-- TECHNICAL_OVERVIEW.md          # Reviewer-focused technical notes.
|   +-- ARCHITECTURE.md                # Architecture and service overview.
+-- README.md
+-- CONTRIBUTING.md
+-- SECURITY.md
+-- LICENSE
```

## App Flow

1. The app starts through `FitnessApplication`.
2. Firebase is initialized from local Android project configuration.
3. Default exercise metadata is seeded into the `exercises` collection.
4. `MainActivity` loads the `NavHostFragment` and opens the login screen.
5. The user signs up or logs in with email and password.
6. If no profile exists, the app asks for health information.
7. The app calculates BMI, classifies the user, and creates a 30-day plan.
8. The user selects a workout day, watches a video, or starts camera mode.
9. CameraX sends frames to MediaPipe for body landmark detection.
10. `ExerciseAnalyzer` counts reps or valid hold time.
11. When the target is reached, Firestore is updated.
12. If more exercises remain, a 5-minute rest timer starts.
13. The profile screen shows health data, steps, and calories.

More details are available in
[docs/TECHNICAL_OVERVIEW.md](docs/TECHNICAL_OVERVIEW.md).

## Setup

Requirements:

- Recent Android Studio version.
- JDK configured through Android Studio or `JAVA_HOME`.
- Physical Android device or emulator with camera support.
- Android SDK API 24 or higher.
- Firebase project with Authentication and Cloud Firestore enabled.

Build command:

```powershell
git clone <repository-url>
cd AIFitness1
.\gradlew.bat assembleDebug
```

Then open the project in Android Studio, sync Gradle, and run the `app`
module on an Android device.

## Firebase Configuration

The public repository does not commit `app/google-services.json`.
To run the app, create your own Firebase project and place the downloaded
config file here:

```text
app/google-services.json
```

Required Firebase services:

- Email/Password Authentication.
- Cloud Firestore.

The source code does not hardcode Firebase API keys.
`FirebaseConfig.kt` calls `FirebaseApp.initializeApp(context)` and relies on
the local `google-services.json` generated configuration at build time.

## Firestore Data Model

```text
exercises/{exerciseId}
users/{uid}
users/{uid}/workouts/day_1
users/{uid}/workouts/day_2
...
users/{uid}/workouts/day_30
```

Meaning:

- `exercises`: shared exercise metadata.
- `users/{uid}`: profile data, BMI, and plan timestamps.
- `workouts/day_N`: daily exercise targets and completion status.

## Android Permissions

Main permissions used by the app:

- `CAMERA`: open the camera for pose detection.
- `INTERNET`: authenticate and sync Firestore data.
- `POST_NOTIFICATIONS`: show notifications on Android 13 and above.
- `ACTIVITY_RECOGNITION`: access step-count sensor data.
- `FOREGROUND_SERVICE`: run step counting and rest timer services.
- `RECEIVE_BOOT_COMPLETED`: restore reminders after device reboot.
- `SCHEDULE_EXACT_ALARM`: schedule workout reminders.

## Documentation

- [Technical Overview](docs/TECHNICAL_OVERVIEW.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)

## Quick Checks

```powershell
.\gradlew.bat assembleDebug
```

```powershell
rg --files -g "*.md" -g "*.txt"
```

## License

This project includes code derived from the TensorFlow MediaPipe Android
sample, released under the Apache License 2.0.

See [LICENSE](LICENSE) for details.
