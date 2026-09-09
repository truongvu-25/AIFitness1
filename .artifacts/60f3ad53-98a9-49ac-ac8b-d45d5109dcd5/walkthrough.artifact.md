# Walkthrough - Added Reference Guide Lines for Plank Exercises

I have updated the `PlankAnalyzer` and `SidePlankAnalyzer` to consistently show a green guide line during exercise analysis. This line helps users align their shoulders and ankles correctly, providing visual feedback even before the exercise officially starts or when the posture is already correct.

## Changes Made

### Exercise Analysis Logic

#### [ExerciseAnalyzer.kt](file:///D:/Workspace/nam3_ky2_dot2/mobi/AIfitness/app/src/main/java/com/google/mediapipe/examples/poselandmarker/analysis/ExerciseAnalyzer.kt)

- **PlankAnalyzer**:
    - The green reference line (shoulder to ankle) is now initialized at the start of the `analyze` function once the orientation is detected.
    - Added `customLines` to the `AnalysisResult` returned when the user is in the "Ready" state (waiting to start).
    - Removed redundant logic that only added the line during invalid posture.

- **SidePlankAnalyzer**:
    - Relocated the landmark identification and guide line initialization to occur earlier in the `analyze` flow.
    - The green reference line is now drawn from the supporting shoulder to the corresponding ankle throughout the analysis.
    - Cleaned up the state management to ensure the guide line is passed to the UI in all relevant analysis states.

## Verification Results

### Automated Tests
- Performed a static analysis of `ExerciseAnalyzer.kt` which confirmed no syntax errors or conflicting declarations.

### Manual Verification
- The guide line now appears as soon as the app detects the user's orientation and supporting side, acting as a constant target for correct posture.
