package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.BaseExerciseAnalyzer
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "PoseLandmarker"
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
    private lateinit var db: FirebaseFirestore

    private var exerciseId: String = ""
    private var exerciseName: String = ""
    private var targetCount: Int = 0
    private var dayIndex: Int = 1

    private var isTimed: Boolean = false
    private var unitStr: String = "lần"
    private var currentProgressCount: Int = 0

    private var exerciseAnalyzer: BaseExerciseAnalyzer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        exerciseId = arguments?.getString("exerciseId") ?: ""
        exerciseName = arguments?.getString("exerciseName") ?: ""
        targetCount = arguments?.getInt("targetCount") ?: 0
        dayIndex = arguments?.getInt("dayIndex") ?: 1

        isTimed = (exerciseId == "plank" || exerciseId == "sideplank")
        unitStr = if (isTimed) "giây" else "lần"
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
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
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
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
        com.google.mediapipe.examples.poselandmarker.RestTimerService.stopService(requireContext())

        backgroundExecutor = Executors.newSingleThreadExecutor()

        fragmentCameraBinding.tvWorkoutTitle.text = exerciseName
        fragmentCameraBinding.tvWorkoutTarget.text = "Mục tiêu: $targetCount $unitStr"

        fragmentCameraBinding.tvCounterLabel.text = if (isTimed) "Thời gian giữ chuẩn tư thế" else "Số lần hoàn thành"
        fragmentCameraBinding.tvCounterValue.text = "0 / $targetCount $unitStr"
        fragmentCameraBinding.tvFormFeedback.text = "Đứng trước camera để hệ thống nhận diện khung xương..."

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
            setUpCamera()
        }

        fragmentCameraBinding.btnFinishWorkout.setOnClickListener {
            completeWorkout()
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

    private var isCompletingWorkout = false

    private fun completeWorkout() {
        if (isCompletingWorkout) return
        isCompletingWorkout = true

        val uid = auth.currentUser?.uid ?: return
        fragmentCameraBinding.btnFinishWorkout.isEnabled = false
        
        val docRef = db.collection("users").document(uid)
            .collection("workouts").document("day_$dayIndex")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val workoutDay = document.toObject(WorkoutDay::class.java)
                if (workoutDay != null) {
                    val updatedExercises = workoutDay.exercises.map {
                        if (it.exerciseId == exerciseId) {
                            it.copy(status = 1)
                        } else {
                            it
                        }
                    }
                    docRef.update("exercises", updatedExercises)
                        .addOnSuccessListener {
                            if (isAdded && !isStateSaved) {
                                val hasRemainingPending = updatedExercises.any { it.status == 0 }
                                if (hasRemainingPending) {
                                    com.google.mediapipe.examples.poselandmarker.RestTimerService.startRestTimer(requireContext(), dayIndex)
                                    Toast.makeText(context, "Chúc mừng! Bạn đã hoàn thành bài tập!", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.google.mediapipe.examples.poselandmarker.RestTimerService.stopService(requireContext())
                                    Toast.makeText(context, "Chúc mừng! Bạn đã hoàn thành TẤT CẢ bài tập hôm nay!", Toast.LENGTH_LONG).show()
                                }
                                
                                // Explicitly pop back to WorkoutCalendarFragment (R.id.workout_calendar_fragment)
                                val popped = findNavController().popBackStack(R.id.workout_calendar_fragment, false)
                                if (!popped) {
                                    findNavController().navigate(R.id.workout_calendar_fragment)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            isCompletingWorkout = false
                            fragmentCameraBinding.btnFinishWorkout.isEnabled = true
                            Toast.makeText(context, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    isCompletingWorkout = false
                    fragmentCameraBinding.btnFinishWorkout.isEnabled = true
                }
            } else {
                isCompletingWorkout = false
                fragmentCameraBinding.btnFinishWorkout.isEnabled = true
            }
        }.addOnFailureListener { e ->
            isCompletingWorkout = false
            fragmentCameraBinding.btnFinishWorkout.isEnabled = true
            Toast.makeText(context, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                    exerciseAnalyzer?.let { analyzer ->
                        val result = analyzer.analyze(landmarks)
                        
                        currentProgressCount = result.currentProgress
                        fragmentCameraBinding.tvCounterValue.text = "$currentProgressCount / $targetCount $unitStr"
                        fragmentCameraBinding.tvFormFeedback.text = result.feedback
                        fragmentCameraBinding.tvFormFeedback.setTextColor(result.feedbackColor)

                        // Update overlay with landmarks and custom lines from analysis
                        fragmentCameraBinding.overlay.setResults(
                            resultBundle.results.first(),
                            resultBundle.inputImageHeight,
                            resultBundle.inputImageWidth,
                            RunningMode.LIVE_STREAM,
                            result.customLines
                        )

                        if (result.isComplete) {
                            completeWorkout()
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
                    fragmentCameraBinding.tvFormFeedback.text = "Hãy đứng lùi lại để camera quét được toàn thân"
                    fragmentCameraBinding.tvFormFeedback.setTextColor(Color.parseColor("#FFCA28"))
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
}
