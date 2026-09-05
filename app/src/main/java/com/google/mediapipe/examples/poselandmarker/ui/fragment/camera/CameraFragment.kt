package com.google.mediapipe.examples.poselandmarker.ui.fragment.camera

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.analysis.BaseExerciseAnalyzer
import com.google.mediapipe.examples.poselandmarker.viewmodel.MainViewModel
import com.google.mediapipe.examples.poselandmarker.analysis.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.service.RestTimerService
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCatalog
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.ui.fragment.onboarding.PermissionsFragment
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private var unitStr: String = ""
    private var currentProgressCount: Int = 0

    private var exerciseAnalyzer: BaseExerciseAnalyzer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraFacing = CameraSelector.LENS_FACING_FRONT
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        exerciseId = ExerciseCatalog.canonicalId(arguments?.getString("exerciseId").orEmpty())
        val catalogExercise = ExerciseCatalog.find(requireContext(), exerciseId)
        exerciseName = catalogExercise?.name ?: arguments?.getString("exerciseName").orEmpty()
        targetCount = (arguments?.getInt("targetCount") ?: 0).takeIf { it > 0 }
            ?: catalogExercise?.defaultTarget
            ?: 1
        dayIndex = arguments?.getInt("dayIndex") ?: 1

        isTimed = catalogExercise?.isTimed ?: (exerciseId == "plank" || exerciseId == "sideplank")
        unitStr = catalogExercise?.unit ?: getString(if (isTimed) R.string.unit_seconds else R.string.unit_reps)
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
        }
        if (this::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
            backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
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

            if (this::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
                backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
            }
        }
    }

    override fun onDestroyView() {
        cameraProvider?.unbindAll()
        if (this::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdownNow()
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

        fragmentCameraBinding.tvWorkoutTitle.text = exerciseName
        fragmentCameraBinding.tvWorkoutTarget.text =
            getString(R.string.camera_target_format, targetCount, unitStr)

        fragmentCameraBinding.tvCounterLabel.setText(
            if (isTimed) R.string.camera_counter_timed else R.string.camera_counter_reps
        )
        fragmentCameraBinding.tvCounterValue.text =
            getString(R.string.camera_counter_format, 0, targetCount, unitStr)
        fragmentCameraBinding.tvFormFeedback.setText(R.string.camera_detecting)

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

    }

    private var isCompletingWorkout = false

    private fun completeWorkout() {
        if (isCompletingWorkout) return
        isCompletingWorkout = true

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            isCompletingWorkout = false
            Toast.makeText(context, R.string.camera_sign_in_required, Toast.LENGTH_SHORT).show()
            return
        }
        fragmentCameraBinding.btnFinishWorkout.isEnabled = false
        
        val docRef = db.collection("users").document(uid)
            .collection("workouts").document("day_$dayIndex")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val workoutDay = document.toObject(WorkoutDay::class.java)
                if (workoutDay != null) {
                    var updatedOneExercise = false
                    val updatedExercises = workoutDay.exercises.map {
                        if (!updatedOneExercise && it.status == 0 &&
                            ExerciseCatalog.canonicalId(it.exerciseId) == exerciseId
                        ) {
                            updatedOneExercise = true
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
                                    RestTimerService.startRestTimer(requireContext(), dayIndex)
                                    Toast.makeText(context, R.string.camera_completed_one, Toast.LENGTH_SHORT).show()
                                } else {
                                    RestTimerService.stopService(requireContext())
                                    Toast.makeText(context, R.string.camera_completed_all, Toast.LENGTH_LONG).show()
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
                            _fragmentCameraBinding?.btnFinishWorkout?.isEnabled = true
                            Toast.makeText(
                                context,
                                getString(R.string.camera_update_error, e.localizedMessage.orEmpty()),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    isCompletingWorkout = false
                    _fragmentCameraBinding?.btnFinishWorkout?.isEnabled = true
                    Toast.makeText(context, R.string.camera_workout_not_found, Toast.LENGTH_SHORT).show()
                }
            } else {
                isCompletingWorkout = false
                _fragmentCameraBinding?.btnFinishWorkout?.isEnabled = true
                Toast.makeText(context, R.string.camera_workout_not_found, Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            isCompletingWorkout = false
            _fragmentCameraBinding?.btnFinishWorkout?.isEnabled = true
            Toast.makeText(
                context,
                getString(R.string.database_error_with_detail, e.localizedMessage.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
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

        // Prioritize front camera if available on device
        val hasFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val hasBackCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        if (cameraFacing == CameraSelector.LENS_FACING_FRONT && !hasFrontCamera && hasBackCamera) {
            cameraFacing = CameraSelector.LENS_FACING_BACK
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
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
                val fallbackFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                val fallbackSelector = CameraSelector.Builder().requireLensFacing(fallbackFacing).build()
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, fallbackSelector, preview, imageAnalyzer)
                preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
                cameraFacing = fallbackFacing
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
        } else {
            imageProxy.close()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _fragmentCameraBinding?.viewFinder?.display?.let { display ->
            imageAnalyzer?.targetRotation = display.rotation
        }
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // Pose Analysis and Exercise Specific Logic
                val hasLandmarks = resultBundle.results.first().landmarks().isNotEmpty()
                if (hasLandmarks) {
                    val landmarks = resultBundle.results.first().landmarks().first()
                    exerciseAnalyzer?.let { analyzer ->
                        val result = analyzer.analyze(landmarks)
                        
                        currentProgressCount = result.currentProgress
                        fragmentCameraBinding.tvCounterValue.text = getString(
                            R.string.camera_counter_format,
                            currentProgressCount,
                            targetCount,
                            unitStr
                        )
                        fragmentCameraBinding.tvFormFeedback.text =
                            ExerciseCatalog.localizeFeedback(requireContext(), result.feedback)
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
                    fragmentCameraBinding.tvFormFeedback.setText(R.string.camera_move_back)
                    fragmentCameraBinding.tvFormFeedback.setTextColor(Color.parseColor("#FFCA28"))
                }
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            context?.let { Toast.makeText(it, error, Toast.LENGTH_SHORT).show() }
        }
    }
}
