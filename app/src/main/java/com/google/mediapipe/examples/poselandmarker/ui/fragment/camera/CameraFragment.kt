package com.google.mediapipe.examples.poselandmarker.ui.fragment.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.mediapipe.examples.poselandmarker.analysis.BaseExerciseAnalyzer
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
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "PoseLandmarker"
        private const val VOICE_MIN_INTERVAL_MS = 2_500L
        private const val VOICE_REPEAT_INTERVAL_MS = 7_000L
        private const val FORM_SAMPLE_INTERVAL_MS = 500L
        private const val CALIBRATION_STABLE_FRAMES = 24
        private const val VOICE_PREFERENCES = "tri_force_voice_coach"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val VOICE_OFF = "off"
        private const val VOICE_FEMALE = "female"
        private const val VOICE_MALE = "male"
        private const val VOICE_DEFAULT = "default"
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

    private var textToSpeech: TextToSpeech? = null
    private var isVoiceReady = false
    private var lastSpokenFeedback = ""
    private var lastVoiceTimeMs = 0L
    private var lastSpokenProgress = 0
    private var hasAnnouncedCompletion = false
    private var voiceMode = VOICE_FEMALE

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

        voiceMode = requireContext()
            .getSharedPreferences(VOICE_PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_VOICE_MODE, VOICE_FEMALE) ?: VOICE_FEMALE

        isTimed = (exerciseId == "plank" || exerciseId == "sideplank")
        unitStr = if (isTimed) "giây" else "lần"
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
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
        textToSpeech?.stop()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isVoiceReady = false
        _fragmentCameraBinding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
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
        workoutStartedAtMs = SystemClock.elapsedRealtime()

        fragmentCameraBinding.tvWorkoutTitle.text = exerciseName
        fragmentCameraBinding.tvWorkoutTarget.text = "Mục tiêu: $targetCount $unitStr"

        fragmentCameraBinding.tvCounterLabel.text = if (isTimed) "Thời gian giữ chuẩn tư thế" else "Số lần hoàn thành"
        fragmentCameraBinding.tvCounterValue.text = "0 / $targetCount $unitStr"
        fragmentCameraBinding.tvFormFeedback.text = "Đứng trước camera để hệ thống nhận diện khung xương..."
        fragmentCameraBinding.btnFinishWorkout.isEnabled = false
        fragmentCameraBinding.btnFinishWorkout.text = "ĐANG HIỆU CHỈNH CAMERA..."
        updateVoiceButtonState()

        initializeVoiceGuidance()

        exerciseAnalyzer = BaseExerciseAnalyzer.create(
            exerciseId, exerciseName, targetCount, isTimed, unitStr
        )

        fragmentCameraBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
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

        fragmentCameraBinding.btnFinishWorkout.setOnClickListener {
            confirmManualCompletion()
        }

        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        initBottomSheetControls()
    }

    private fun initializeVoiceGuidance() {
        textToSpeech = TextToSpeech(requireContext().applicationContext) { status ->
            val engine = textToSpeech ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Không thể khởi tạo hướng dẫn bằng giọng nói")
                return@TextToSpeech
            }

            val languageResult = engine.setLanguage(Locale.forLanguageTag("vi-VN"))
            isVoiceReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            if (!isVoiceReady) {
                Log.w(TAG, "Thiết bị không có dữ liệu giọng đọc tiếng Việt")
                return@TextToSpeech
            }

            applyVoicePreference()

            if (_fragmentCameraBinding == null) return@TextToSpeech
            speakGuidance(
                "Bắt đầu hiệu chỉnh camera. Đứng vào khung hình để nhìn thấy toàn thân.",
                force = true
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
        val modes = arrayOf(VOICE_OFF, VOICE_FEMALE, VOICE_MALE, VOICE_DEFAULT)
        val selected = modes.indexOf(voiceMode).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Voice coach")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                voiceMode = modes[which]
                requireContext()
                    .getSharedPreferences(VOICE_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VOICE_MODE, voiceMode)
                    .apply()
                applyVoicePreference()
                updateVoiceButtonState()
                if (voiceMode != VOICE_OFF) {
                    speakGuidance("Đã cập nhật giọng hướng dẫn.", force = true)
                } else {
                    textToSpeech?.stop()
                }
                dialog.dismiss()
            }
            .setNeutralButton("HIỆU CHỈNH LẠI") { _, _ -> resetCalibration() }
            .setNegativeButton("ĐÓNG", null)
            .show()
    }

    private fun applyVoicePreference() {
        val engine = textToSpeech ?: return
        val vietnameseVoices = engine.voices
            ?.filter { it.locale.language == "vi" }
            .orEmpty()
        val hints = when (voiceMode) {
            VOICE_FEMALE -> listOf("female", "woman", "vif", "viet_female")
            VOICE_MALE -> listOf("male", "man", "vim", "viet_male")
            else -> emptyList()
        }
        val preferredVoice = vietnameseVoices.firstOrNull { voice ->
            hints.any { hint -> voice.name.contains(hint, ignoreCase = true) }
        } ?: vietnameseVoices.firstOrNull()
        preferredVoice?.let { engine.voice = it }

        engine.setSpeechRate(1.0f)
        engine.setPitch(
            when (voiceMode) {
                VOICE_FEMALE -> 1.06f
                VOICE_MALE -> 0.94f
                else -> 1.0f
            }
        )
    }

    private fun updateVoiceButtonState() {
        _fragmentCameraBinding?.btnVoiceSettings?.alpha =
            if (voiceMode == VOICE_OFF) 0.45f else 1f
    }

    private fun handleVoiceGuidance(feedback: String, progress: Int, isComplete: Boolean) {
        if (isComplete) {
            if (!hasAnnouncedCompletion) {
                hasAnnouncedCompletion = true
                speakGuidance("Hoàn thành bài tập.", force = true)
            }
            return
        }

        if (progress > lastSpokenProgress) {
            lastSpokenProgress = progress
            val shouldAnnounceProgress = !isTimed || progress % 5 == 0
            if (shouldAnnounceProgress) {
                val progressText = if (isTimed) "$progress giây" else progress.toString()
                speakGuidance(progressText, force = true)
                return
            }
        }

        speakGuidance(feedback)
    }

    private fun speakGuidance(text: String, force: Boolean = false) {
        if (!isVoiceReady || voiceMode == VOICE_OFF || text.isBlank()) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastVoiceTimeMs
        val isRepeatedFeedback = text == lastSpokenFeedback
        if (!force && elapsed < VOICE_MIN_INTERVAL_MS) return
        if (!force && isRepeatedFeedback && elapsed < VOICE_REPEAT_INTERVAL_MS) return

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "tri_force_guidance_${now}"
        )
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

    private fun handleCalibration(landmarks: List<NormalizedLandmark>): Boolean {
        if (isCalibrated) return true

        val analyzer = exerciseAnalyzer ?: return false
        if (!analyzer.isFullBodyVisible(landmarks)) {
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
        val stableProgress = 55 +
            (calibrationStableFrames * 45 / CALIBRATION_STABLE_FRAMES).coerceAtMost(45)
        updateCalibration(
            CalibrationStage.HOLD_STILL,
            "Giữ nguyên tư thế chuẩn bị trong giây lát...",
            stableProgress
        )

        if (calibrationStableFrames >= CALIBRATION_STABLE_FRAMES) {
            isCalibrated = true
            workoutStartedAtMs = SystemClock.elapsedRealtime()
            fragmentCameraBinding.tvCalibrationStep.text = "CAMERA ĐÃ SẴN SÀNG"
            fragmentCameraBinding.tvCalibrationMessage.text = "Bắt đầu $exerciseName ngay bây giờ."
            fragmentCameraBinding.progressCalibration.progress = 100
            fragmentCameraBinding.btnFinishWorkout.isEnabled = true
            fragmentCameraBinding.btnFinishWorkout.text = "HOÀN THÀNH BÀI TẬP"
            fragmentCameraBinding.tvFormFeedback.text = "Camera đã sẵn sàng. Bắt đầu bài tập!"
            speakGuidance("Hiệu chỉnh hoàn tất. Bắt đầu $exerciseName.", force = true)
            fragmentCameraBinding.calibrationCard.postDelayed({
                if (_fragmentCameraBinding != null && isCalibrated) {
                    fragmentCameraBinding.calibrationCard.visibility = View.GONE
                }
            }, 1_200L)
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
        fragmentCameraBinding.calibrationCard.visibility = View.VISIBLE
        fragmentCameraBinding.tvCalibrationStep.text = when (stage) {
            CalibrationStage.FIND_BODY -> "HIỆU CHỈNH CAMERA • BƯỚC 1/3"
            CalibrationStage.READY_POSE -> "HIỆU CHỈNH CAMERA • BƯỚC 2/3"
            CalibrationStage.HOLD_STILL -> "HIỆU CHỈNH CAMERA • BƯỚC 3/3"
        }
        fragmentCameraBinding.tvCalibrationMessage.text = message
        fragmentCameraBinding.progressCalibration.progress = progress.coerceIn(0, 100)
        if (stageChanged) speakGuidance(message, force = true)
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
        _fragmentCameraBinding?.let { binding ->
            binding.calibrationCard.visibility = View.VISIBLE
            binding.tvCalibrationStep.text = "HIỆU CHỈNH CAMERA • BƯỚC 1/3"
            binding.tvCalibrationMessage.text =
                "Đứng vào khung hình để camera nhìn thấy toàn thân."
            binding.progressCalibration.progress = 8
            binding.btnFinishWorkout.isEnabled = false
            binding.btnFinishWorkout.text = "ĐANG HIỆU CHỈNH CAMERA..."
        }
        speakGuidance("Bắt đầu hiệu chỉnh lại camera.", force = true)
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
        fragmentCameraBinding.btnFinishWorkout.isEnabled = false

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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = TriForceDatabase.getInstance(requireContext()).workoutSessionDao()
                val previous = withContext(Dispatchers.IO) {
                    val old = dao.getRecent(uid, 100)
                        .firstOrNull { it.exerciseId == exerciseId }
                    dao.upsert(session.toLocalEntity(uid))
                    old
                }
                if (!isAdded || isStateSaved || _fragmentCameraBinding == null) return@launch

                WorkoutSyncScheduler.enqueue(requireContext())
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
            } catch (error: Exception) {
                showWorkoutSaveError("Không thể lưu kết quả trên thiết bị: ${error.localizedMessage}")
            }
        }
    }

    private fun showWorkoutSaveError(message: String) {
        isCompletingWorkout = false
        _fragmentCameraBinding?.btnFinishWorkout?.isEnabled = true
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
            if (poseLandmarkerHelper.minPoseDetectionConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence >= 0.2) {
                poseLandmarkerHelper.minPosePresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence <= 0.8) {
                poseLandmarkerHelper.minPosePresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    poseLandmarkerHelper.currentDelegate = p2
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
                    poseLandmarkerHelper.currentModel = p2
                    updateControlsUi()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
    }

    private fun updateControlsUi() {
        if (this::poseLandmarkerHelper.isInitialized) {
            fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
                String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPoseDetectionConfidence)
            fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
                String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPoseTrackingConfidence)
            fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
                String.format(Locale.US, "%.2f", poseLandmarkerHelper.minPosePresenceConfidence)

            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
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

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
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
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed, attempting fallback camera", exc)
            try {
                val fallbackSelector = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, fallbackSelector, preview, imageAnalyzer)
                preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback camera binding failed", e)
            }
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pose Analysis and Exercise Specific Logic
                val hasLandmarks = resultBundle.results.first().landmarks().isNotEmpty()
                if (hasLandmarks) {
                    val landmarks = resultBundle.results.first().landmarks().first()
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM,
                        emptyList()
                    )
                    exerciseAnalyzer?.let { analyzer ->
                        if (handleCalibration(landmarks)) {
                            val result = analyzer.analyze(landmarks)
                        
                            currentProgressCount = result.currentProgress
                            fragmentCameraBinding.tvCounterValue.text = "$currentProgressCount / $targetCount $unitStr"
                            fragmentCameraBinding.tvFormFeedback.text = result.feedback
                            fragmentCameraBinding.tvFormFeedback.setTextColor(result.feedbackColor)
                            trackFormSample(result.feedback, result.feedbackColor)
                            handleVoiceGuidance(
                                feedback = result.feedback,
                                progress = currentProgressCount,
                                isComplete = result.isComplete
                            )

                            // Update overlay with landmarks and custom lines from analysis
                            fragmentCameraBinding.overlay.setResults(
                                resultBundle.results.first(),
                                resultBundle.inputImageHeight,
                                resultBundle.inputImageWidth,
                                RunningMode.LIVE_STREAM,
                                result.customLines
                            )

                            if (result.isComplete) {
                                completeWorkout(completedAutomatically = true)
                            }
                        }
                    }
                } else {
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM,
                        emptyList()
                    )
                    val noBodyFeedback = "Hãy đứng lùi lại để camera quét được toàn thân"
                    fragmentCameraBinding.tvFormFeedback.text = noBodyFeedback
                    fragmentCameraBinding.tvFormFeedback.setTextColor(Color.parseColor("#FFCA28"))
                    if (!isCalibrated) {
                        calibrationStableFrames = 0
                        updateCalibration(CalibrationStage.FIND_BODY, noBodyFeedback, 8)
                    } else {
                        speakGuidance(noBodyFeedback)
                    }
                }
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
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
