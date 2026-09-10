# Tri Force implementation notes

[Overview](../README.md) · [Architecture](ARCHITECTURE.md) · [Verification commands](SETUP.md#verification)

This document describes the current code, including its implementation limits. Paths below use `app/src/main/java/com/google/mediapipe/examples/poselandmarker/` as the source root.

## Pose pipeline

`CameraFragment` binds CameraX preview and image analysis, using `STRATEGY_KEEP_ONLY_LATEST` and RGBA frames. `PoseLandmarkerHelper` handles rotation/front-camera transforms and MediaPipe running modes. The helper supports Full, Lite, and Heavy pose models with CPU/GPU delegate options; its defaults are Full and CPU.

MediaPipe supplies 33 body landmarks. `OverlayView` renders landmark connections and analyzer guide lines. `BaseExerciseAnalyzer` checks visibility, estimates front/side orientation using shoulder positions, checks a starting pose, and dispatches to an exercise state machine. The camera screen also requires a stable starting pose during calibration.

The helper exposes image, video, and live-stream modes. Gallery analysis is separate from the guided camera workout flow. There is no measured frame-rate guarantee in this repository.

## Exercise analysis

Angles are calculated from the normalized two-dimensional landmark coordinates:

```text
angle = abs(atan2(C.y - B.y, C.x - B.x)
          - atan2(A.y - B.y, A.x - B.x)) * 180 / pi
if angle > 180: angle = 360 - angle
```

These are implementation thresholds, not validated biomechanical measurements.

| Analyzer | Current counting logic after setup |
| --- | --- |
| Push-up | Enter down state below 90° elbow angle with straight knees; count when returning above 160° |
| Squat | Both knee angles below 100° enter down state; both above 160° complete a rep |
| Jumping jack | Hands above shoulders and feet spread enter open state; hands down and feet closed complete a rep |
| Sit-up | Bent knees and body angle below 130° enter raised state; return to at least 165° with bent knees to count |
| Plank | Advance timer when body angle exceeds 165° and both knees are at least 160° |
| Side plank | Detect supporting elbow, then advance timer with body angle at least 170° and straight knees |
| Split squat | Both knees below 90° enter down state; both above 150° complete a rep |

Jumping jack and side plank request front orientation; the other analyzers accept a side orientation. The orientation estimate is based on image geometry and visibility, so camera placement matters.

Timed analyzers increment progress in approximately one-second steps when their valid-form path runs. The shared timer timestamp is not reset on every invalid-form path; exact accumulated valid-hold time should be checked on a device before changing or making stronger claims about this behavior.

## Form feedback and speech

`CameraFragment` samples analyzer feedback, excludes selected setup messages, and derives a form score from the share of sampled feedback marked with the correct-form color. It also collects frequently occurring feedback strings. This is a heuristic score based on analyzer output.

Voice coaching uses Android TextToSpeech with Vietnamese language/voice selection. It announces repetitions, timed progress at five-second intervals, completion, and throttled posture feedback. Voice availability depends on the device's speech engine. Selected male/female voices fall back to available Vietnamese voices.

## Profile questionnaire and plan generation

The onboarding chat is driven by `ChatQuestions` and `ChatFlowController`. Answers include profile metrics, fitness level, goals, and self-reported ability ranges. The controller supports answer editing and preloading.

`UserInfoFragment` computes BMI as weight in kilograms divided by height in meters squared, then stores one of the existing data keys `GAY`, `CAN DOI`, or `THUA CAN`. These keys influence exercise selection and rest days.

Initial plans contain 30 day documents written in a Firestore batch. Their targets combine:

- Week multipliers: 1.0, 1.2, 1.4, and 1.6 across days 1–7, 8–14, 15–21, and 22–30.
- Fitness-level multipliers: 0.8 for beginner, 1.3 for advanced, otherwise 1.0.
- Parsed ability ranges and goal-specific exercise biases.
- Minimum targets of five repetitions or ten seconds.

Rest-day lists vary by the stored BMI category. Home/calendar screens also handle preset and custom plans. The canonical catalog and three weekly presets live in `ExerciseCatalog`; adding an exercise requires updating its analyzer and resources as well.

## Session summaries and progression

Camera completion creates a `WorkoutSession` and saves it to Room before navigating to the summary. Metrics include exercise ID, counts, duration, form score/issues, automatic-completion flag, and completion time.

The summary accepts `TOO_EASY`, `JUST_RIGHT`, or `TOO_HARD` feedback. `ProgressionAdvisor` calculates a rounded 10% adjustment, with a minimum change of one:

- Form score 1–54 reduces the target.
- Too easy increases the target only when the current target was reached and form score is at least 70.
- Too hard reduces the target.
- Other cases retain the target; a nonpositive input target returns zero.

A user can apply a changed recommendation to matching exercises in upcoming days. Difficulty is updated locally and also sent through a direct Firestore write in the summary. Background synchronization separately handles pending sessions; the existing-session transaction path avoids increasing the session count again.

## Tutorials, reminders, and steps

Tutorial URIs resolve to the seven bundled MP4 files in `assets/videos/`. Playback implementations in the screens use Android media APIs and local assets/cache. Playback is available without downloading a video at runtime; startup latency still depends on the device.

`NotificationHelper` persists the reminder configuration and schedules a daily alarm; `WorkoutReminderReceiver` checks pending work and `BootReceiver` restores scheduling after reboot. `RestTimerService` maintains a five-minute foreground countdown.

`StepCounterService` uses `TYPE_STEP_COUNTER`, persists daily state, and broadcasts updates to the UI. Health Connect can supply daily aggregate steps separately.

## Existing verification coverage

JVM tests cover questionnaire traversal/editing, canonical exercise IDs, preset plan structure, weekday resource mappings, and progression recommendations. The repository also retains a basic sample unit test and an instrumented package-name test.

These tests do not establish camera accuracy, Firestore rule correctness, cross-account privacy, speech availability, or background behavior across devices. Follow the manual scenarios in [SETUP.md](SETUP.md#verification) when modifying those areas.

