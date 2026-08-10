# Architecture

This document describes the high-level technical structure of Fitness For You.
Flow-level implementation notes are available in `docs/TECHNICAL_OVERVIEW.md`.

## Android Components

```text
FitnessApplication
+-- MainActivity
    +-- NavHostFragment
        +-- LoginFragment
        +-- RegisterFragment
        +-- UserInfoFragment
        +-- WorkoutCalendarFragment
        +-- CameraFragment
        +-- ProfileFragment
        +-- UpdateBmiFragment
        +-- GalleryFragment
```

Background components:

- `WorkoutReminderReceiver`: receives daily workout reminder alarms.
- `BootReceiver`: restores reminders after device reboot.
- `StepCounterService`: Foreground Service for steps and calories.
- `RestTimerService`: Foreground Service for 5-minute rest timing.

## Main Data Flow

```text
Firebase Auth
+-- uid
    +-- users/{uid}
        +-- workouts/day_N
```

```text
FitnessApplication
+-- exercises/{exerciseId}
```

`exercises` stores shared exercise metadata.
`users/{uid}` stores the user's health profile.
`users/{uid}/workouts` stores the user's workout plan and progress.

## AI Pose Detection Flow

```text
CameraX ImageAnalysis
+-- CameraFragment.detectPose()
    +-- PoseLandmarkerHelper.detectLiveStream()
        +-- MediaPipe Pose Landmarker
            +-- OverlayView.setResults()
            +-- ExerciseAnalyzer.analyze()
```

CameraX reads live camera frames.
MediaPipe returns body landmarks.
OverlayView draws the skeleton.
ExerciseAnalyzer checks exercise form and returns progress.

## Exercise Analysis

The factory in `ExerciseAnalyzer.kt` selects an analyzer by `exerciseId`.

```text
pushup      -> PushupAnalyzer
squat       -> SquatAnalyzer
jumpingjack -> JumpingJackAnalyzer
situp       -> SitupAnalyzer
plank       -> PlankAnalyzer
sideplank   -> SidePlankAnalyzer
splitsquat  -> SplitSquatAnalyzer
```

Rep-based exercises use movement states such as up/down or open/closed.
Plank and Side Plank use valid hold time.

## Services And Notifications

`NotificationHelper` uses `AlarmManager` to schedule a reminder at 8:00 AM.
`WorkoutReminderReceiver` checks for pending exercises before showing a
notification.

`StepCounterService` uses `Sensor.TYPE_STEP_COUNTER`.
The service stores step data in `SharedPreferences` and estimates calories:

```text
calories = steps * 0.04
```

`RestTimerService` runs a 5-minute `CountDownTimer`.
When the timer ends, it sends a high-priority notification so the user can
return to the next exercise.

## Public Repository Configuration

The public repository does not track:

- `.idea/`.
- `local.properties`.
- `app/google-services.json`.
- Release signing files.
- Build output.

Anyone cloning the project must provide their own Firebase
`app/google-services.json` file.
