package com.google.mediapipe.examples.poselandmarker.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.Exercise
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
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

    // Register permission launcher directly
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
        loadUserProfileAndPlan()
    }

    private fun setupRecyclerViews() {
        // Init days selector adapter
        daysAdapter = WorkoutDaysAdapter(emptyList(), 0) { dayNum ->
            selectedDayIndex = dayNum
            binding.tvSelectedDay.text = "Bài tập Ngày $dayNum"
            loadExercisesForDay(dayNum)
        }
        binding.rvDays.adapter = daysAdapter

        // Init exercises list adapter
        exercisesAdapter = ExercisesAdapter(emptyList()) { exercise ->
            checkCameraPermissionAndStart(exercise)
        }
        binding.rvExercises.adapter = exercisesAdapter
    }

    private fun loadUserProfileAndPlan() {
        if (uid.isEmpty()) return
        
        binding.calendarProgress.visibility = View.VISIBLE
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        val planTypeLabel = when (profile.bmiType) {
                            "GAY" -> "Lộ trình: Tăng Cân & Tăng Cơ (Gầy)"
                            "CAN DOI" -> "Lộ trình: Săn Chắc Thể Hình (Cân đối)"
                            else -> "Lộ trình: Đốt Mỡ & Giảm Cân (Thừa cân)"
                        }
                        binding.tvPlanType.text = planTypeLabel

                        // Calculate current workout day index based on start time
                        val diffMs = System.currentTimeMillis() - profile.createdTime
                        val currentDayIndex = (diffMs / (24 * 60 * 60 * 1000)).toInt() + 1
                        
                        // Default selection falls to current day (clamp between 1 and 30)
                        val defaultSelectedDay = currentDayIndex.coerceIn(1, 30)
                        selectedDayIndex = defaultSelectedDay
                        
                        val dayList = (1..30).toList()
                        daysAdapter.updateData(dayList, defaultSelectedDay - 1)
                        
                        // Auto-scroll horizontal day list to selected day
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
                Toast.makeText(context, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_LONG).show()
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
                    val exercises = workoutDay?.exercises ?: emptyList()
                    exercisesAdapter.updateData(exercises)
                } else {
                    exercisesAdapter.updateData(emptyList())
                }
            }
            .addOnFailureListener { e ->
                binding.calendarProgress.visibility = View.GONE
                Toast.makeText(context, "Lỗi tải bài tập: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
        private val onStartClicked: (Exercise) -> Unit
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
            holder.bind(exercises[position], onStartClicked)
        }

        override fun getItemCount(): Int = exercises.size

        class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvExerciseName)
            private val tvTarget: TextView = itemView.findViewById(R.id.tvExerciseTarget)
            private val tvDesc: TextView = itemView.findViewById(R.id.tvExerciseDesc)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val btnStart: Button = itemView.findViewById(R.id.btnStartExercise)

            fun bind(exercise: Exercise, onStartClicked: (Exercise) -> Unit) {
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

                btnStart.setOnClickListener {
                    onStartClicked(exercise)
                }
            }
        }
    }
}
