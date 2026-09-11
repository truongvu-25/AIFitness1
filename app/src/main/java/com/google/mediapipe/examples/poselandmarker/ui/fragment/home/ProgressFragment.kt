package com.google.mediapipe.examples.poselandmarker.ui.fragment.home

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProgressBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemAchievementBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemExerciseProgressBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemRecentWorkoutBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemWeeklyActivityBarBinding
import com.google.mediapipe.examples.poselandmarker.data.local.TriForceDatabase
import com.google.mediapipe.examples.poselandmarker.data.local.toLocalEntity
import com.google.mediapipe.examples.poselandmarker.data.local.toModel
import com.google.mediapipe.examples.poselandmarker.model.WorkoutSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var weeklyGoal = DEFAULT_WEEKLY_GOAL
    private var latestSessions: List<WorkoutSession> = emptyList()
    private var cachedSessions: List<WorkoutSession> = emptyList()
    private var currentWeeklyWorkoutCount = 0
    private var currentStreak = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnProgressBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnEditWeeklyGoal.setOnClickListener {
            showWeeklyGoalDialog()
        }

        weeklyGoal = readCachedWeeklyGoal()
        loadCloudWeeklyGoal()
        loadWorkoutProgress()
    }

    private fun loadWorkoutProgress() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            renderProgress(emptyList())
            return
        }

        binding.progressDashboardLoading.visibility = View.VISIBLE
        binding.progressScrollView.visibility = View.INVISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            cachedSessions = TriForceDatabase.getInstance(requireContext())
                .workoutSessionDao()
                .getRecent(uid, 100)
                .map { it.toModel() }
            if (cachedSessions.isNotEmpty() && _binding != null) {
                renderProgress(cachedSessions)
            }
            loadRemoteProgress(uid)
        }
    }

    private fun loadRemoteProgress(uid: String) {
        db.collection("users")
            .document(uid)
            .collection("workout_sessions")
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val sessions = snapshot.documents.mapNotNull { document ->
                    document.toObject(WorkoutSession::class.java)?.let { session ->
                        if (session.id.isBlank()) session.copy(id = document.id) else session
                    }
                }
                val merged = (cachedSessions + sessions)
                    .distinctBy { it.id }
                    .sortedByDescending { it.completedAt }
                    .take(100)
                renderProgress(merged)
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = TriForceDatabase.getInstance(requireContext()).workoutSessionDao()
                    sessions.forEach { remote ->
                        if (dao.getById(remote.id) == null) {
                            dao.insertIfAbsent(remote.toLocalEntity(uid, firestoreSynced = true))
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                if (!isAdded || _binding == null) return@addOnFailureListener

                renderProgress(cachedSessions)
                Toast.makeText(
                    requireContext(),
                    "Đang hiển thị dữ liệu trên máy. Sẽ đồng bộ khi có mạng.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun renderProgress(sessions: List<WorkoutSession>) {
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val sevenDayStart = shiftDay(todayStart, -6)
        val weeklySessions = sessions.filter { it.completedAt >= sevenDayStart }
        val totalWeeklySeconds = weeklySessions.sumOf { it.durationSeconds.coerceAtLeast(0) }
        val weeklyMinutes = if (totalWeeklySeconds == 0) 0 else (totalWeeklySeconds + 59) / 60
        val formScores = weeklySessions.map { it.formScore }.filter { it > 0 }

        latestSessions = sessions
        currentWeeklyWorkoutCount = weeklySessions.size
        currentStreak = calculateCurrentStreak(sessions, todayStart)

        binding.tvWeeklyWorkoutCount.text = weeklySessions.size.toString()
        binding.tvWeeklyMinutes.text = weeklyMinutes.toString()
        binding.tvCurrentStreak.text = currentStreak.toString()
        binding.tvAverageFormScore.text = if (formScores.isEmpty()) {
            "—"
        } else {
            formScores.average().roundToInt().toString()
        }
        binding.tvProgressInsight.text = progressInsight(weeklySessions.size)

        renderWeeklyGoal()
        renderWeeklyChart(weeklySessions, todayStart)
        renderAchievements(sessions)
        renderRecentActivity(sessions)
        renderExerciseProgress(sessions)

        binding.progressDashboardLoading.visibility = View.GONE
        binding.progressScrollView.visibility = View.VISIBLE
    }

    private fun loadCloudWeeklyGoal() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val cloudGoal = document.getLong(WEEKLY_GOAL_FIELD)?.toInt()
                    ?.takeIf { it in MIN_WEEKLY_GOAL..MAX_WEEKLY_GOAL }
                    ?: return@addOnSuccessListener

                weeklyGoal = cloudGoal
                cacheWeeklyGoal(cloudGoal)
                renderWeeklyGoal()
                renderAchievements(latestSessions)
            }
    }

    private fun showWeeklyGoalDialog() {
        val goals = (MIN_WEEKLY_GOAL..MAX_WEEKLY_GOAL).toList()
        val labels = goals.map { "$it buổi / tuần" }.toTypedArray()
        val selectedIndex = goals.indexOf(weeklyGoal).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Chọn mục tiêu tuần")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                weeklyGoal = goals[which]
                cacheWeeklyGoal(weeklyGoal)
                saveWeeklyGoalToCloud(weeklyGoal)
                renderWeeklyGoal()
                renderAchievements(latestSessions)
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun renderWeeklyGoal() {
        if (_binding == null) return

        val completed = currentWeeklyWorkoutCount
        val remaining = (weeklyGoal - completed).coerceAtLeast(0)
        val goalReached = completed >= weeklyGoal
        binding.tvWeeklyGoalValue.text = "$completed / $weeklyGoal buổi"
        binding.progressWeeklyGoal.progress =
            (completed * 100 / weeklyGoal).coerceIn(0, 100)
        binding.progressWeeklyGoal.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (goalReached) R.color.tri_force_success else R.color.tri_force_blue
            )
        )
        binding.tvWeeklyGoalHint.text = when {
            goalReached -> "Bạn đã hoàn thành mục tiêu tuần. Hãy giữ nhịp và ưu tiên phục hồi."
            completed == 0 -> "Hoàn thành buổi đầu tiên để bắt đầu mục tiêu tuần này."
            else -> "Còn $remaining buổi để hoàn thành mục tiêu tuần này."
        }
    }

    private fun renderAchievements(sessions: List<WorkoutSession>) {
        if (_binding == null) return

        val achievements = listOf(
            Achievement("Khởi động", "Hoàn thành buổi tập đầu tiên", sessions.isNotEmpty()),
            Achievement("Chinh phục", "Hoàn thành đủ một mục tiêu", sessions.any {
                it.targetCount > 0 && it.actualCount >= it.targetCount
            }),
            Achievement("Form chuẩn", "Đạt điểm kỹ thuật từ 85", sessions.any { it.formScore >= 85 }),
            Achievement("Kiên định", "Duy trì chuỗi tập 3 ngày", currentStreak >= 3),
            Achievement("Tuần mạnh mẽ", "Hoàn thành mục tiêu tuần", currentWeeklyWorkoutCount >= weeklyGoal),
            Achievement("Bền bỉ", "Hoàn thành tổng cộng 10 buổi", sessions.size >= 10)
        )

        binding.achievementContainer.removeAllViews()
        achievements.forEach { achievement ->
            val item = ItemAchievementBinding.inflate(
                layoutInflater,
                binding.achievementContainer,
                false
            )
            val iconColor = if (achievement.unlocked) {
                R.color.tri_force_blue
            } else {
                R.color.tri_force_text_disabled
            }
            val titleColor = if (achievement.unlocked) {
                R.color.tri_force_text_primary
            } else {
                R.color.tri_force_text_tertiary
            }

            item.tvAchievementTitle.text = achievement.title
            item.tvAchievementDescription.text = achievement.description
            item.ivAchievementIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), iconColor)
            )
            item.tvAchievementTitle.setTextColor(
                ContextCompat.getColor(requireContext(), titleColor)
            )
            item.achievementCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (achievement.unlocked) R.color.tri_force_white
                    else R.color.tri_force_surface_soft
                )
            )
            item.achievementCard.strokeColor = ContextCompat.getColor(
                requireContext(),
                if (achievement.unlocked) R.color.tri_force_blue_light
                else R.color.tri_force_stroke
            )

            binding.achievementContainer.addView(item.root)
        }
    }

    private fun readCachedWeeklyGoal(): Int {
        val cached = requireContext()
            .getSharedPreferences(PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
            .getInt(weeklyGoalKey(), DEFAULT_WEEKLY_GOAL)
        return cached.coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL)
    }

    private fun cacheWeeklyGoal(goal: Int) {
        requireContext()
            .getSharedPreferences(PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(weeklyGoalKey(), goal)
            .apply()
    }

    private fun saveWeeklyGoalToCloud(goal: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(uid)
            .set(mapOf(WEEKLY_GOAL_FIELD to goal), SetOptions.merge())
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Mục tiêu đã lưu trên máy nhưng chưa đồng bộ được.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun weeklyGoalKey(): String {
        return "weekly_goal_${auth.currentUser?.uid.orEmpty()}"
    }

    private fun renderWeeklyChart(
        weeklySessions: List<WorkoutSession>,
        todayStart: Long
    ) {
        val countsByDay = weeklySessions.groupingBy {
            startOfDay(it.completedAt)
        }.eachCount()
        val maximumCount = countsByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        binding.weeklyChartContainer.removeAllViews()

        for (offset in -6..0) {
            val dayStart = shiftDay(todayStart, offset)
            val count = countsByDay[dayStart] ?: 0
            val item = ItemWeeklyActivityBarBinding.inflate(
                layoutInflater,
                binding.weeklyChartContainer,
                false
            )

            item.tvActivityCount.text = if (count > 0) count.toString() else ""
            item.tvActivityDay.text = shortDayLabel(dayStart)

            val barHeightDp = if (count == 0) {
                4
            } else {
                (18 + 64f * count / maximumCount).roundToInt()
            }
            item.barActivity.layoutParams = item.barActivity.layoutParams.apply {
                height = dpToPx(barHeightDp)
            }
            item.barActivity.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (offset == 0) R.color.tri_force_blue else R.color.tri_force_blue_light
                )
            )
            item.tvActivityDay.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (offset == 0) R.color.tri_force_blue else R.color.tri_force_text_tertiary
                )
            )

            binding.weeklyChartContainer.addView(item.root)
        }
    }

    private fun renderRecentActivity(sessions: List<WorkoutSession>) {
        val recentSessions = sessions
            .sortedByDescending { it.completedAt }
            .take(5)

        binding.recentWorkoutContainer.removeAllViews()
        binding.recentActivitySection.visibility =
            if (recentSessions.isEmpty()) View.GONE else View.VISIBLE
        binding.tvRecentActivityCount.text = "${recentSessions.size} gần nhất"

        val dateFormatter = SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault())
        recentSessions.forEach { session ->
            val item = ItemRecentWorkoutBinding.inflate(
                layoutInflater,
                binding.recentWorkoutContainer,
                false
            )

            item.tvRecentExerciseName.text = session.exerciseName.ifBlank { session.exerciseId }
            item.tvRecentCompletedAt.text = dateFormatter.format(session.completedAt)
            item.tvRecentResult.text = resultText(session)
            item.tvRecentDuration.text = durationText(session.durationSeconds)
            item.tvRecentFormScore.text = if (session.formScore > 0) {
                "Điểm form ${session.formScore}"
            } else {
                "Chưa có dữ liệu form"
            }

            val difficulty = difficultyDisplay(session.difficulty)
            item.tvRecentDifficulty.visibility = if (difficulty == null) View.GONE else View.VISIBLE
            difficulty?.let { (label, colorRes) ->
                item.tvRecentDifficulty.text = label
                item.tvRecentDifficulty.setTextColor(
                    ContextCompat.getColor(requireContext(), colorRes)
                )
            }

            binding.recentWorkoutContainer.addView(item.root)
        }
    }

    private fun renderExerciseProgress(sessions: List<WorkoutSession>) {
        val groupedSessions = sessions
            .filter { it.exerciseId.isNotBlank() }
            .groupBy { it.exerciseId }
            .values
            .sortedByDescending { group -> group.maxOfOrNull { it.completedAt } ?: 0L }

        binding.exerciseProgressContainer.removeAllViews()
        binding.tvTrackedExerciseCount.text = "${groupedSessions.size} bài"
        binding.tvProgressEmpty.visibility = if (groupedSessions.isEmpty()) View.VISIBLE else View.GONE

        groupedSessions.forEach { group ->
            val sorted = group.sortedByDescending { it.completedAt }
            val latest = sorted.first()
            val previous = sorted.getOrNull(1)
            val best = sorted.maxOfOrNull { it.actualCount } ?: latest.actualCount
            val item = ItemExerciseProgressBinding.inflate(
                layoutInflater,
                binding.exerciseProgressContainer,
                false
            )

            item.tvExerciseProgressName.text = latest.exerciseName.ifBlank { latest.exerciseId }
            item.tvExerciseSessionCount.text = "${sorted.size} buổi"
            item.tvExerciseLatestResult.text = getString(
                R.string.progress_latest_result,
                resultText(latest)
            )
            item.tvExerciseFormScore.text = if (latest.formScore > 0) {
                "Form ${latest.formScore}"
            } else {
                "Chưa có form"
            }
            item.progressExerciseTarget.progress = if (latest.targetCount > 0) {
                (latest.actualCount * 100 / latest.targetCount).coerceIn(0, 100)
            } else {
                0
            }
            item.tvExerciseTrend.text = trendText(latest, previous, best)

            binding.exerciseProgressContainer.addView(item.root)
        }
    }

    private fun resultText(session: WorkoutSession): String {
        return if (session.targetCount > 0) {
            "${session.actualCount}/${session.targetCount} ${session.unit}"
        } else {
            "${session.actualCount} ${session.unit}"
        }
    }

    private fun durationText(durationSeconds: Int): String {
        val safeDuration = durationSeconds.coerceAtLeast(0)
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            safeDuration / 60,
            safeDuration % 60
        )
    }

    private fun difficultyDisplay(value: String): Pair<String, Int>? {
        return when (value) {
            "TOO_EASY" -> "Hơi nhẹ" to R.color.tri_force_blue
            "JUST_RIGHT" -> "Vừa sức" to R.color.tri_force_success
            "TOO_HARD" -> "Hơi nặng" to R.color.tri_force_warning
            else -> null
        }
    }

    private fun trendText(
        latest: WorkoutSession,
        previous: WorkoutSession?,
        best: Int
    ): String {
        val comparison = if (previous == null) {
            "Buổi đầu tiên"
        } else {
            val delta = latest.actualCount - previous.actualCount
            when {
                delta > 0 -> "Tăng $delta ${latest.unit} so với buổi trước"
                delta < 0 -> "Giảm ${-delta} ${latest.unit} so với buổi trước"
                else -> "Giữ nguyên so với buổi trước"
            }
        }
        return "$comparison • Tốt nhất $best ${latest.unit}"
    }

    private fun calculateCurrentStreak(
        sessions: List<WorkoutSession>,
        todayStart: Long
    ): Int {
        if (sessions.isEmpty()) return 0

        val activeDays = sessions.map { startOfDay(it.completedAt) }.toSet()
        var cursor = todayStart
        if (cursor !in activeDays) cursor = shiftDay(cursor, -1)

        var streak = 0
        while (cursor in activeDays) {
            streak++
            cursor = shiftDay(cursor, -1)
        }
        return streak
    }

    private fun progressInsight(weeklyWorkoutCount: Int): String {
        return when {
            weeklyWorkoutCount >= 5 ->
                "Nhịp tập tuần này rất tốt. Hãy ưu tiên phục hồi để giữ phong độ ổn định."
            weeklyWorkoutCount >= 3 ->
                "Bạn đang duy trì nhịp tập tốt. Thêm một buổi chất lượng để củng cố chuỗi tiến bộ."
            weeklyWorkoutCount >= 1 ->
                "Bạn đã bắt đầu tuần này. Một lịch tập đều sẽ hiệu quả hơn những buổi tập dồn."
            else ->
                "Hoàn thành một bài tập bằng AI Camera để bắt đầu theo dõi tiến độ."
        }
    }

    private fun shortDayLabel(timeMillis: Long): String {
        return when (Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            .get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }
    }

    private fun startOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun shiftDay(timeMillis: Long, amount: Int): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            add(Calendar.DATE, amount)
        }.timeInMillis
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class Achievement(
        val title: String,
        val description: String,
        val unlocked: Boolean
    )

    companion object {
        private const val PROGRESS_PREFERENCES = "tri_force_progress"
        private const val WEEKLY_GOAL_FIELD = "weeklyWorkoutGoal"
        private const val DEFAULT_WEEKLY_GOAL = 4
        private const val MIN_WEEKLY_GOAL = 2
        private const val MAX_WEEKLY_GOAL = 7
    }
}
