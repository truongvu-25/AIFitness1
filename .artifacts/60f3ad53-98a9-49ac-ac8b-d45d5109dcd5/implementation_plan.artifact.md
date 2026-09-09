# Implementation Plan - Add Guide Lines for Side Plank and Plank

The user wants to add a green guide line from the shoulder to the ankle on the supporting side for the `SidePlank` exercise. This guide line will help users align their body correctly during the exercise. Currently, the line is only shown when the posture is incorrect in `SidePlankAnalyzer` and `PlankAnalyzer`. I will modify them to show the guide line consistently as a reference.

## Proposed Changes

### [Component Name]

#### [MODIFY] [ExerciseAnalyzer.kt](file:///D:/Workspace/nam3_ky2_dot2/mobi/AIfitness/app/src/main/java/com/google/mediapipe/examples/poselandmarker/analysis/ExerciseAnalyzer.kt)

- **SidePlankAnalyzer**:
    - Move landmark identification (`shoulderIdx`, `hipIdx`, `ankleIdx`) and supporting side check before the `!hasStarted` check.
    - Initialize `customLines` earlier and add the green guide line (shoulder to ankle) immediately after identifying the supporting side.
    - Ensure all return paths in `analyze` (except for early failure due to visibility or orientation) return the `customLines`.
- **PlankAnalyzer**:
    - Apply similar logic: move landmark identification up and ensure the green guide line is always returned in `AnalysisResult` once the exercise starts or is being analyzed.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no syntax errors were introduced.

### Manual Verification
- Deploy the app and test the Side Plank exercise.
- Verify that a green line appears from the shoulder to the ankle of the supporting arm as soon as the body is correctly oriented toward the camera.
- Verify the line remains visible both when the posture is valid and invalid.
