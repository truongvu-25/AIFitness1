package com.google.mediapipe.examples.poselandmarker.fragment

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.Exercise
import com.google.mediapipe.examples.poselandmarker.ExerciseDetails
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.UserExercise
import com.google.mediapipe.examples.poselandmarker.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentWorkoutCalendarBinding

class WorkoutCalendarFragment : Fragment() {

    private var _binding: FragmentWorkoutCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var uid: String = ""

    private lateinit var daysAdapter: WorkoutDaysAdapter
    private lateinit var exercisesAdapter: ExercisesAdapter
    
    private var selectedDayIndex: Int = 1
    private var pendingExercise: Exercise? = null

    // Local memory cache for static exercise details (loaded once on startup)
    private val exercisesCache = HashMap<String, ExerciseDetails>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingExercise?.let { openWorkoutCamera(it) }
        } else {
            Toast.makeText(context, "Ứng dụng cần quyền Camera để hiển thị khung xương tập luyện AI.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        uid = auth.currentUser?.uid ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        loadMasterExercisesAndInit()

        binding.btnResetPlan.setOnClickListener {
            showResetPlanDialog()
        }
    }

    private fun setupRecyclerViews() {
        daysAdapter = WorkoutDaysAdapter(emptyList(), 0) { dayNum ->
            selectedDayIndex = dayNum
            binding.tvSelectedDay.text = "Bài tập Ngày $dayNum"
            loadExercisesForDay(dayNum)
        }
        binding.rvDays.adapter = daysAdapter

        exercisesAdapter = ExercisesAdapter(
            emptyList(),
            onStartClicked = { exercise ->
                checkCameraPermissionAndStart(exercise)
            },
            onWatchVideoClicked = { exercise ->
                showVideoTutorialDialog(exercise)
            }
        )
        binding.rvExercises.adapter = exercisesAdapter
    }

    // Loads the global 7 exercises list into memory cache, then proceeds to build the schedule
    private fun loadMasterExercisesAndInit() {
        binding.calendarProgress.visibility = View.VISIBLE
        db.collection("exercises").get()
            .addOnSuccessListener { result ->
                exercisesCache.clear()
                for (document in result) {
                    val details = document.toObject(ExerciseDetails::class.java)
                    exercisesCache[details.id] = details
                }
                // Once caching is complete, load the user's customized schedule
                loadUserProfileAndPlan()
            }
            .addOnFailureListener { e ->
                binding.calendarProgress.visibility = View.GONE
                Toast.makeText(context, "Lỗi tải kho bài tập: ${e.message}", Toast.LENGTH_SHORT).show()
                // Default fallback load
                loadUserProfileAndPlan()
            }
    }

    private fun loadUserProfileAndPlan() {
        if (uid.isEmpty()) return
        
        binding.calendarProgress.visibility = View.VISIBLE
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        // 1. Check weekly forced BMI update status (7 days)
                        val lastBmiUpdated = profile.lastBmiUpdatedTime
                        val diffMs = System.currentTimeMillis() - lastBmiUpdated
                        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                        if (lastBmiUpdated > 0 && diffMs >= sevenDaysMs) {
                            binding.calendarProgress.visibility = View.GONE
                            findNavController().navigate(R.id.action_workout_calendar_to_update_bmi)
                            return@addOnSuccessListener
                        }

                        // 2. Load plan display
                        val planTypeLabel = when (profile.bmiType) {
                            "GAY" -> "Lộ trình: Tăng Cân & Tăng Cơ (Gầy)"
                            "CAN DOI" -> "Lộ trình: Săn Chắc Thể Hình (Cân đối)"
                            else -> "Lộ trình: Đốt Mỡ & Giảm Cân (Thừa cân)"
                        }
                        binding.tvPlanType.text = planTypeLabel

                        val diffPlanMs = System.currentTimeMillis() - profile.createdTime
                        val currentDayIndex = (diffPlanMs / (24 * 60 * 60 * 1000)).toInt() + 1
                        val defaultSelectedDay = currentDayIndex.coerceIn(1, 30)
                        selectedDayIndex = defaultSelectedDay
                        
                        val dayList = (1..30).toList()
                        daysAdapter.updateData(dayList, defaultSelectedDay - 1)
                        
                        binding.rvDays.post {
                            binding.rvDays.scrollToPosition((defaultSelectedDay - 1).coerceAtLeast(0))
                        }

                        binding.tvSelectedDay.text = "Bài tập Ngày $selectedDayIndex"
                        loadExercisesForDay(selectedDayIndex)
                    }
                } else {
                    binding.calendarProgress.visibility = View.GONE
                    Toast.makeText(context, "Không tìm thấy hồ sơ người dùng.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.calendarProgress.visibility = View.GONE
                Toast.makeText(context, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadExercisesForDay(dayNum: Int) {
        if (uid.isEmpty()) return
        binding.calendarProgress.visibility = View.VISIBLE
        
        db.collection("users").document(uid)
            .collection("workouts").document("day_$dayNum").get()
            .addOnSuccessListener { document ->
                binding.calendarProgress.visibility = View.GONE
                if (document.exists()) {
                    val workoutDay = document.toObject(WorkoutDay::class.java)
                    val userExercises = workoutDay?.exercises ?: emptyList()
                    
                    // Map relational UserExercise to Display Helper Exercise using the master cache
                    val displayExercises = userExercises.map { userEx ->
                        val details = exercisesCache[userEx.exerciseId]
                        Exercise(
                            id = userEx.exerciseId,
                            name = details?.name ?: userEx.exerciseId,
                            targetCount = userEx.targetCount,
                            status = userEx.status,
                            description = details?.description ?: "",
                            videoUrl = details?.videoUrl ?: ""
                        )
                    }
                    exercisesAdapter.updateData(displayExercises)
                } else {
                    exercisesAdapter.updateData(emptyList())
                }
            }
            .addOnFailureListener { e ->
                binding.calendarProgress.visibility = View.GONE
                Toast.makeText(context, "Lỗi tải bài tập: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResetPlanDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tạo lộ trình mới")
            .setMessage("Bạn có chắc chắn muốn xóa lộ trình hiện tại và tạo lộ trình 30 ngày mới dựa trên thông số cơ thể hiện tại không?")
            .setPositiveButton("Đồng ý") { dialog, _ ->
                dialog.dismiss()
                resetWorkoutPlan()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun resetWorkoutPlan() {
        if (uid.isEmpty()) return
        binding.calendarProgress.visibility = View.VISIBLE

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        val batch = db.batch()
                        // Regenerate 30 days exercises with relational UserExercise objects
                        for (day in 1..30) {
                            val exercises = getExercisesForBmiAndDay(profile.bmiType, day)
                            val workoutDay = WorkoutDay(dayIndex = day, exercises = exercises)
                            val docRef = db.collection("users").document(uid)
                                .collection("workouts").document("day_$day")
                            batch.set(docRef, workoutDay)
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                val updatedProfile = profile.copy(createdTime = System.currentTimeMillis())
                                db.collection("users").document(uid).set(updatedProfile)
                                    .addOnSuccessListener {
                                        binding.calendarProgress.visibility = View.GONE
                                        Toast.makeText(context, "Đã tạo lộ trình 30 ngày tập luyện mới!", Toast.LENGTH_SHORT).show()
                                        loadUserProfileAndPlan() // Refresh UI
                                    }
                            }
                            .addOnFailureListener { e ->
                                binding.calendarProgress.visibility = View.GONE
                                Toast.makeText(context, "Lỗi tạo lại lộ trình: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.calendarProgress.visibility = View.GONE
                Toast.makeText(context, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getExercisesForBmiAndDay(bmiType: String, dayIndex: Int): List<UserExercise> {
        val weekMultiplier = when {
            dayIndex <= 7 -> 1.0
            dayIndex <= 14 -> 1.2
            dayIndex <= 21 -> 1.4
            else -> 1.6
        }

        return when (bmiType) {
            "GAY" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", (10 * weekMultiplier).toInt()),
                        UserExercise("squat", (12 * weekMultiplier).toInt()),
                        UserExercise("plank", (2 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", (10 * weekMultiplier).toInt()),
                        UserExercise("situp", (12 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (2 * weekMultiplier).toInt())
                    )
                }
            }
            "CAN DOI" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", (15 * weekMultiplier).toInt()),
                        UserExercise("squat", (15 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt()),
                        UserExercise("plank", (3 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", (12 * weekMultiplier).toInt()),
                        UserExercise("situp", (15 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (3 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt())
                    )
                }
            }
            else -> { // THUA CAN
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("jumpingjack", (30 * weekMultiplier).toInt()),
                        UserExercise("squat", (20 * weekMultiplier).toInt()),
                        UserExercise("situp", (20 * weekMultiplier).toInt()),
                        UserExercise("plank", (3 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("jumpingjack", (30 * weekMultiplier).toInt()),
                        UserExercise("splitsquat", (15 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (3 * weekMultiplier).toInt()),
                        UserExercise("pushup", (12 * weekMultiplier).toInt())
                    )
                }
            }
        }
    }

    private fun showVideoTutorialDialog(exercise: Exercise) {
        val url = exercise.videoUrl
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể mở ứng dụng YouTube.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_video_player)
        
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val videoView = dialog.findViewById<VideoView>(R.id.dialogVideoView)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.videoProgress)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnDialogClose)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)

        tvTitle.text = "Hướng dẫn: ${exercise.name}"

        try {
            val videoUri = Uri.parse(url)
            videoView.setVideoURI(videoUri)
            videoView.setOnPreparedListener { mp ->
                progressBar.visibility = View.GONE
                videoView.start()
                mp.isLooping = true
            }
            videoView.setOnErrorListener { _, _, _ ->
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Không thể tải video hướng dẫn.", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(context, "Lỗi phát video: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            videoView.stopPlayback()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndStart(exercise: Exercise) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openWorkoutCamera(exercise)
        } else {
            pendingExercise = exercise
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openWorkoutCamera(exercise: Exercise) {
        val bundle = Bundle().apply {
            putString("exerciseId", exercise.id)
            putString("exerciseName", exercise.name)
            putInt("targetCount", exercise.targetCount)
            putInt("dayIndex", selectedDayIndex)
        }
        findNavController().navigate(R.id.action_workout_calendar_to_camera, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- HORIZONTAL RECYCLERVIEW ADAPTER FOR DAYS ---
    private class WorkoutDaysAdapter(
        private var days: List<Int>,
        private var selectedPosition: Int,
        private val onDaySelected: (Int) -> Unit
    ) : RecyclerView.Adapter<WorkoutDaysAdapter.DayViewHolder>() {

        fun updateData(newDays: List<Int>, defaultSelectedIdx: Int) {
            this.days = newDays
            this.selectedPosition = defaultSelectedIdx
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val dayNum = days[position]
            holder.bind(dayNum, position == selectedPosition)
            
            holder.itemView.setOnClickListener {
                if (position != selectedPosition) {
                    val oldPos = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)
                    onDaySelected(dayNum)
                }
            }
        }

        override fun getItemCount(): Int = days.size

        class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardDay: MaterialCardView = itemView.findViewById(R.id.cardDay)
            private val tvDayNum: TextView = itemView.findViewById(R.id.tvDayNum)
            private val tvDayLabel: TextView = itemView.findViewById(R.id.tvDayLabel)

            fun bind(dayNum: Int, isSelected: Boolean) {
                tvDayNum.text = String.format("%02d", dayNum)
                if (isSelected) {
                    cardDay.setCardBackgroundColor(Color.parseColor("#007F8B"))
                    tvDayNum.setTextColor(Color.WHITE)
                    tvDayLabel.setTextColor(Color.parseColor("#B2DFDB"))
                    cardDay.strokeColor = Color.parseColor("#007F8B")
                } else {
                    cardDay.setCardBackgroundColor(Color.WHITE)
                    tvDayNum.setTextColor(Color.parseColor("#007F8B"))
                    tvDayLabel.setTextColor(Color.parseColor("#757575"))
                    cardDay.strokeColor = Color.parseColor("#E0E0E0")
                }
            }
        }
    }

    // --- VERTICAL RECYCLERVIEW ADAPTER FOR EXERCISES ---
    private class ExercisesAdapter(
        private var exercises: List<Exercise>,
        private val onStartClicked: (Exercise) -> Unit,
        private val onWatchVideoClicked: (Exercise) -> Unit
    ) : RecyclerView.Adapter<ExercisesAdapter.ExerciseViewHolder>() {

        fun updateData(newExercises: List<Exercise>) {
            this.exercises = newExercises
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise, parent, false)
            return ExerciseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
            holder.bind(exercises[position], onStartClicked, onWatchVideoClicked)
        }

        override fun getItemCount(): Int = exercises.size

        class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvExerciseName)
            private val tvTarget: TextView = itemView.findViewById(R.id.tvExerciseTarget)
            private val tvDesc: TextView = itemView.findViewById(R.id.tvExerciseDesc)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val btnWatchVideo: Button = itemView.findViewById(R.id.btnWatchVideo)
            private val btnStart: Button = itemView.findViewById(R.id.btnStartExercise)

            fun bind(
                exercise: Exercise,
                onStartClicked: (Exercise) -> Unit,
                onWatchVideoClicked: (Exercise) -> Unit
            ) {
                tvName.text = exercise.name
                tvTarget.text = "Mục tiêu: ${exercise.targetCount} lần"
                tvDesc.text = exercise.description

                if (exercise.status == 1) { // Completed
                    tvStatus.visibility = View.VISIBLE
                    btnStart.text = "TẬP LẠI"
                    btnStart.setBackgroundColor(Color.parseColor("#757575"))
                } else { // Pending
                    tvStatus.visibility = View.GONE
                    btnStart.text = "BẮT ĐẦU"
                    btnStart.setBackgroundColor(Color.parseColor("#007F8B"))
                }

                btnWatchVideo.setOnClickListener {
                    onWatchVideoClicked(exercise)
                }

                btnStart.setOnClickListener {
                    onStartClicked(exercise)
                }
            }
        }
    }
}
