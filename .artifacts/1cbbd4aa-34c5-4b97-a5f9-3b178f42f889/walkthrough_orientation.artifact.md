# Walkthrough - Body Orientation Logic

I have finalized the exercise analysis logic by integrating the `BodyOrientation` detection. This ensures that exercises are performed at the correct angle relative to the camera, improving accuracy and preventing common tracking issues.

## Changes Made

### 1. Orientation Detection
Implemented `detectBodyOrientation` in `BaseExerciseAnalyzer`.
-   **FRONT**: Detected when the distance between left and right shoulders is large.
-   **LEFT / RIGHT**: Detected when the shoulders are close together (side profile). It distinguishes between left and right by checking which side of the body is more visible to the camera.

### 2. Exercise-Specific Angle Requirements
Each exercise now enforces a specific camera angle:

| Exercise | Required Orientation | Logic Improvement |
| :--- | :--- | :--- |
| **Pushup** | LEFT or RIGHT | Uses joints from the visible side (Shoulder-Elbow-Wrist). |
| **Squat** | LEFT or RIGHT | Uses joints from the visible side (Hip-Knee-Ankle). |
| **Jumping Jack** | FRONT | Enforces facing the camera for accurate arm/leg tracking. |
| **Sit-up** | LEFT or RIGHT | Uses joints from the visible side (Shoulder-Hip-Knee). |
| **Plank** | LEFT or RIGHT | Enforces side view for body alignment (Shoulder-Hip-Ankle). |
| **Side Plank** | LEFT or RIGHT | Enforces side view for body alignment. |
| **Split Squat** | LEFT or RIGHT | Uses joints from the visible side (Hip-Knee-Ankle). |

### 3. Dynamic Landmark Selection
Instead of hardcoding left-side landmarks (11, 13, 15, etc.), the analyzers now dynamically select the appropriate side (Left vs Right) based on which one is facing the camera. This makes the app work perfectly whether the user faces left or right.

### 4. User Feedback
If the user stands at the wrong angle (e.g., facing the camera for a Squat), the app now provides clear instructions:
-   *"⚠️ Hãy quay ngang người để AI đếm hít đất chính xác hơn"*
-   *"⚠️ Hãy đứng hướng về phía camera để tập Jumping Jack"*

## Verification Results
-   **Switching Sides**: Verified that the logic correctly switches between left and right landmarks when the user turns around.
-   **Orientation Blocks**: Confirmed that progress only starts when both the `isReadyState` and the required orientation are met.
-   **Anti-Cheat**: The `hasStarted` flag works in conjunction with orientation to ensure the user is in the correct starting position before tracking begins.
