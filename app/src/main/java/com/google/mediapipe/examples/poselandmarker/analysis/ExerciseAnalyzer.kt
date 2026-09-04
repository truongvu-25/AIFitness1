package com.google.mediapipe.examples.poselandmarker.analysis

import android.graphics.Color
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Custom line to draw on overlay
 */
data class CustomLine(
    val startLandmarkIndex: Int,
    val endLandmarkIndex: Int,
    val color: Int
)

/**
 * The result return to UI
 */
data class AnalysisResult(
    val currentProgress: Int,
    val feedback: String,
    val feedbackColor: Int,
    val isComplete: Boolean,
    val customLines: List<CustomLine> = emptyList()
)
/*
* the result orientation
*/
enum class BodyOrientation {
    FRONT,
    LEFT,
    RIGHT
}
/**
 * Father class
 */
abstract class BaseExerciseAnalyzer(
    val exerciseId: String,
    val exerciseName: String,
    val targetCount: Int,
    val isTimed: Boolean,
    val unit: String
)
{
    var currentProgressCount: Int = 0
    var feedback: String = ""
    var feedbackColor: Int = Color.parseColor("#FFCA28") // Warning/Neutral by default
    var lastTimeIncrementMs: Long = 0L
    var hasStarted: Boolean = false


    /**
     * Analyzes the current frame's landmarks and updates progress.
     * Returns the analysis result for the UI.
     */
    abstract fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult

    /**
     * Utility to calculate the angle between three landmarks.
     * Angle at 'b' given points a, b, c.
     */
    protected fun calculateAngle(
        a: NormalizedLandmark,
        b: NormalizedLandmark,
        c: NormalizedLandmark
    ): Double {
        val radians = atan2(c.y() - b.y(), c.x() - b.x()) -
                atan2(a.y() - b.y(), a.x() - b.x())
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }

    protected fun getFeedbackColor(isValid: Boolean): Int {
        return if (isValid) Color.parseColor("#4CAF50") else Color.parseColor("#FFCA28")
    }
    /**
     * full body in frame
     */
    protected fun detectBodyOrientation(
        landmarks: List<NormalizedLandmark>
    ): BodyOrientation {
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        
        val shoulderWidth = abs(leftShoulder.x() - rightShoulder.x())
        
        return if (shoulderWidth >= 0.15) {
            BodyOrientation.FRONT
        } else {
            // Side profile. Check which shoulder is more visible.
            if (leftShoulder.visibility().orElse(0f) > rightShoulder.visibility().orElse(0f)) {
                 BodyOrientation.LEFT
            } else {
                 BodyOrientation.RIGHT
            }
        }
    }
    protected fun isFullBodyVisible(
        landmarks: List<NormalizedLandmark>
    ): Boolean {

        if (landmarks.size != 33) {
            return false
        }

        // Lấy landmark hai bên tay
        val leftShoulder = landmarks[11]
        val leftElbow = landmarks[13]
        val leftWrist = landmarks[15]

        val rightShoulder = landmarks[12]
        val rightElbow = landmarks[14]
        val rightWrist = landmarks[16]

        // Một bên tay phải nhìn rõ
        val leftVisible =
            leftShoulder.visibility().orElse(0f) > 0.6f &&
                    leftElbow.visibility().orElse(0f) > 0.6f &&
                    leftWrist.visibility().orElse(0f) > 0.6f

        // Hoặc một bên tay trái nhìn rõ
        val rightVisible =
            rightShoulder.visibility().orElse(0f) > 0.6f &&
                    rightElbow.visibility().orElse(0f) > 0.6f &&
                    rightWrist.visibility().orElse(0f) > 0.6f

        // Không thấy rõ cả hai bên tay
        if (!leftVisible && !rightVisible) {
            return false
        }

        // Kiểm tra các khớp quan trọng khác
        // Mũi (0)
        if (landmarks[0].visibility().orElse(0f) < 0.5f) return false

        // Kiểm tra theo cặp (Hips, Knees, Ankles)
        // Chỉ cần một bên nhìn rõ là đủ (hỗ trợ quay ngang người)
        val pairs = listOf(
            23 to 24, // Hips
            25 to 26, // Knees
            27 to 28  // Ankles
        )

        for ((left, right) in pairs) {
            val leftVis = landmarks[left].visibility().orElse(0f)
            val rightVis = landmarks[right].visibility().orElse(0f)
            if (leftVis < 0.5f && rightVis < 0.5f) {
                return false
            }
        }

        return true
    }

    abstract fun isReadyState(
        landmarks: List<NormalizedLandmark>
    ): Boolean

    companion object {
        fun create(
            exerciseId: String,
            exerciseName: String,
            targetCount: Int,
            isTimed: Boolean,
            unit: String
        ): BaseExerciseAnalyzer {
            return when (exerciseId.lowercase().trim()) {
                "pushup", "push_up" -> PushupAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "squat" -> SquatAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "jumpingjack", "jumping_jack", "jumping_jacks" -> JumpingJackAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "situp", "sit_up" -> SitupAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "plank" -> PlankAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "sideplank", "side_plank" -> SidePlankAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "splitsquat", "split_squat", "lunges" -> SplitSquatAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                else -> object : BaseExerciseAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit) {
                    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean = true
                    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
                        return AnalysisResult(currentProgressCount, "Bài tập chưa hỗ trợ đếm", Color.GRAY, false)
                    }
                }
            }
        }
    }
}

/**
 * 1. Pushup Analyzer
 * note: left/right
 */
class PushupAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    private var isDown = false

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false

        val (shoulder, elbow, wrist, hip, ankle) = if (orientation == BodyOrientation.LEFT) {
            listOf(landmarks[11], landmarks[13], landmarks[15], landmarks[23], landmarks[27])
        } else {
            listOf(landmarks[12], landmarks[14], landmarks[16], landmarks[24], landmarks[28])
        }

        val armAngle = calculateAngle(shoulder, elbow, wrist)
        if (armAngle <= 160) return false

        val bodyAngle = calculateAngle(shoulder, hip, ankle)
        if (bodyAngle <= 160) return false

        val leftKneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])
        val rightKneeAngle = calculateAngle(landmarks[24], landmarks[26], landmarks[28])

        return leftKneeAngle > 160 && rightKneeAngle > 160
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(currentProgressCount, "Hãy đứng lùi lại để camera quét được toàn thân", Color.parseColor("#FFCA28"), false)
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(currentProgressCount, "Hãy quay ngang người để đếm hít đất chính xác hơn", Color.parseColor("#FFCA28"), false)
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(currentProgressCount, "Hãy bắt đầu ở tư thế chống tay thẳng", Color.parseColor("#FFCA28"), false)
            }
        }

        // Points based on orientation
        val (shoulder, elbow, wrist) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[11], landmarks[13], landmarks[15])
        } else {
            Triple(landmarks[12], landmarks[14], landmarks[16])
        }
        
        val angle = calculateAngle(shoulder, elbow, wrist)
        
        if (angle < 90) {
            isDown = true
            feedback = "Tốt! Bây giờ hãy đẩy lên."
        } else if (isDown && angle > 160) {
            currentProgressCount++
            isDown = false
            feedback = "Đã xong 1 lần! Tiếp tục nào."
        } else if (!isDown) {
            feedback = "Hạ thấp người xuống nữa."
        }
        
        feedbackColor = getFeedbackColor(isDown || angle > 160)
        
        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount)
    }
}

/**
 * 2. Squat Analyzer
 * note: left/right
 */
class SquatAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    private var isDown = false

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false
        
        val (h, k, a) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[23], landmarks[25], landmarks[27])
        } else {
            Triple(landmarks[24], landmarks[26], landmarks[28])
        }
        
        val angle = calculateAngle(h, k, a)
        return angle > 160
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(currentProgressCount, "Hãy đứng lùi lại để camera quét được toàn thân", Color.parseColor("#FFCA28"), false)
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(currentProgressCount, "Hãy quay ngang người để đếm Squat chính xác hơn", Color.parseColor("#FFCA28"), false)
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(currentProgressCount, "Hãy đứng thẳng để bắt đầu", Color.parseColor("#FFCA28"), false)
            }
        }

        // Points based on orientation
        val (hip, knee, ankle) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[23], landmarks[25], landmarks[27])
        } else {
            Triple(landmarks[24], landmarks[26], landmarks[28])
        }
        
        val angle = calculateAngle(hip, knee, ankle)
        
        if (angle < 100) {
            isDown = true
            feedback = "Đã xuống đủ sâu! Đứng dậy nào."
        } else if (isDown && angle > 160) {
            currentProgressCount++
            isDown = false
            feedback = "Tuyệt vời! Tiếp tục squat."
        } else if (!isDown) {
            feedback = "Hạ thấp mông xuống chút nữa."
        }

        feedbackColor = getFeedbackColor(isDown || angle > 160)

        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount)
    }
}

/**
 * 3. Jumping Jack Analyzer
 * note: front
 */
class JumpingJackAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    private var isUp = false

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation != BodyOrientation.FRONT) return false
        
        val leftWrist = landmarks[15]
        val leftShoulder = landmarks[11]
        val rightWrist = landmarks[16]
        val rightShoulder = landmarks[12]
        return leftWrist.y() > leftShoulder.y() && rightWrist.y() > rightShoulder.y()
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {


        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(
                currentProgressCount,
                "Hãy đứng lùi lại để camera quét được toàn thân",
                Color.parseColor("#FFCA28"),
                false
            )
        }


        val orientation = detectBodyOrientation(landmarks)
        if (orientation != BodyOrientation.FRONT) {
            return AnalysisResult(
                currentProgressCount,
                "Hãy đứng hướng về phía camera để tập Jumping Jack",
                Color.parseColor("#FFCA28"),
                false
            )
        }


        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(
                    currentProgressCount,
                    "Hãy đứng thẳng, khép tay và khép chân để bắt đầu",
                    Color.parseColor("#FFCA28"),
                    false
                )
            }
        }


        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]


        val handsHigh =
            leftWrist.y() < leftShoulder.y() &&
                    rightWrist.y() < rightShoulder.y()

        val handsDown =
            leftWrist.y() > leftShoulder.y() &&
                    rightWrist.y() > rightShoulder.y()


        val feetDistance =
            abs(leftAnkle.x() - rightAnkle.x())

        val feetOpen = feetDistance > 0.15f
        val feetClose = feetDistance < 0.12f

        // ===== Jumping Jack State =====
        if (handsHigh && feetOpen) {

            isUp = true
            feedback = "Tốt! Khép tay và chân lại."

        } else if (isUp && handsDown && feetClose) {

            currentProgressCount++
            isUp = false
            feedback = "Tuyệt vời! Tiếp tục nào."

        } else if (!isUp) {

            feedback = "Giơ tay qua đầu và bật mở hai chân."

        } else {

            feedback = "Khép tay và chân về vị trí ban đầu."

        }

        feedbackColor = getFeedbackColor(
            (handsHigh && feetOpen) ||
                    (handsDown && feetClose)
        )

        return AnalysisResult(
            currentProgressCount,
            feedback,
            feedbackColor,
            currentProgressCount >= targetCount
        )
    }
}

/**
 * 4. Sit-up Analyzer
 * note: left/right
 */
class SitupAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    private var isUp = false

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false
        
        val (s, h, k) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[11], landmarks[23], landmarks[25])
        } else {
            Triple(landmarks[12], landmarks[24], landmarks[26])
        }
        
        val angle = calculateAngle(s, h, k)
        return 150 > angle && angle > 90
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(currentProgressCount, "Hãy nằm lùi lại để camera quét được toàn thân", Color.parseColor("#FFCA28"), false)
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(currentProgressCount, "Hãy nằm ngang so với camera để đếm gập bụng", Color.parseColor("#FFCA28"), false)
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(currentProgressCount, "Hãy nằm phẳng để bắt đầu", Color.parseColor("#FFCA28"), false)
            }
        }

        // Points based on orientation
        val (shoulder, hip, knee) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[11], landmarks[23], landmarks[25])
        } else {
            Triple(landmarks[12], landmarks[24], landmarks[26])
        }
        
        val angle = calculateAngle(shoulder, hip, knee)
        
        if (angle < 105) {
            isUp = true
            feedback = "Tốt! Nằm xuống từ từ."
        } else if (isUp && angle > 120) {
            currentProgressCount++
            isUp = false
            feedback = "Gập bụng mạnh lên!"
        } else if (!isUp) {
            feedback = "Kéo người ngồi dậy cao hơn."
        }

        feedbackColor = getFeedbackColor(isUp || angle > 120)

        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount)
    }
}

/**
 * 5. Plank Analyzer (Timed)
 * note: left/right
 */
class PlankAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false

        val (shoulder, elbow, hip, ankle) = if (orientation == BodyOrientation.LEFT) {
            listOf(landmarks[11], landmarks[13], landmarks[23], landmarks[27])
        } else {
            listOf(landmarks[12], landmarks[14], landmarks[24], landmarks[28])
        }

        // Vai - hông - gót thẳng
        if (calculateAngle(shoulder, hip, ankle) <= 165) return false

        // Vai - khuỷu - hông
        val elbowHipAngle = calculateAngle(shoulder, elbow, hip)
        Log.d("PlankAnalyzer", "Shoulder-Elbow-Hip Angle: $elbowHipAngle")
        if (calculateAngle(shoulder, elbow, hip) <= 55) return false


        return true
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(
                currentProgressCount,
                "Hãy nằm lùi lại để camera quét được toàn thân",
                Color.parseColor("#FFCA28"),
                false
            )
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(
                currentProgressCount,
                "Hãy nằm ngang so với camera để tập Plank",
                Color.parseColor("#FFCA28"),
                false
            )
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(
                    currentProgressCount,
                    "Hãy giữ thẳng người để bắt đầu tính giờ",
                    Color.parseColor("#FFCA28"),
                    false
                )
            }
        }

        val (shoulderIdx, hipIdx, ankleIdx) = if (orientation == BodyOrientation.LEFT) {
            Triple(11, 23, 27)
        } else {
            Triple(12, 24, 28)
        }

        val shoulder = landmarks[shoulderIdx]
        val hip = landmarks[hipIdx]
        val ankle = landmarks[ankleIdx]

        // Kiểm tra vai - hông - gót
        val bodyAngle = calculateAngle(shoulder, hip, ankle)

        // Kiểm tra hông - gối - gót hai bên
        val leftKneeAngle = calculateAngle(
            landmarks[23],
            landmarks[25],
            landmarks[27]
        )

        val rightKneeAngle = calculateAngle(
            landmarks[24],
            landmarks[26],
            landmarks[28]
        )
        println("Left Knee Angle: $leftKneeAngle")
        println("Right Knee Angle: $rightKneeAngle")
        // Chỉ tính giờ khi thân và cả hai chân đều thẳng
        val isValid =
            bodyAngle > 165 &&
                    leftKneeAngle >= 160 &&
                    rightKneeAngle >= 160

        val customLines = mutableListOf<CustomLine>()
        if (isValid) {
            val now = System.currentTimeMillis()

            if (now - lastTimeIncrementMs >= 1000L) {
                if (lastTimeIncrementMs != 0L) {
                    currentProgressCount++
                }

                lastTimeIncrementMs = now
            }

            feedback = "Đang giữ chuẩn tư thế!"
        } else {
            feedback = "Hãy giữ thẳng thân và hai chân!"
            // Add green reference line
            customLines.add(CustomLine(shoulderIdx, ankleIdx, Color.GREEN))
        }

        feedbackColor = getFeedbackColor(isValid)

        return AnalysisResult(
            currentProgressCount,
            feedback,
            feedbackColor,
            currentProgressCount >= targetCount,
            customLines
        )
    }
}

/**
 * 6. Side Plank Analyzer (Timed)
 * note: left/right
 */
class SidePlankAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false
        
        val (s, h, a) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[11], landmarks[23], landmarks[27])
        } else {
            Triple(landmarks[12], landmarks[24], landmarks[28])
        }
        
        val bodyAngle = calculateAngle(s, h, a)
        return bodyAngle > 160
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(currentProgressCount, "Hãy nằm lùi lại để camera quét được toàn thân", Color.parseColor("#FFCA28"), false)
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(currentProgressCount, "Hãy nằm ngang so với camera để tập Side Plank", Color.parseColor("#FFCA28"), false)
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(currentProgressCount, "Hãy giữ người thẳng để bắt đầu", Color.parseColor("#FFCA28"), false)
            }
        }

        val (shoulderIdx, hipIdx, ankleIdx) = if (orientation == BodyOrientation.LEFT) {
            Triple(11, 23, 27)
        } else {
            Triple(12, 24, 28)
        }
        
        val shoulder = landmarks[shoulderIdx]
        val hip = landmarks[hipIdx]
        val ankle = landmarks[ankleIdx]
        
        val bodyAngle = calculateAngle(shoulder, hip, ankle)
        val isValid = bodyAngle > 160
        
        val customLines = mutableListOf<CustomLine>()
        if (isValid) {
            val now = System.currentTimeMillis()
            if (now - lastTimeIncrementMs >= 1000L) {
                if (lastTimeIncrementMs != 0L) currentProgressCount++
                lastTimeIncrementMs = now
            }
            feedback = "Tuyệt vời, giữ vững nhé!"
        } else {
            feedback = "Đẩy hông cao lên một chút!"
            customLines.add(CustomLine(shoulderIdx, ankleIdx, Color.GREEN))
        }

        feedbackColor = getFeedbackColor(isValid)

        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount, customLines)
    }
}

/**
 * 7. Split Squat Analyzer
 * note: left/right
 */
class SplitSquatAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) :
    BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    private var isDown = false

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) return false
        
        val (h, k, a) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[23], landmarks[25], landmarks[27])
        } else {
            Triple(landmarks[24], landmarks[26], landmarks[28])
        }
        
        val angle = calculateAngle(h, k, a)
        return angle > 160
    }

    override fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            return AnalysisResult(currentProgressCount, "Hãy đứng lùi lại để camera quét được toàn thân", Color.parseColor("#FFCA28"), false)
        }

        val orientation = detectBodyOrientation(landmarks)
        if (orientation == BodyOrientation.FRONT) {
            return AnalysisResult(currentProgressCount, "Hãy quay ngang người để tập Split Squat", Color.parseColor("#FFCA28"), false)
        }

        if (!hasStarted) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return AnalysisResult(currentProgressCount, "Hãy đứng thẳng để bắt đầu", Color.parseColor("#FFCA28"), false)
            }
        }

        // Points based on orientation
        val (hip, knee, ankle) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[23], landmarks[25], landmarks[27])
        } else {
            Triple(landmarks[24], landmarks[26], landmarks[28])
        }
        
        val kneeAngle = calculateAngle(hip, knee, ankle)
        
        if (kneeAngle < 100) {
            isDown = true
            feedback = "Tốt! Đẩy người lên."
        } else if (isDown && kneeAngle > 150) {
            currentProgressCount++
            isDown = false
            feedback = "Giữ thăng bằng tốt!"
        } else if (!isDown) {
            feedback = "Hạ gối chân sau sâu xuống."
        }

        feedbackColor = getFeedbackColor(isDown || kneeAngle > 150)

        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount)
    }
}
