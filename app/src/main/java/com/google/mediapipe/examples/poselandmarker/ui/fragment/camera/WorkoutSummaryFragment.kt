package com.google.mediapipe.examples.poselandmarker.ui.fragment.camera

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentWorkoutSummaryBinding
import com.google.mediapipe.examples.poselandmarker.data.WorkoutSyncScheduler
import com.google.mediapipe.examples.poselandmarker.data.local.TriForceDatabase
import com.google.mediapipe.examples.poselandmarker.model.ProgressionAdvisor
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class WorkoutSummaryFragment : Fragment() {

    private var _binding: FragmentWorkoutSummaryBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var sessionId = ""
    private var exerciseId = ""
    private var exerciseName = ""
    private var dayIndex = 1
    private var targetCount = 0
    private var actualCount = 0
    private var unit = "lần"
    private var formScore = 0
    private var selectedDifficulty = ""
    private var recommendedTarget = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        readArguments()
        renderSummary()
        setupDifficultyButtons()
        observeSyncState()

        binding.btnSummaryContinue.setOnClickListener { returnToCalendar() }
        binding.btnSummaryRepeat.setOnClickListener { repeatExercise() }
    }

    private fun observeSyncState() {
        if (sessionId.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TriForceDatabase.getInstance(requireContext())
                    .workoutSessionDao()
                    .observeById(sessionId)
                    .collectLatest { local ->
                        if (_binding == null || local == null) return@collectLatest
                        binding.tvSyncStatus.visibility = View.VISIBLE
                        when {
                            local.firestoreSynced -> {
                                binding.tvSyncStatus.text = "Đã đồng bộ an toàn"
                                binding.tvSyncStatus.setTextColor(
                                    ContextCompat.getColor(requireContext(), R.color.tri_force_success)
                                )
                            }
                            local.lastSyncError.isNotBlank() -> {
                                binding.tvSyncStatus.text = "Đã lưu trên máy • sẽ tự thử lại"
                                binding.tvSyncStatus.setTextColor(
                                    ContextCompat.getColor(requireContext(), R.color.tri_force_warning)
                                )
                            }
                            else -> {
                                binding.tvSyncStatus.text = "Đã lưu trên thiết bị • đang tự đồng bộ"
                                binding.tvSyncStatus.setTextColor(
                                    ContextCompat.getColor(requireContext(), R.color.tri_force_blue)
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun readArguments() {
        val args = arguments
        sessionId = args?.getString("sessionId").orEmpty()
        exerciseId = args?.getString("exerciseId").orEmpty()
        exerciseName = args?.getString("exerciseName").orEmpty()
        dayIndex = args?.getInt("dayIndex") ?: 1
        targetCount = args?.getInt("targetCount") ?: 0
        actualCount = args?.getInt("actualCount") ?: 0
        unit = args?.getString("unit") ?: "lần"
        formScore = args?.getInt("formScore") ?: 0
    }

    private fun renderSummary() {
        val args = arguments
        val durationSeconds = args?.getInt("durationSeconds") ?: 0
        val issues = args?.getStringArrayList("formIssues").orEmpty()
        val hasPreviousSession = args?.getBoolean("hasPreviousSession") ?: false
        val previousActualCount = args?.getInt("previousActualCount") ?: 0
        val previousFormScore = args?.getInt("previousFormScore") ?: 0
        val hasRemainingPending = args?.getBoolean("hasRemainingPending") ?: true

        binding.tvSyncStatus.visibility = if (args?.getBoolean("syncPending", false) == true) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.tvSummaryExerciseName.text = "$exerciseName • Ngày $dayIndex"
        binding.tvSummaryTitle.text = "KẾT QUẢ BUỔI TẬP"
        binding.tvSummaryHeadline.text = if (targetCount > 0 && actualCount < targetCount) {
            "Đã lưu kết quả"
        } else {
            "Tuyệt vời!"
        }
        binding.tvSummaryScore.text = formScore.toString()
        binding.tvSummaryScoreLabel.text = when {
            formScore >= 85 -> "Kỹ thuật rất tốt"
            formScore >= 70 -> "Tư thế tốt"
            formScore >= 50 -> "Cần ổn định thêm"
            formScore > 0 -> "Cần luyện thêm"
            else -> "Chưa đủ dữ liệu"
        }
        binding.tvSummaryActual.text = "$actualCount/$targetCount $unit"
        binding.tvSummaryDuration.text = String.format(
            Locale.US,
            "%02d:%02d",
            durationSeconds / 60,
            durationSeconds % 60
        )

        binding.tvComparison.text = buildComparisonText(
            hasPreviousSession = hasPreviousSession,
            previousActualCount = previousActualCount,
            previousFormScore = previousFormScore,
            formScore = formScore
        )

        binding.tvFormSummary.text = when {
            issues.isNotEmpty() -> issues.joinToString(separator = "\n") { "• $it" }
            formScore == 0 -> "AI chưa thu đủ dữ liệu tư thế. Hãy đảm bảo toàn thân nằm trong khung hình ở buổi tiếp theo."
            else -> "Tư thế được duy trì ổn định. Tiếp tục giữ nhịp và biên độ chuyển động như buổi này."
        }

        binding.btnSummaryContinue.text = if (hasRemainingPending) {
            "TIẾP TỤC LỘ TRÌNH"
        } else {
            "HOÀN TẤT NGÀY TẬP"
        }
    }

    private fun buildComparisonText(
        hasPreviousSession: Boolean,
        previousActualCount: Int,
        previousFormScore: Int,
        formScore: Int
    ): String {
        if (!hasPreviousSession) {
            return "Buổi đầu tiên đã được lưu. Đây sẽ là mốc để AI theo dõi tiến bộ của bạn."
        }

        val progressParts = mutableListOf<String>()
        val countDelta = actualCount - previousActualCount
        val scoreDelta = formScore - previousFormScore

        when {
            countDelta > 0 -> progressParts += "tăng $countDelta $unit"
            countDelta < 0 -> progressParts += "giảm ${-countDelta} $unit"
        }
        when {
            scoreDelta > 0 -> progressParts += "điểm kỹ thuật tăng $scoreDelta"
            scoreDelta < 0 -> progressParts += "điểm kỹ thuật giảm ${-scoreDelta}"
        }

        return if (progressParts.isEmpty()) {
            "Bạn đang giữ vững kết quả so với buổi trước."
        } else {
            progressParts.joinToString(", ").replaceFirstChar { it.uppercase() } + " so với buổi trước."
        }
    }

    private fun setupDifficultyButtons() {
        binding.btnDifficultyEasy.setOnClickListener {
            selectDifficulty(ProgressionAdvisor.TOO_EASY, binding.btnDifficultyEasy)
        }
        binding.btnDifficultyRight.setOnClickListener {
            selectDifficulty(ProgressionAdvisor.JUST_RIGHT, binding.btnDifficultyRight)
        }
        binding.btnDifficultyHard.setOnClickListener {
            selectDifficulty(ProgressionAdvisor.TOO_HARD, binding.btnDifficultyHard)
        }
        binding.btnApplyRecommendedTarget.setOnClickListener {
            applyRecommendedTarget()
        }
        renderDifficultySelection(null)
    }

    private fun selectDifficulty(value: String, selectedButton: MaterialButton) {
        if (selectedDifficulty == value) return
        selectedDifficulty = value
        renderDifficultySelection(selectedButton)
        renderAdaptiveRecommendation(value)

        viewLifecycleOwner.lifecycleScope.launch {
            TriForceDatabase.getInstance(requireContext())
                .workoutSessionDao()
                .updateDifficulty(sessionId, value)
            WorkoutSyncScheduler.enqueue(requireContext())
        }

        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val batch = db.batch()
        batch.set(
            userRef.collection("workout_sessions").document(sessionId),
            mapOf("difficulty" to value),
            SetOptions.merge()
        )
        batch.set(
            userRef.collection("exercise_history").document(exerciseId),
            mapOf("lastDifficulty" to value),
            SetOptions.merge()
        )
        batch.commit().addOnFailureListener {
            if (isAdded) {
                Toast.makeText(
                    context,
                    "Phản hồi đã lưu trên máy và sẽ tự đồng bộ.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderAdaptiveRecommendation(difficulty: String) {
        if (targetCount <= 0) {
            binding.adaptiveRecommendationCard.visibility = View.GONE
            return
        }

        recommendedTarget = ProgressionAdvisor.recommendTarget(
            currentTarget = targetCount,
            actualCount = actualCount,
            formScore = formScore,
            difficulty = difficulty
        )

        binding.adaptiveRecommendationCard.visibility = View.VISIBLE
        binding.btnApplyRecommendedTarget.visibility =
            if (recommendedTarget == targetCount) View.GONE else View.VISIBLE
        binding.btnApplyRecommendedTarget.isEnabled = true
        binding.btnApplyRecommendedTarget.text = "ÁP DỤNG CHO CÁC BUỔI SẮP TỚI"

        binding.tvAdaptiveRecommendation.text = when {
            formScore == 0 && recommendedTarget == targetCount ->
                "Giữ mục tiêu $targetCount $unit vì AI chưa thu đủ dữ liệu form. Hãy đặt toàn thân trong khung hình ở buổi tiếp theo."
            formScore in 1..54 && recommendedTarget < targetCount ->
                "Giảm ${ProgressionAdvisor.LOW_FORM_ADJUSTMENT_PERCENT}%: từ $targetCount xuống $recommendedTarget $unit vì điểm form còn thấp. Ưu tiên kỹ thuật an toàn trước."
            difficulty == ProgressionAdvisor.TOO_EASY && recommendedTarget > targetCount ->
                "Tăng ${ProgressionAdvisor.TOO_EASY_ADJUSTMENT_PERCENT}%: từ $targetCount lên $recommendedTarget $unit để buổi sau đủ thử thách hơn."
            difficulty == ProgressionAdvisor.TOO_EASY && formScore in 1..69 ->
                "Giữ mục tiêu $targetCount $unit và ưu tiên cải thiện kỹ thuật trước khi tăng khối lượng."
            difficulty == ProgressionAdvisor.TOO_HARD && recommendedTarget < targetCount ->
                "Giảm ${ProgressionAdvisor.TOO_HARD_ADJUSTMENT_PERCENT}%: từ $targetCount xuống $recommendedTarget $unit để tập đủ bài và giữ form tốt hơn."
            else ->
                "Mục tiêu $targetCount $unit đang phù hợp. Tiếp tục duy trì trong buổi kế tiếp."
        }
    }

    private fun applyRecommendedTarget() {
        val uid = auth.currentUser?.uid ?: return
        if (recommendedTarget <= 0 || recommendedTarget == targetCount) return

        binding.btnApplyRecommendedTarget.isEnabled = false
        binding.btnApplyRecommendedTarget.text = "ĐANG ÁP DỤNG..."

        db.collection("users")
            .document(uid)
            .collection("workouts")
            .get()
            .addOnSuccessListener workoutsSuccess@ { snapshot ->
                if (!isAdded || _binding == null) return@workoutsSuccess

                val batch = db.batch()
                var updatedWorkoutCount = 0

                snapshot.documents.forEach { document ->
                    val workoutDay = document.toObject(WorkoutDay::class.java)
                        ?: return@forEach
                    if (workoutDay.dayIndex <= dayIndex) return@forEach

                    var changed = false
                    val updatedExercises = workoutDay.exercises.map { exercise ->
                        if (exercise.exerciseId == exerciseId && exercise.status == 0) {
                            changed = true
                            exercise.copy(targetCount = recommendedTarget)
                        } else {
                            exercise
                        }
                    }

                    if (changed) {
                        batch.update(document.reference, "exercises", updatedExercises)
                        updatedWorkoutCount++
                    }
                }

                if (updatedWorkoutCount == 0) {
                    restoreRecommendationButton()
                    Toast.makeText(
                        requireContext(),
                        "Không còn buổi $exerciseName nào sắp tới để cập nhật.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@workoutsSuccess
                }

                batch.commit()
                    .addOnSuccessListener commitSuccess@ {
                        if (!isAdded || _binding == null) return@commitSuccess
                        binding.btnApplyRecommendedTarget.text = "ĐÃ ÁP DỤNG"
                        Toast.makeText(
                            requireContext(),
                            "Đã cập nhật mục tiêu cho $updatedWorkoutCount buổi sắp tới.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener commitFailure@ { error ->
                        if (!isAdded || _binding == null) return@commitFailure
                        restoreRecommendationButton()
                        Toast.makeText(
                            requireContext(),
                            "Chưa thể cập nhật mục tiêu: ${error.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener workoutsFailure@ { error ->
                if (!isAdded || _binding == null) return@workoutsFailure
                restoreRecommendationButton()
                Toast.makeText(
                    requireContext(),
                    "Chưa thể đọc lịch tập: ${error.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun restoreRecommendationButton() {
        binding.btnApplyRecommendedTarget.isEnabled = true
        binding.btnApplyRecommendedTarget.text = "THỬ ÁP DỤNG LẠI"
    }

    private fun renderDifficultySelection(selectedButton: MaterialButton?) {
        listOf(
            binding.btnDifficultyEasy,
            binding.btnDifficultyRight,
            binding.btnDifficultyHard
        ).forEach { button ->
            val active = button === selectedButton
            val background = if (active) R.color.tri_force_blue else R.color.tri_force_white
            val text = if (active) R.color.tri_force_white else R.color.tri_force_text_secondary
            val stroke = if (active) R.color.tri_force_blue else R.color.tri_force_stroke
            button.backgroundTintList = colorStateList(background)
            button.setTextColor(ContextCompat.getColor(requireContext(), text))
            button.strokeColor = colorStateList(stroke)
        }
    }

    private fun colorStateList(colorRes: Int): ColorStateList =
        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))

    private fun repeatExercise() {
        val cameraArgs = Bundle().apply {
            putString("exerciseId", exerciseId)
            putString("exerciseName", exerciseName)
            putInt("targetCount", targetCount)
            putInt("dayIndex", dayIndex)
        }
        findNavController().navigate(R.id.action_workout_summary_to_camera, cameraArgs)
    }

    private fun returnToCalendar() {
        val popped = findNavController().popBackStack(R.id.workout_calendar_fragment, false)
        if (!popped) {
            findNavController().navigate(R.id.workout_calendar_fragment)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
