# Technical Overview & Implementation Details

This document provides an engineering deep dive into Fitness For You, detailing key technical decisions, algorithms, component responsibilities, and system mechanics.

## Executive Summary

Fitness For You is an AI-powered native Android application engineered in Kotlin. It combines real-time on-device computer vision (Google MediaPipe Pose Landmarker), hardware sensor integrations, background Foreground Services, and cloud synchronization (Firebase Auth & Cloud Firestore) to deliver a personalized 30-day fitness experience.

## Component Traceability Matrix

| Component | Source File | Layout XML / Resource | Technical Responsibilities |
| :--- | :--- | :--- | :--- |
| **Application** | `FitnessApplication.kt` | `AndroidManifest.xml` | Global application setup, Firebase init, master exercise seeding, notification channel creation. |
| **Main Activity** | `MainActivity.kt` | `activity_main.xml` | Single Activity container, `NavHostFragment` setup, dynamic toolbar/bottom nav visibility. |
| **Login Screen** | `LoginFragment.kt` | `fragment_login.xml` | Firebase Auth email/password sign-in, auto-login persistence check, 7-day BMI expiry routing. |
| **Register Screen** | `RegisterFragment.kt` | `fragment_register.xml` | New user account creation via Firebase Auth. |
| **User Survey** | `UserInfoFragment.kt` | `fragment_user_info.xml` | Health metrics collection, BMI calculation, body categorization, 30-day workout plan batch-creation. |
| **Workout Calendar** | `WorkoutCalendarFragment.kt` | `fragment_workout_calendar.xml` | 30-day interactive calendar (6 color states), offline video tutorial popup, exercise start handling. |
| **Camera AI** | `CameraFragment.kt` | `fragment_camera.xml` | CameraX stream binding, MediaPipe live stream pose detection, real-time rep counting, rest timer trigger. |
| **Profile & Pedometer** | `ProfileFragment.kt` | `fragment_profile.xml` | Foreground pedometer view, step/calorie display, 6 AI health advice scenarios, logout. |
| **Update BMI** | `UpdateBmiFragment.kt` | `fragment_update_bmi.xml` | Enforced 7-day height/weight update survey. |
| **Rest Timer** | `RestTimerService.kt` | — | Foreground Service executing a 5-minute ($300\text{ s}$) rest countdown with notification alerts. |
| **Step Counter** | `StepCounterService.kt` | — | Foreground Service reading `Sensor.TYPE_STEP_COUNTER` hardware data & calculating calories. |
| **AI Pose Engine** | `PoseLandmarkerHelper.kt` | `pose_landmarker_*.task` | Wrapper for MediaPipe Pose Landmarker Tasks Vision API. |
| **Skeleton Overlay** | `OverlayView.kt` | — | Custom View drawing 33 landmark points (yellow) and skeletal connection lines (teal). |
| **Motion Analyzers** | `ExerciseAnalyzer.kt` | — | Trigonometric joint angle calculation & state machine rep counters for 7 exercises. |
| **System Alarms** | `NotificationHelper.kt` | — | Schedules 8:00 AM daily workout reminder via `AlarmManager`. |
| **Reboot Receiver** | `BootReceiver.kt` | — | Restores daily workout reminder alarm upon device restart (`BOOT_COMPLETED`). |

## Deep Dive: BMI Engine & 30-Day Workout Generator

### 1. BMI Calculation & Categorization

User height ($h$ in cm) and weight ($w$ in kg) are processed in `UserInfoFragment.kt`:

$$\text{BMI} = \frac{w}{\left(\frac{h}{100}\right)^2}$$

Categorization rules:
- **`GAY` (Underweight)**: $\text{BMI} < 18.5$
- **`CAN DOI` (Balanced)**: $18.5 \le \text{BMI} < 25.0$
- **`THUA CAN` (Overweight)**: $\text{BMI} \ge 25.0$

### 2. Tailored Rest-Day Allocation

Workout plans vary by body category to allow proper muscle recovery:
- **`GAY`**: Rest days on Days 4, 7, 11, 14, 18, 21, 25, 28 (8 rest days total).
- **`CAN DOI`**: Rest days on Days 4, 8, 12, 16, 20, 24, 28 (7 rest days total).
- **`THUA CAN`**: Rest days on Days 5, 10, 15, 20, 25, 30 (6 rest days total).

### 3. Progressive Weekly Difficulty Scaling

Exercise targets scale across the 4 weeks using a multiplier ($M_\text{week}$):

$$M_\text{week} = \begin{cases} 
1.0 & \text{Day } 1 - 7 \quad (\text{Week 1}) \\
1.2 & \text{Day } 8 - 14 \quad (\text{Week 2}) \\
1.4 & \text{Day } 15 - 21 \quad (\text{Week 3}) \\
1.6 & \text{Day } 22 - 30 \quad (\text{Week 4})
\end{cases}$$

Plan documents are committed to Cloud Firestore using a Write Batch operation (`db.batch()`) containing all 30 days in a single atomic network call.

## Deep Dive: Computer Vision & Motion Analysis

### 1. CameraX & MediaPipe Pipeline

`CameraFragment.kt` configures CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` and `OUTPUT_IMAGE_FORMAT_RGBA_8888`. Live frames (~30 FPS) are passed to `PoseLandmarkerHelper.kt`, which runs MediaPipe Pose Landmarker on a dedicated single-thread executor (`backgroundExecutor`).

### 2. 33 3D Joint Extraction & Overlay Rendering

MediaPipe extracts 33 3D body landmarks. `OverlayView.kt` projects these coordinates onto the camera preview, drawing 33 joint markers and connecting skeletal lines.

```text
Landmarks (33 Points) ──► OverlayView (Canvas Paint) ──► Skeleton Visual Feedback
                       └──► ExerciseAnalyzer (Angle Math) ──► Rep/Second Counter
```

### 3. Trigonometric Angle Computation

`ExerciseAnalyzer.kt` calculates the interior angle $\theta$ between three joint points $A(x_a, y_a)$, $B(x_b, y_b)$, and $C(x_c, y_c)$ where $B$ is the vertex:

$$\theta = \left| \text{atan2}(y_c - y_b, x_c - x_b) - \text{atan2}(y_a - y_b, x_a - x_b) \right| \times \frac{180}{\pi}$$

Angle evaluation rules:
- **Push-up**: Elbow angle ($A$: Shoulder, $B$: Elbow, $C$: Wrist). Rep completes when angle transitions from $\le 90^\circ$ to $\ge 160^\circ$.
- **Squat**: Knee angle ($A$: Hip, $B$: Knee, $C$: Ankle). Rep completes when angle transitions from $\le 95^\circ$ to $\ge 160^\circ$.
- **Plank**: Hip angle ($A$: Shoulder, $B$: Hip, $C$: Ankle). Hold time counter increments every $1000\text{ ms}$ while hip angle remains within $160^\circ - 180^\circ$.

## Deep Dive: Zero-Latency Tutorial Video Delivery

To ensure tutorial videos play instantly without network buffering:
1. Video files (`push_up.mp4`, `squat.mp4`, etc.) are bundled directly in `app/src/main/assets/videos/`.
2. When the user taps **"XEM VIDEO"**, `getMediaUri()` checks if the video is an `asset:///` URI.
3. The helper unpacks the MP4 file into the application's `cacheDir` (`File(context.cacheDir, fileName)`).
4. The unpacked cache file URI is passed to `VideoView` inside `dialog_video_player.xml`, providing zero-latency offline looping playback.

## Deep Dive: Resilient Background Execution

### 1. Step Counter Service (`StepCounterService.kt`)
- Inherits from `Service()`.
- Registers `SensorEventListener` for `Sensor.TYPE_STEP_COUNTER`.
- Computes estimated calorie burn: $\text{Calories} = \text{Steps} \times 0.04\text{ kcal}$.
- Broadcasts updates to `ProfileFragment` via `Intent("com.google.mediapipe.examples.poselandmarker.STEPS_UPDATED")`.

### 2. 5-Minute Rest Timer Service (`RestTimerService.kt`)
- Starts automatically upon completing an exercise if pending exercises remain for the day.
- Executes a 5-minute ($300\text{ s}$) `CountDownTimer`.
- Displays live countdown in an ongoing Foreground notification (`CHANNEL_ID = "rest_timer_channel"`).
- Triggers a high-priority alert notification upon expiration.

## Build & Verification Quick Reference

```powershell
# Build debug APK
.\gradlew.bat assembleDebug

# Run static codebase checks
.\gradlew.bat lintDebug
```
