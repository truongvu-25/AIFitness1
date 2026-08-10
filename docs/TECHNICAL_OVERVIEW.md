# Technical Overview

Fitness For You is a native Android fitness app built with Kotlin.
It combines Firebase, CameraX, MediaPipe Pose Landmarker and Android
Foreground Services to create a personalized workout experience.

This document is written for code reviewers and recruiters who want to
understand the technical decisions behind the project.

## Product Scope

The app helps users:

- Create an account.
- Enter personal health information.
- Calculate BMI.
- Receive a 30-day workout plan.
- Watch exercise tutorial videos.
- Train with real-time AI pose detection.
- Track completed exercises.
- Count daily steps and estimated calories.
- Receive daily workout reminders.

## Core User Flow

```text
Login / Register
└── User profile input
    └── BMI calculation
        └── 30-day workout plan
            └── Workout calendar
                ├── Tutorial video
                └── Camera AI workout
                    └── Firestore progress update
```

Important files:

- `LoginFragment.kt`: login and route decision after authentication.
- `RegisterFragment.kt`: account creation.
- `UserInfoFragment.kt`: profile input, BMI and workout plan generation.
- `WorkoutCalendarFragment.kt`: 30-day plan UI and exercise selection.
- `CameraFragment.kt`: real-time camera workout flow.
- `ProfileFragment.kt`: user profile, step count and calories.

## AI Pose Detection Pipeline

```text
CameraX ImageAnalysis
└── CameraFragment.detectPose()
    └── PoseLandmarkerHelper.detectLiveStream()
        └── MediaPipe Pose Landmarker
            ├── OverlayView
            └── ExerciseAnalyzer
```

Responsibilities:

- `CameraFragment.kt` owns the camera screen and passes frames to AI.
- `PoseLandmarkerHelper.kt` configures MediaPipe models and delegates.
- `OverlayView.kt` draws detected landmarks and skeleton connections.
- `ExerciseAnalyzer.kt` converts pose landmarks into workout progress.

Supported exercise analyzers:

- Push-up.
- Squat.
- Jumping Jack.
- Sit-up.
- Plank.
- Side Plank.
- Split Squat.

The analyzer layer is separated from the camera layer.
This makes it easier to add new exercises without rewriting CameraX
or MediaPipe setup code.

## Firebase Integration

The app uses:

- Firebase Authentication for email/password login.
- Cloud Firestore for profile, exercise metadata and workout progress.

Firestore structure:

```text
exercises/{exerciseId}
users/{uid}
users/{uid}/workouts/day_1
users/{uid}/workouts/day_2
...
users/{uid}/workouts/day_30
```

The global `exercises` collection stores reusable exercise metadata.
Each user owns their own workout documents under `users/{uid}/workouts`.

Firebase is initialized through `FirebaseApp.initializeApp(context)`.
The repository does not commit `app/google-services.json`, so anyone
running the app must provide their own Firebase configuration.

## Android Services

### StepCounterService

`StepCounterService.kt` is a Foreground Service that listens to
`Sensor.TYPE_STEP_COUNTER`.

It calculates:

```text
calories = steps * 0.04
```

The result is saved in `SharedPreferences` and broadcast to
`ProfileFragment` so the UI can update without polling.

### RestTimerService

`RestTimerService.kt` is a Foreground Service for a 5-minute rest timer.
It starts after a workout is completed if there are remaining exercises
in the same day.

The service keeps counting even if the user leaves the app.
When the timer ends, it sends a high-priority notification.

### Workout Reminder

`NotificationHelper.kt` schedules a daily reminder at 8:00 AM.
`WorkoutReminderReceiver.kt` checks whether the current user still has
pending exercises before showing a notification.
`BootReceiver.kt` restores the reminder after device reboot.

## Data Models

`Models.kt` contains the Firestore-facing data classes:

- `ExerciseDetails`: shared exercise metadata.
- `UserExercise`: exercise target and completion status per user.
- `WorkoutDay`: one day in the 30-day plan.
- `Exercise`: UI-ready merged exercise model.
- `UserProfile`: personal profile, BMI and timestamps.

The model layer keeps Firestore data explicit and easy to inspect.

## Security And Public Repo Notes

The repository is prepared for public GitHub usage:

- `.idea/` is ignored.
- `local.properties` is ignored.
- `app/google-services.json` is ignored.
- Release signing files are ignored.
- Firebase API keys are not hardcoded in source code.

Review `SECURITY.md` before connecting a production Firebase project.

## Potential Improvements

- Add unit tests for BMI and workout-plan generation.
- Add UI tests for login and workout calendar flows.
- Replace the demo calorie formula with a more personalized calculation.
- Add a local cache for workout data when network connection is weak.
- Add screenshots or a demo video to the GitHub README.
