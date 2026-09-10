# Refactoring Implementation Plan - ExerciseAnalyzer.kt

The goal is to refactor `ExerciseAnalyzer.kt` to reduce code duplication, improve readability, and maintain all existing exercise logic. We will extract common patterns into the base class and use constants for landmark indices.

## User Review Required

> [!IMPORTANT]
> This refactoring will centralize visibility and orientation checks. I have categorized the exercises by their required orientation based on your current logic:
> - **Side Profile (LEFT/RIGHT)**: Pushup, Squat, Situp, Plank, SplitSquat.
> - **Front Profile (FRONT)**: Jumping Jack, Side Plank.
>
> I will ensure that the specific feedback messages for each exercise are preserved.

## Proposed Changes

### [Core Framework]

#### [MODIFY] [ExerciseAnalyzer.kt](file:///D:/Workspace/nam3_ky2_dot2/mobi/AIfitness/app/src/main/java/com/google/mediapipe/examples/poselandmarker/analysis/ExerciseAnalyzer.kt)

1. **Define Landmark Constants**: Add a companion object to `BaseExerciseAnalyzer` with named constants for landmarks (e.g., `L_SHOULDER = 11`, `R_HIP = 24`, etc.).
2. **Template Method Pattern**:
    - Introduce `analyze(landmarks: List<NormalizedLandmark>)` as a final method in the base class (or a common wrapper).
    - It will handle:
        - `isFullBodyVisible` check.
        - `detectBodyOrientation` and validation against a `requiredOrientation` property.
        - `hasStarted` / `isReadyState` logic.
    - It will then call a new abstract method `doAnalyze(landmarks, orientation)` which subclasses will implement.
3. **Helper Methods**:
    - `getSideLandmark(landmarks, orientation, leftIdx, rightIdx)`: Returns the landmark corresponding to the visible side.
    - `updateTimedProgress()`: Centralizes the second-counting logic used in Plank and Side Plank.
    - `createResult(...)`: A helper to create `AnalysisResult` using current state.

### [Exercise Analyzers]

- Each subclass will be simplified to only contain its core movement detection logic inside `doAnalyze`.
- `JumpingJackAnalyzer` and `SidePlankAnalyzer` will override the `requiredOrientation` to `FRONT`.
- The landmark index magic numbers will be replaced with constants.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors.
- Verify that the `create` factory method still works correctly for all 7 IDs.

### Manual Verification
- I will verify each exercise's specific logic (angles, thresholds, feedback strings) against the original code to ensure 100% fidelity.
