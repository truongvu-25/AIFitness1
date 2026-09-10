package com.google.mediapipe.examples.poselandmarker.analysis

import android.graphics.Color
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

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

enum class BodyOrientation {
    FRONT,
    LEFT,
    RIGHT
}

enum class SupportingSide {
    LEFT,
    RIGHT,
    INVALID
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
) {
    var currentProgressCount: Int = 0
    var feedback: String = ""
    var feedbackColor: Int = Color.parseColor("#FFCA28")
    var lastTimeIncrementMs: Long = 0L
    var hasStarted: Boolean = false
    var hasTrueForm: Boolean = false
    private var stableBodyOrientation: BodyOrientation? = null

    open val requiredOrientation: BodyOrientation = BodyOrientation.LEFT // Default to side view

    /**
     * Primary entry point for UI. Handles common visibility and orientation logic.
     */
    fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        if (!isFullBodyVisible(landmarks)) {
            val msg = if (exerciseId.contains("situp", true) || exerciseId.contains("plank", true)) 
                "Hãy nằm lùi lại để camera quét được toàn thân" 
            else "Hãy đứng lùi lại để camera quét được toàn thân"
            return createResult(msg, Color.parseColor("#FFCA28"))
        }

        val orientation = detectBodyOrientation(landmarks)
        if (!isCorrectOrientation(orientation)) {
            val msg = if (requiredOrientation == BodyOrientation.FRONT) 
                "Hãy đứng hướng về phía camera để tập $exerciseName"
            else "Hãy quay ngang người để đếm $exerciseName chính xác hơn"
            return createResult(msg, Color.parseColor("#FFCA28"))
        }

        if (!hasStarted && !isTimedAndStarted()) {
            if (isReadyState(landmarks)) {
                hasStarted = true
            } else {
                return createResult(getReadyFeedback(), Color.parseColor("#FFCA28"), getInitialCustomLines(landmarks))
            }
        }

        return doAnalyze(landmarks, orientation)
    }

    private fun isTimedAndStarted() = isTimed && currentProgressCount > 0

    /**
     * Specialized analysis for each exercise.
     */
    protected abstract fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult

    /**
     * Initial feedback when the user is not in ready state.
     */
    protected abstract fun getReadyFeedback(): String

    /**
     * Optional custom lines to show during ready state (e.g., guide lines).
     */
    protected open fun getInitialCustomLines(landmarks: List<NormalizedLandmark>): List<CustomLine> = emptyList()

    /**
     * Check if current orientation matches the required one.
     */
    protected open fun isCorrectOrientation(orientation: BodyOrientation): Boolean {
        return if (requiredOrientation == BodyOrientation.FRONT) {
            orientation == BodyOrientation.FRONT
        } else {
            orientation != BodyOrientation.FRONT
        }
    }

    abstract fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean

    // --- Utility Methods ---

    protected fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val radians = atan2(c.y() - b.y(), c.x() - b.x()) - atan2(a.y() - b.y(), a.x() - b.x())
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    protected fun calculateDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt(dx * dx + dy * dy)
    }

    protected fun getFeedbackColor(isValid: Boolean): Int {
        return if (isValid) Color.parseColor("#4CAF50") else Color.parseColor("#FFCA28")
    }

    protected fun createResult(msg: String, color: Int, lines: List<CustomLine> = emptyList()): AnalysisResult {
        feedback = msg
        feedbackColor = color
        return AnalysisResult(currentProgressCount, feedback, feedbackColor, currentProgressCount >= targetCount, lines)
    }

    protected fun detectBodyOrientation(landmarks: List<NormalizedLandmark>): BodyOrientation {
        val leftShoulder = landmarks[L_SHOULDER]
        val rightShoulder = landmarks[R_SHOULDER]
        val shoulderWidth = abs(leftShoulder.x() - rightShoulder.x())
        
        // Use hysteresis around the front/side boundary. Without it, small landmark
        // noise can flip orientation every frame and make an otherwise valid rep vanish.
        val isFront = when (stableBodyOrientation) {
            BodyOrientation.FRONT -> shoulderWidth >= 0.085f
            BodyOrientation.LEFT, BodyOrientation.RIGHT -> shoulderWidth >= 0.115f
            null -> shoulderWidth >= 0.10f
        }

        stableBodyOrientation = if (isFront) {
            BodyOrientation.FRONT
        } else {
            // Keep the selected side while landmark visibility is nearly tied.
            val leftVisibility = leftShoulder.visibility().orElse(0f)
            val rightVisibility = rightShoulder.visibility().orElse(0f)
            when {
                leftVisibility > rightVisibility + 0.08f -> BodyOrientation.LEFT
                rightVisibility > leftVisibility + 0.08f -> BodyOrientation.RIGHT
                stableBodyOrientation == BodyOrientation.LEFT -> BodyOrientation.LEFT
                else -> BodyOrientation.RIGHT
            }
        }
        return stableBodyOrientation!!
    }

    fun isFullBodyVisible(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size != 33) return false
        val leftVisible = landmarks[L_SHOULDER].visibility().orElse(0f) > 0.6f && landmarks[L_ELBOW].visibility().orElse(0f) > 0.6f && landmarks[L_WRIST].visibility().orElse(0f) > 0.6f
        val rightVisible = landmarks[R_SHOULDER].visibility().orElse(0f) > 0.6f && landmarks[R_ELBOW].visibility().orElse(0f) > 0.6f && landmarks[R_WRIST].visibility().orElse(0f) > 0.6f
        if (!leftVisible && !rightVisible) return false
        if (landmarks[NOSE].visibility().orElse(0f) < 0.5f) return false
        val pairs = listOf(L_HIP to R_HIP, L_KNEE to R_KNEE, L_ANKLE to R_ANKLE)
        for ((left, right) in pairs) {
            if (landmarks[left].visibility().orElse(0f) < 0.5f && landmarks[right].visibility().orElse(0f) < 0.5f) return false
        }
        return true
    }

    protected fun updateTimedProgress() {
        val now = System.currentTimeMillis()
        if (lastTimeIncrementMs == 0L) {
            lastTimeIncrementMs = now
        } else {
            val elapsedSeconds = ((now - lastTimeIncrementMs) / 1000L).toInt()
            if (elapsedSeconds > 0) {
                currentProgressCount += elapsedSeconds
                if (targetCount > 0) {
                    currentProgressCount = currentProgressCount.coerceAtMost(targetCount)
                }
                lastTimeIncrementMs += elapsedSeconds * 1000L
            }
        }
    }

    companion object {
        // Landmarks Constants
        const val NOSE = 0
        const val L_SHOULDER = 11
        const val R_SHOULDER = 12
        const val L_ELBOW = 13
        const val R_ELBOW = 14
        const val L_WRIST = 15
        const val R_WRIST = 16
        const val L_HIP = 23
        const val R_HIP = 24
        const val L_KNEE = 25
        const val R_KNEE = 26
        const val L_ANKLE = 27
        const val R_ANKLE = 28
        const val L_HEEL = 29
        const val R_HEEL = 30

        fun create(exerciseId: String, exerciseName: String, targetCount: Int, isTimed: Boolean, unit: String): BaseExerciseAnalyzer {
            val id = exerciseId.lowercase().trim()
            return when (id) {
                "pushup", "push_up" -> PushupAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "squat" -> SquatAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "jumpingjack", "jumping_jack", "jumping_jacks" -> JumpingJackAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "situp", "sit_up" -> SitupAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "plank" -> PlankAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "sideplank", "side_plank" -> SidePlankAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                "splitsquat", "split_squat", "lunges" -> SplitSquatAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit)
                else -> object : BaseExerciseAnalyzer(exerciseId, exerciseName, targetCount, isTimed, unit) {
                    override fun isReadyState(landmarks: List<NormalizedLandmark>) = true
                    override fun getReadyFeedback() = ""
                    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation) = 
                        AnalysisResult(currentProgressCount, "Bài tập chưa hỗ trợ đếm", Color.GRAY, false)
                }
            }
        }
    }
}

/** 1. Pushup Analyzer */
class PushupAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    private var isDown = false

    override fun getReadyFeedback() = "Hãy bắt đầu ở tư thế chống tay thẳng"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        val (shoulder, elbow, wrist, hip, ankle) = if (orientation == BodyOrientation.LEFT) {
            listOf(landmarks[L_SHOULDER], landmarks[L_ELBOW], landmarks[L_WRIST], landmarks[L_HIP], landmarks[L_ANKLE])
        } else {
            listOf(landmarks[R_SHOULDER], landmarks[R_ELBOW], landmarks[R_WRIST], landmarks[R_HIP], landmarks[R_ANKLE])
        }
        return calculateAngle(shoulder, elbow, wrist) > 160 && calculateAngle(shoulder, hip, ankle) > 160 && 
               calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE]) > 160 && 
               calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE]) > 160
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val (shoulder, elbow, wrist) = if (orientation == BodyOrientation.LEFT) {
            Triple(landmarks[L_SHOULDER], landmarks[L_ELBOW], landmarks[L_WRIST])
        } else {
            Triple(landmarks[R_SHOULDER], landmarks[R_ELBOW], landmarks[R_WRIST])
        }
        
        val angle = calculateAngle(shoulder, elbow, wrist)
        val leftKnee = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightKnee = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])
        hasTrueForm = leftKnee > 160 && rightKnee > 160

        if (angle < 100 && hasTrueForm) {
            isDown = true
            feedback = "Tốt! Bây giờ hãy đẩy lên."
        } else if (isDown && angle > 150) {
            currentProgressCount++
            isDown = false
            feedback = "Đã xong 1 lần! Tiếp tục nào."
        } else if (!isDown && hasTrueForm) {
            feedback = "Hạ thấp người xuống nữa."
        } else {
            feedback = "Thẳng cái chân đi"
        }
        return createResult(feedback, getFeedbackColor(isDown || angle > 150))
    }
}

/** 2. Squat Analyzer */
class SquatAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    private var isDown = false

    override fun getReadyFeedback() = "Hãy đứng thẳng để bắt đầu"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        val (h, k, a) = if (orientation == BodyOrientation.LEFT) 
            Triple(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        else Triple(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])
        return calculateAngle(h, k, a) > 160
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val leftAngle = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightAngle = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])

        if (leftAngle < 110 && rightAngle < 110) {
            isDown = true
            feedback = "Đã xuống đủ sâu! Đứng dậy nào."
        } else if (isDown && leftAngle > 150 && rightAngle > 150) {
            currentProgressCount++
            isDown = false
            feedback = "Tuyệt vời! Tiếp tục squat."
        } else if (!isDown) {
            feedback = "Hạ thấp mông xuống chút nữa."
        }
        return createResult(feedback, getFeedbackColor(isDown || (leftAngle > 150 && rightAngle > 150)))
    }
}

/** 3. Jumping Jack Analyzer */
class JumpingJackAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    private var isUp = false
    override val requiredOrientation = BodyOrientation.FRONT

    override fun getReadyFeedback() = "Hãy đứng thẳng, khép tay và khép chân để bắt đầu"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        return landmarks[L_WRIST].y() > landmarks[L_SHOULDER].y() && landmarks[R_WRIST].y() > landmarks[R_SHOULDER].y()
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val handsHigh = landmarks[L_WRIST].y() < landmarks[L_SHOULDER].y() && landmarks[R_WRIST].y() < landmarks[R_SHOULDER].y()
        val handsDown = landmarks[L_WRIST].y() > landmarks[L_SHOULDER].y() && landmarks[R_WRIST].y() > landmarks[R_SHOULDER].y()
        val feetDistance = abs(landmarks[L_ANKLE].x() - landmarks[R_ANKLE].x())
        val bodyScale = abs(landmarks[L_SHOULDER].x() - landmarks[R_SHOULDER].x()).coerceAtLeast(0.08f)
        val feetOpen = feetDistance > bodyScale * 1.35f
        val feetClose = feetDistance < bodyScale * 0.95f

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
        return createResult(feedback, getFeedbackColor((handsHigh && feetOpen) || (handsDown && feetClose)))
    }
}

/** 4. Sit-up Analyzer */
class SitupAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    private var isUp = false

    override fun getReadyFeedback() = "Hãy co gối và nằm thẳng để bắt đầu"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        val (shoulder, hip, knee, heel) = if (orientation == BodyOrientation.LEFT) 
            listOf(landmarks[L_SHOULDER], landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_HEEL])
        else listOf(landmarks[R_SHOULDER], landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_HEEL])

        return calculateAngle(hip, knee, heel) <= 120 && calculateAngle(shoulder, hip, heel) >= 165
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val (shoulder, hip, knee, heel) = if (orientation == BodyOrientation.LEFT) 
            listOf(landmarks[L_SHOULDER], landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_HEEL])
        else listOf(landmarks[R_SHOULDER], landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_HEEL])

        val kneeAngle = calculateAngle(hip, knee, heel)
        val bodyAngle = calculateAngle(shoulder, hip, heel)
        val kneeBent = kneeAngle <= 120

        if (!isUp && bodyAngle < 130 && kneeBent) {
            isUp = true
            feedback = "Tốt! Nằm xuống từ từ."
        } else if (isUp && bodyAngle >= 165 && kneeBent) {
            currentProgressCount++
            isUp = false
            feedback = "Tốt! Tiếp tục."
        } else if (!kneeBent) {
            feedback = "Hãy giữ đầu gối co lại."
        } else if (!isUp) {
            feedback = "Gập người lên cao hơn."
        }
        return createResult(feedback, getFeedbackColor(kneeBent))
    }
}

/** 5. Plank Analyzer (Timed) */
class PlankAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    
    override fun getReadyFeedback() = "Hãy giữ thẳng người để bắt đầu tính giờ"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val orientation = detectBodyOrientation(landmarks)
        val (shoulder, elbow, hip, ankle) = if (orientation == BodyOrientation.LEFT) 
            listOf(landmarks[L_SHOULDER], landmarks[L_ELBOW], landmarks[L_HIP], landmarks[L_ANKLE])
        else listOf(landmarks[R_SHOULDER], landmarks[R_ELBOW], landmarks[R_HIP], landmarks[R_ANKLE])

        return calculateAngle(shoulder, hip, ankle) > 165 && calculateAngle(shoulder, elbow, hip) > 55
    }

    override fun getInitialCustomLines(landmarks: List<NormalizedLandmark>): List<CustomLine> {
        val orientation = detectBodyOrientation(landmarks)
        val (s, a) = if (orientation == BodyOrientation.LEFT) L_SHOULDER to L_ANKLE else R_SHOULDER to R_ANKLE
        return listOf(CustomLine(s, a, Color.GREEN))
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val (shoulderIdx, hipIdx, ankleIdx) = if (orientation == BodyOrientation.LEFT) Triple(L_SHOULDER, L_HIP, L_ANKLE) else Triple(R_SHOULDER, R_HIP, R_ANKLE)
        val bodyAngle = calculateAngle(landmarks[shoulderIdx], landmarks[hipIdx], landmarks[ankleIdx])
        val leftKnee = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightKnee = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])
        val isValid = bodyAngle > 165 && leftKnee >= 160 && rightKnee >= 160

        val lines = mutableListOf<CustomLine>()
        if (isValid) {
            updateTimedProgress()
            feedback = "Đang giữ chuẩn tư thế!"
        } else {
            lastTimeIncrementMs = 0L
            feedback = "Hãy giữ thẳng thân và hai chân!"
            lines.add(CustomLine(shoulderIdx, ankleIdx, Color.GREEN))
        }
        return createResult(feedback, getFeedbackColor(isValid), lines)
    }
}

/** 6. Side Plank Analyzer (Timed) */
class SidePlankAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    override val requiredOrientation = BodyOrientation.FRONT

    override fun getReadyFeedback() = "Hãy chống một khuỷu tay xuống đất"

    private fun detectSupportingSide(landmarks: List<NormalizedLandmark>): SupportingSide {
        val leftAngle = calculateAngle(landmarks[L_SHOULDER], landmarks[L_ELBOW], landmarks[L_HIP])
        val rightAngle = calculateAngle(landmarks[R_SHOULDER], landmarks[R_ELBOW], landmarks[R_HIP])
        val leftDiff = abs(leftAngle - 90)
        val rightDiff = abs(rightAngle - 90)
        return when {
            leftDiff <= 15 && leftDiff < rightDiff -> SupportingSide.LEFT
            rightDiff <= 15 && rightDiff < leftDiff -> SupportingSide.RIGHT
            else -> SupportingSide.INVALID
        }
    }

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val side = detectSupportingSide(landmarks)
        if (side == SupportingSide.INVALID) return false
        val (s, h, a) = if (side == SupportingSide.LEFT) Triple(landmarks[L_SHOULDER], landmarks[L_HIP], landmarks[L_ANKLE])
        else Triple(landmarks[R_SHOULDER], landmarks[R_HIP], landmarks[R_ANKLE])
        return calculateAngle(s, h, a) >= 160
    }

    override fun getInitialCustomLines(landmarks: List<NormalizedLandmark>): List<CustomLine> {
        val side = detectSupportingSide(landmarks)
        if (side == SupportingSide.INVALID) return emptyList()
        val (s, a) = if (side == SupportingSide.LEFT) L_SHOULDER to L_ANKLE else R_SHOULDER to R_ANKLE
        return listOf(CustomLine(s, a, Color.GREEN))
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val side = detectSupportingSide(landmarks)
        if (side == SupportingSide.INVALID) return createResult("Hãy chống một khuỷu tay xuống đất", Color.parseColor("#FFCA28"))

        val (sIdx, hIdx, aIdx) = if (side == SupportingSide.LEFT) Triple(L_SHOULDER, L_HIP, L_ANKLE) else Triple(R_SHOULDER, R_HIP, R_ANKLE)
        val bodyAngle = calculateAngle(landmarks[sIdx], landmarks[hIdx], landmarks[aIdx])
        val leftKnee = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightKnee = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])
        val isValid = bodyAngle >= 170 && leftKnee >= 160 && rightKnee >= 160

        val lines = mutableListOf<CustomLine>()
        if (isValid) {
            updateTimedProgress()
            feedback = "Tuyệt vời, giữ vững nhé!"
        } else {
            lastTimeIncrementMs = 0L
            feedback = "Đẩy hông cao lên một chút!"
            lines.add(CustomLine(sIdx, aIdx, Color.GREEN))
        }
        return createResult(feedback, getFeedbackColor(isValid), lines)
    }
}

/** 7. Split Squat Analyzer */
class SplitSquatAnalyzer(id: String, name: String, target: Int, timed: Boolean, u: String) : BaseExerciseAnalyzer(id, name, target, timed, u) {
    private var isDown = false

    override fun getReadyFeedback() = "Hãy đứng vào tư thế Split Squat để bắt đầu"

    override fun isReadyState(landmarks: List<NormalizedLandmark>): Boolean {
        val leftAngle = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightAngle = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])
        val oneLegMoreBent = abs(leftAngle - rightAngle) >= 20
        val bothLegsBent = leftAngle < 175 && rightAngle < 175
        val legsSeparated = calculateDistance(landmarks[L_HEEL], landmarks[R_HEEL]) > 0.15f
        return bothLegsBent && oneLegMoreBent && legsSeparated
    }

    override fun doAnalyze(landmarks: List<NormalizedLandmark>, orientation: BodyOrientation): AnalysisResult {
        val leftKnee = calculateAngle(landmarks[L_HIP], landmarks[L_KNEE], landmarks[L_ANKLE])
        val rightKnee = calculateAngle(landmarks[R_HIP], landmarks[R_KNEE], landmarks[R_ANKLE])

        if (leftKnee < 110 && rightKnee < 110) {
            isDown = true
            feedback = "Tốt! Đẩy người lên."
        } else if (isDown && leftKnee > 145 && rightKnee > 145) {
            currentProgressCount++
            isDown = false
            feedback = "Giữ thăng bằng tốt!"
        } else if (!isDown) {
            feedback = "Hạ gối chân sau sâu xuống."
        }
        return createResult(feedback, getFeedbackColor(isDown || (leftKnee > 145 && rightKnee > 145)))
    }
}
