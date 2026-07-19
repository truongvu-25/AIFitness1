# Walkthrough - Exercise Readiness and Full Body Validation

I have updated all exercise analyzers to enforce full-body visibility and a "ready state" before any progress is tracked. This prevents cheating and ensures accurate analysis from a proper starting position.

## Changes Made

### Base Exercise Analyzer

Modified [ExerciseAnalyzer.kt](file:///D:/Workspace/nam3_ky2_dot2/mobi/AIFitness/app/src/main/java/com/google/mediapipe/examples/poselandmarker/ExerciseAnalyzer.kt):
- Added `hasStarted` flag to `BaseExerciseAnalyzer`.
- Updated all 7 subclasses to utilize `isFullBodyVisible` at the start of the `analyze` loop.
- Implemented specific `isReadyState` logic for every exercise.

### Exercise-Specific Ready States

| Exercise | Ready State Condition | Requirement to Start |
| :--- | :--- | :--- |
| **Pushup** | Arms Straight | Arm angle > 160° |
| **Squat** | Standing Upright | Knee angle > 160° |
| **Jumping Jack** | Arms at Sides | Wrists below shoulder level |
| **Sit-up** | Lying Flat | Hip angle > 150° |
| **Plank** | Body Straight | Shoulder-Hip-Ankle angle > 165° |
| **Side Plank** | Body Straight | Shoulder-Hip-Ankle angle > 160° |
| **Split Squat** | Standing Upright | Knee angle > 160° |

## Logic Flow

1.  **Visibility Check**: If the AI cannot see the full body (shoulders to ankles), it displays a warning: "⚠️ Hãy đứng lùi lại để camera quét được toàn thân".
2.  **Ready State**: Before starting, the app prompts the user to enter the correct starting position (e.g., "Hãy đứng thẳng để bắt đầu").
3.  **Analysis**: Once the ready state is detected (`hasStarted = true`), the actual rep counting or timing begins.

> [!IMPORTANT]
> The `hasStarted` flag ensures that a user cannot "cheat" by starting half-way through a rep. They must establish a neutral starting position first.

## Verification

-   **Visibility**: Verified that the visibility threshold (0.6f) correctly detects when limbs are out of frame.
-   **Anti-Cheat**: Confirmed that progress only increments after the specific `isReadyState` is met for each exercise type.
