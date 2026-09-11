package com.google.mediapipe.examples.poselandmarker.ui.fragment.camera

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.mediapipe.examples.poselandmarker.analysis.BaseExerciseAnalyzer
import com.google.mediapipe.examples.poselandmarker.analysis.CustomLine
import com.google.mediapipe.examples.poselandmarker.data.WorkoutSyncScheduler
import com.google.mediapipe.examples.poselandmarker.data.local.TriForceDatabase
import com.google.mediapipe.examples.poselandmarker.data.local.toLocalEntity
import com.google.mediapipe.examples.poselandmarker.viewmodel.MainViewModel
import com.google.mediapipe.examples.poselandmarker.analysis.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.service.RestTimerService
import com.google.mediapipe.examples.poselandmarker.model.WorkoutSession
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.ui.fragment.onboarding.PermissionsFragment
import com.google.mediapipe.examples.poselandmarker.voice.VoiceCoachManager
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "PoseLandmarker"
        private const val VOICE_MIN_INTERVAL_MS = 6_000L
        private const val VOICE_REPEAT_INTERVAL_MS = 15_000L
        private const val FORM_SAMPLE_INTERVAL_MS = 500L
        private const val CALIBRATION_STABLE_FRAMES = 16
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    private lateinit var auth: FirebaseAuth

    private var exerciseId: String = ""
    private var exerciseName: String = ""
    private var targetCount: Int = 0
    private var dayIndex: Int = 1
    private var hasRemainingPending: Boolean = true

    private var isTimed: Boolean = false
    private var unitStr: String = "lần"
    private var currentProgressCount: Int = 0

    private var exerciseAnalyzer: BaseExerciseAnalyzer? = null

    private var lastSpokenFeedback = ""
    private var lastVoiceTimeMs = 0L
    private var lastSpokenProgress = 0
    private var hasAnnouncedCompletion = false
    private var voiceMode = VoiceCoachManager.VOICE_FEMALE
    private var lastInferenceUiUpdateMs = 0L

    private var isCalibrated = false
    private var calibrationStableFrames = 0
    private var calibrationStage = CalibrationStage.FIND_BODY

    private var workoutStartedAtMs = 0L
    private var lastFormSampleAtMs = 0L
    private var scoredFormSamples = 0
    private var correctFormSamples = 0
    private val formIssueCounts = linkedMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        exerciseId = arguments?.getString("exerciseId") ?: ""
        exerciseName = arguments?.getString("exerciseName") ?: ""
        targetCount = arguments?.getInt("targetCount") ?: 0
        dayIndex = arguments?.getInt("dayIndex") ?: 1
        hasRemainingPending = arguments?.getBoolean("hasRemainingPending", true) ?: true

        voiceMode = VoiceCoachManager.currentMode(requireContext())

        isTimed = (exerciseId == "plank" || exerciseId == "sideplank")
        unitStr = if (isTimed) "giây" else "lần"
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions, arguments)
            return
        }
        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        VoiceCoachManager.stop()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        imageAnalyzer?.clearAnalyzer()
        cameraProvider?.unbindAll()
        imageAnalyzer = null
        preview = null
        camera = null
        if (this::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
            backgroundExecutor.execute {
                if (this::poseLandmarkerHelper.isInitialized) poseLandmarkerHelper.clearPoseLandmarker()
            }
            backgroundExecutor.shutdown()
        }
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cancel any active rest timer when starting an exercise
        RestTimerService.stopService(requireContext())

        backgroundExecutor = Executors.newSingleThreadExecutor()
        currentProgressCount = 0
        isCompletingWorkout = false
        isCalibrated = false
        calibrationStableFrames = 0
        scoredFormSamples = 0
        correctFormSamples = 0
        formIssueCounts.clear()
        hasAnnouncedCompletion = false
        lastSpokenProgress = 0
        workoutStartedAtMs = SystemClock.elapsedRealtime()

        cameraFacing = CameraSelector.LENS_FACING_FRONT
        fragmentCameraBinding.tvWorkoutTitle.text = exerciseName
        fragmentCameraBinding.tvWorkoutTarget.text = "Mục tiêu: $targetCount $unitStr"

        fragmentCameraBinding.tvCounterValue.text = "0/$targetCount"
        updateVoiceButtonState()

        initializeVoiceGuidance()

        exerciseAnalyzer = BaseExerciseAnalyzer.create(
            exerciseId, exerciseName, targetCount, isTimed, unitStr
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { fragmentCameraBinding.btnBack.performClick() }
        })
        fragmentCameraBinding.btnBack.setOnClickListener {
            if (isCompletingWorkout) return@setOnClickListener
            if (currentProgressCount > 0 && !isCompletingWorkout) {
                confirmManualCompletion()
            } else {
                findNavController().popBackStack()
            }
        }

        fragmentCameraBinding.btnSwitchCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            resetCalibration()
            setUpCamera()
        }

        fragmentCameraBinding.btnVoiceSettings.setOnClickListener {
            showVoiceSettingsDialog()
        }

        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        val appContext = requireContext().applicationContext
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = appContext,
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                currentModel = viewModel.currentModel,
                poseLandmarkerHelperListener = this
            )
        }

        initBottomSheetControls()
    }

    private fun initializeVoiceGuidance() {
        VoiceCoachManager.initialize(requireContext()) { ready ->
            if (!ready || _fragmentCameraBinding == null) return@initialize
            speakGuidance(
                "Bắt đầu hiệu chỉnh camera. Đứng vào khung hình để nhìn thấy toàn thân.",
                force = true,
                speechRate = VoiceCoachManager.CALIBRATION_SPEECH_RATE
            )
        }
    }

    private fun showVoiceSettingsDialog() {
        val labels = arrayOf(
            "Tắt hướng dẫn giọng nói",
            "Ưu tiên giọng nữ",
            "Ưu tiên giọng nam",
            "Giọng mặc định của thiết bị"
        )
        val modes = arrayOf(
            VoiceCoachManager.VOICE_OFF,
            VoiceCoachManager.VOICE_FEMALE,
            VoiceCoachManager.VOICE_MALE,
            VoiceCoachManager.VOICE_DEFAULT
        )
        val selected = modes.indexOf(voiceMode).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Voice coach")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                voiceMode = modes[which]
                VoiceCoachManager.setMode(requireContext(), voiceMode)
                updateVoiceButtonState()
                if (voiceMode != VoiceCoachManager.VOICE_OFF) {
                    speakGuidance("Đã cập nhật giọng hướng dẫn.", force = true)
                } else {
                    VoiceCoachManager.stop()
                }
                dialog.dismiss()
            }
            .setNeutralButton("HIỆU CHỈNH LẠI") { _, _ -> resetCalibration() }
            .setNegativeButton("ĐÓNG", null)
            .show()
    }

    private fun updateVoiceButtonState() {
        _fragmentCameraBinding?.btnVoiceSettings?.alpha =
            if (voiceMode == VoiceCoachManager.VOICE_OFF) 0.45f else 1f
    }

    private fun handleVoiceGuidance(feedback: String, progress: Int, isComplete: Boolean) {
        if (isComplete) {
            if (!hasAnnouncedCompletion) {
                hasAnnouncedCompletion = true
                speakGuidance("Hoàn thành.", force = true)
            }
            return
        }

        if (progress > lastSpokenProgress) {
            lastSpokenProgress = progress
            val shouldAnnounceProgress = if (isTimed) {
                progress > 0 && progress % 15 == 0
            } else {
                progress == 1 || progress % 5 == 0
            }
            if (shouldAnnounceProgress) {
                val progressText = if (isTimed) "$progress giây" else progress.toString()
                speakGuidance(progressText, force = true)
            }
            // Do not append form feedback in the same frame as a new count.
            return
        }

        speakGuidance(conciseVoiceCue(feedback))
    }

    private fun conciseVoiceCue(feedback: String): String {
        val normalized = feedback.trim().lowercase(Locale.forLanguageTag("vi-VN"))
        return when {
            normalized.contains("toàn thân") -> "Lùi lại."
            normalized.contains("quay ngang") -> "Quay ngang."
            normalized.contains("hướng về phía camera") -> "Nhìn camera."
            normalized.contains("đẩy hông") -> "Nâng hông."
            normalized.contains("hạ gối") -> "Hạ gối."
            normalized.contains("hạ thấp mông") -> "Hạ mông."
            normalized.contains("hạ thấp người") -> "Hạ thấp hơn."
            normalized.contains("nằm xuống") -> "Hạ người."
            normalized.contains("gập người") -> "Gập cao hơn."
            normalized.contains("đầu gối co") -> "Co gối."
            normalized.contains("thẳng cái chân") -> "Duỗi chân."
            normalized.contains("đẩy lên") || normalized.contains("đẩy người lên") -> "Đẩy lên."
            normalized.contains("đứng dậy") -> "Đứng lên."
            normalized.contains("khép tay và chân") -> "Khép lại."
            normalized.contains("giơ tay") -> "Mở tay và chân."
            normalized.contains("giữ thẳng thân") -> "Giữ thẳng người."
            normalized.contains("đang giữ chuẩn") ||
                normalized.contains("đã xong") ||
                normalized.contains("tiếp tục") ||
                normalized.contains("tốt") ||
                normalized.contains("tuyệt vời") -> ""
            else -> feedback
        }
    }

    private fun speakGuidance(
        text: String,
        force: Boolean = false,
        speechRate: Float = VoiceCoachManager.NORMAL_SPEECH_RATE
    ) {
        if (voiceMode == VoiceCoachManager.VOICE_OFF || text.isBlank()) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastVoiceTimeMs
        val isRepeatedFeedback = text == lastSpokenFeedback
        if (!force && elapsed < VOICE_MIN_INTERVAL_MS) return
        if (!force && isRepeatedFeedback && elapsed < VOICE_REPEAT_INTERVAL_MS) return

        VoiceCoachManager.speak(text, "tri_force_guidance_${now}", speechRate)
        lastSpokenFeedback = text
        lastVoiceTimeMs = now
    }

    private fun trackFormSample(feedback: String, feedbackColor: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFormSampleAtMs < FORM_SAMPLE_INTERVAL_MS) return

        val normalized = feedback.trim()
        val setupMessage = listOf(
            "lùi lại",
            "quay ngang",
            "để bắt đầu",
            "chưa hỗ trợ",
            "đang nhận diện"
        ).any { normalized.contains(it, ignoreCase = true) }
        if (setupMessage) return

        lastFormSampleAtMs = now
        scoredFormSamples++

        val isCorrect = feedbackColor == Color.parseColor("#4CAF50")
        if (isCorrect) {
            correctFormSamples++
        } else if (normalized.isNotBlank()) {
            formIssueCounts[normalized] = (formIssueCounts[normalized] ?: 0) + 1
        }
    }

    private fun isFullBodyVisible(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size != 33) return false
        val leftVisible = landmarks[11].visibility().orElse(0f) > 0.6f && landmarks[13].visibility().orElse(0f) > 0.6f && landmarks[15].visibility().orElse(0f) > 0.6f
        val rightVisible = landmarks[12].visibility().orElse(0f) > 0.6f && landmarks[14].visibility().orElse(0f) > 0.6f && landmarks[16].visibility().orElse(0f) > 0.6f
        if (!leftVisible && !rightVisible) return false
        if (landmarks[0].visibility().orElse(0f) < 0.5f) return false
        val pairs = listOf(23 to 24, 25 to 26, 27 to 28)
        for ((left, right) in pairs) {
            if (landmarks[left].visibility().orElse(0f) < 0.5f && landmarks[right].visibility().orElse(0f) < 0.5f) return false
        }
        return true
    }

    private fun handleCalibration(landmarks: List<NormalizedLandmark>): Boolean {
        if (isCalibrated) return true

        val analyzer = exerciseAnalyzer ?: return false
        if (!isFullBodyVisible(landmarks)) {
            calibrationStableFrames = 0
            updateCalibration(
                CalibrationStage.FIND_BODY,
                "Đứng lùi lại và giữ toàn bộ đầu, tay, hông, gối, cổ chân trong khung hình.",
                12
            )
            return false
        }

        if (!analyzer.isReadyState(landmarks)) {
            calibrationStableFrames = 0
            updateCalibration(
                CalibrationStage.READY_POSE,
                calibrationPoseInstruction(),
                45
            )
            return false
        }

        calibrationStableFrames++
        // Timed holds already validate the pose continuously inside their analyzer.
        // Start them as soon as one ready frame is detected instead of making the
        // user hold the same pose through the full repetition calibration window.
        val requiredStableFrames = if (isTimed) 1 else CALIBRATION_STABLE_FRAMES
        val stableProgress = 55 +
            (calibrationStableFrames * 45 / requiredStableFrames).coerceAtMost(45)
        updateCalibration(
            CalibrationStage.HOLD_STILL,
            "Giữ nguyên tư thế chuẩn bị trong giây lát...",
            stableProgress
        )

        if (calibrationStableFrames >= requiredStableFrames) {
            isCalibrated = true
            workoutStartedAtMs = SystemClock.elapsedRealtime()
            speakGuidance(
                "Hiệu chỉnh hoàn tất. Bắt đầu $exerciseName.",
                force = true,
                speechRate = VoiceCoachManager.CALIBRATION_SPEECH_RATE
            )
        }
        return false
    }

    private fun updateCalibration(
        stage: CalibrationStage,
        message: String,
        progress: Int
    ) {
        val stageChanged = calibrationStage != stage
        calibrationStage = stage
        // Keep coaching while the user remains stuck on a calibration step.
        // speakGuidance() throttles repeated text, so this is audible roughly
        // every VOICE_REPEAT_INTERVAL_MS instead of once per camera frame.
        speakGuidance(
            message,
            force = stageChanged,
            speechRate = VoiceCoachManager.CALIBRATION_SPEECH_RATE
        )
    }

    private fun calibrationPoseInstruction(): String {
        return when (exerciseId.lowercase().trim()) {
            "jumpingjack", "jumping_jack", "jumping_jacks" ->
                "Đứng hướng về camera, khép tay và khép chân ở tư thế chuẩn bị."
            "pushup", "push_up" ->
                "Quay ngang, vào tư thế chống tay thẳng và giữ thân người thẳng."
            "squat" ->
                "Quay ngang camera, đứng thẳng và duỗi gối ở tư thế bắt đầu."
            "situp", "sit_up" ->
                "Nằm ngang camera, co gối và giữ thân ở tư thế bắt đầu."
            "plank", "sideplank", "side_plank" ->
                "Quay ngang camera và giữ vai, hông, cổ chân thành một đường thẳng."
            "splitsquat", "split_squat", "lunges" ->
                "Quay ngang camera, bước một chân về trước và đứng thẳng."
            else -> "Giữ tư thế chuẩn bị của bài tập trong khung hình."
        }
    }

    private fun resetCalibration() {
        isCalibrated = false
        calibrationStableFrames = 0
        calibrationStage = CalibrationStage.FIND_BODY
        speakGuidance(
            "Bắt đầu hiệu chỉnh lại camera.",
            force = true,
            speechRate = VoiceCoachManager.CALIBRATION_SPEECH_RATE
        )
    }

    private fun calculateFormScore(): Int {
        if (scoredFormSamples == 0) {
            return if (targetCount > 0 && currentProgressCount >= targetCount) 100 else 0
        }
        return ((correctFormSamples * 100f) / scoredFormSamples)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun topFormIssues(): List<String> =
        formIssueCounts.entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }

    private fun confirmManualCompletion() {
        if (targetCount <= 0 || currentProgressCount >= targetCount) {
            completeWorkout(completedAutomatically = false)
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Kết thúc sớm?")
            .setMessage(
                "Bạn mới hoàn thành $currentProgressCount/$targetCount $unitStr. " +
                    "Kết quả hiện tại vẫn sẽ được lưu để theo dõi tiến độ."
            )
            .setNegativeButton("TIẾP TỤC TẬP", null)
            .setPositiveButton("KẾT THÚC") { _, _ ->
                completeWorkout(completedAutomatically = false)
            }
            .show()
    }

    private var isCompletingWorkout = false

    private fun completeWorkout(completedAutomatically: Boolean) {
        if (isCompletingWorkout) return
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(context, "Bạn cần đăng nhập để lưu kết quả.", Toast.LENGTH_SHORT).show()
            return
        }
        isCompletingWorkout = true

        val session = WorkoutSession(
            id = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            dayIndex = dayIndex,
            targetCount = targetCount,
            actualCount = currentProgressCount,
            unit = unitStr,
            durationSeconds = ((SystemClock.elapsedRealtime() - workoutStartedAtMs) / 1000L)
                .toInt().coerceAtLeast(1),
            formScore = calculateFormScore(),
            formIssues = topFormIssues(),
            completedAutomatically = completedAutomatically,
            completedAt = System.currentTimeMillis()
        )

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = TriForceDatabase.getInstance(appContext).workoutSessionDao()
                val previous = withContext(Dispatchers.IO + NonCancellable) {
                    val old = dao.getRecent(uid, 100)
                        .firstOrNull { it.exerciseId == exerciseId }
                    dao.upsert(session.toLocalEntity(uid))
                    WorkoutSyncScheduler.enqueue(appContext)
                    old
                }
                if (!isAdded || isStateSaved || _fragmentCameraBinding == null) return@launch

                if (hasRemainingPending) {
                    RestTimerService.startRestTimer(requireContext(), dayIndex)
                } else {
                    RestTimerService.stopService(requireContext())
                }
                findNavController().navigate(
                    R.id.action_camera_to_workout_summary,
                    Bundle().apply {
                        putString("sessionId", session.id)
                        putString("exerciseId", session.exerciseId)
                        putString("exerciseName", session.exerciseName)
                        putInt("dayIndex", session.dayIndex)
                        putInt("targetCount", session.targetCount)
                        putInt("actualCount", session.actualCount)
                        putString("unit", session.unit)
                        putInt("durationSeconds", session.durationSeconds)
                        putInt("formScore", session.formScore)
                        putStringArrayList("formIssues", ArrayList(session.formIssues))
                        putBoolean("hasPreviousSession", previous != null)
                        putInt("previousActualCount", previous?.actualCount ?: 0)
                        putInt("previousFormScore", previous?.formScore ?: 0)
                        putBoolean("hasRemainingPending", hasRemainingPending)
                        putBoolean("syncPending", true)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showWorkoutSaveError("Không thể lưu kết quả trên thiết bị: ${error.localizedMessage}")
            }
        }
    }

    private fun showWorkoutSaveError(message: String) {
        isCompletingWorkout = false
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun initBottomSheetControls() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence)
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence)
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence)

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence >= 0.2) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence <= 0.8) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence >= 0.2) {
                viewModel.setMinPoseTrackingConfidence(viewModel.currentMinPoseTrackingConfidence - 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence <= 0.8) {
                viewModel.setMinPoseTrackingConfidence(viewModel.currentMinPoseTrackingConfidence + 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence >= 0.2) {
                viewModel.setMinPosePresenceConfidence(viewModel.currentMinPosePresenceConfidence - 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence <= 0.8) {
                viewModel.setMinPosePresenceConfidence(viewModel.currentMinPosePresenceConfidence + 0.1f)
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    viewModel.setDelegate(p2)
                    updateControlsUi()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    viewModel.setModel(p2)
                    updateControlsUi()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
    }

    private fun updateControlsUi() {
        if (this::poseLandmarkerHelper.isInitialized) {
            fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
                String.format(Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence)
            fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
                String.format(Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence)
            fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
                String.format(Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence)

            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence
                poseLandmarkerHelper.minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence
                poseLandmarkerHelper.minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence
                poseLandmarkerHelper.currentDelegate = viewModel.currentDelegate
                poseLandmarkerHelper.currentModel = viewModel.currentModel
                poseLandmarkerHelper.setupPoseLandmarker()
            }
            fragmentCameraBinding.overlay.clear()
        }
    }

    private fun setUpCamera() {
        if (_fragmentCameraBinding == null || !isAdded) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            if (_fragmentCameraBinding != null && isAdded) {
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases()
                } catch (e: Exception) {
                    Log.e(TAG, "Error obtaining camera provider", e)
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        if (_fragmentCameraBinding == null || !isAdded) return
        val cameraProvider = cameraProvider ?: return
        val display = fragmentCameraBinding.viewFinder.display ?: return

        val requestedSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()
        val oppositeFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val oppositeSelector = CameraSelector.Builder()
            .requireLensFacing(oppositeFacing)
            .build()
        val hasRequestedCamera = runCatching {
            cameraProvider.hasCamera(requestedSelector)
        }.getOrDefault(false)
        val hasOppositeCamera = runCatching {
            cameraProvider.hasCamera(oppositeSelector)
        }.getOrDefault(false)

        // Emulator webcams and USB cameras may expose LENS_FACING_UNKNOWN/null.
        // An empty selector intentionally accepts the first available camera.
        val cameraSelector = when {
            hasRequestedCamera -> requestedSelector
            hasOppositeCamera -> {
                cameraFacing = oppositeFacing
                oppositeSelector
            }
            cameraProvider.availableCameraInfos.isNotEmpty() -> CameraSelector.Builder().build()
            else -> {
                showCameraUnavailable("Không tìm thấy camera khả dụng trên thiết bị.")
                return
            }
        }
        val canSwitchLens = hasRequestedCamera && hasOppositeCamera
        fragmentCameraBinding.btnSwitchCamera.isEnabled = canSwitchLens
        fragmentCameraBinding.btnSwitchCamera.alpha = if (canSwitchLens) 1f else 0.45f

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(display.rotation)
            .build()

        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setTargetRotation(display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    detectPose(image)
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Camera use case binding failed", exc)
            showCameraUnavailable("Không thể mở camera. Hãy đóng ứng dụng khác đang dùng webcam rồi thử lại.")
        }
    }

    private fun showCameraUnavailable(message: String) {
        if (_fragmentCameraBinding == null || !isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (!this::poseLandmarkerHelper.isInitialized || poseLandmarkerHelper.isClose()) {
            imageProxy.close()
            return
        }
        try {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy, cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        } catch (error: RuntimeException) {
            // The helper always releases the frame, including conversion failures.
            Log.e(TAG, "Unable to analyze camera frame", error)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null && isResumed && resultBundle.results.isNotEmpty()) {
                val now = SystemClock.uptimeMillis()
                if (now - lastInferenceUiUpdateMs >= 500L) {
                    fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                        String.format(Locale.US, "%d ms", resultBundle.inferenceTime)
                    lastInferenceUiUpdateMs = now
                }

                // Pose Analysis and Exercise Specific Logic
                val hasLandmarks = resultBundle.results.first().landmarks().isNotEmpty()
                if (hasLandmarks) {
                    val landmarks = resultBundle.results.first().landmarks().first()
                    var overlayLines: List<CustomLine> = emptyList()
                    exerciseAnalyzer?.let { analyzer ->
                        if (handleCalibration(landmarks)) {
                            val result = analyzer.analyze(landmarks)

                            currentProgressCount = result.currentProgress
                            val counterText = "$currentProgressCount/$targetCount"
                            if (fragmentCameraBinding.tvCounterValue.text.toString() != counterText) {
                                fragmentCameraBinding.tvCounterValue.text = counterText
                            }
                            trackFormSample(result.feedback, result.feedbackColor)
                            handleVoiceGuidance(
                                feedback = result.feedback,
                                progress = currentProgressCount,
                                isComplete = result.isComplete
                            )

                            overlayLines = result.customLines

                            if (result.isComplete) {
                                completeWorkout(completedAutomatically = true)
                            }
                        }
                    }
                    // Draw the skeleton once per result instead of invalidating it twice.
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM,
                        overlayLines
                    )
                } else {
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM,
                        emptyList()
                    )
                    val noBodyFeedback = "Hãy đứng lùi lại để camera quét được toàn thân"
                    if (!isCalibrated) {
                        calibrationStableFrames = 0
                        updateCalibration(CalibrationStage.FIND_BODY, noBodyFeedback, 8)
                    } else {
                        speakGuidance(noBodyFeedback)
                    }
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            if (!isAdded || _fragmentCameraBinding == null) return@runOnUiThread
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    private enum class CalibrationStage {
        FIND_BODY,
        READY_POSE,
        HOLD_STILL
    }
}
