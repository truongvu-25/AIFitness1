package com.google.mediapipe.examples.poselandmarker.ui.fragment.library

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCreateCustomPlanBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemDayScheduleCardBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemHorizontalPickerExerciseBinding
import org.json.JSONArray
import org.json.JSONObject

data class DaySchedule(
    val dayName: String,
    val dayKey: String, // "mon", "tue", "wed", "thu", "fri", "sat", "sun"
    val exercises: MutableList<LibraryExercise> = mutableListOf()
)

class CreateCustomPlanFragment : Fragment() {

    private var _binding: FragmentCreateCustomPlanBinding? = null
    private val binding get() = _binding!!

    private val allExercises = mutableListOf<LibraryExercise>()
    private val horizontalPickerAdapter = HorizontalPickerAdapter { exercise ->
        showAssignDayDialog(exercise)
    }

    private val weeklySchedule = mutableListOf(
        DaySchedule("Thứ Hai", "mon"),
        DaySchedule("Thứ Ba", "tue"),
        DaySchedule("Thứ Tư", "wed"),
        DaySchedule("Thứ Năm", "thu"),
        DaySchedule("Thứ Sáu", "fri"),
        DaySchedule("Thứ Bảy", "sat"),
        DaySchedule("Chủ Nhật", "sun")
    )
    private lateinit var weeklyAdapter: WeeklyScheduleAdapter

    private var currentCategory = "Không dụng cụ"

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateCustomPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initExerciseDatabase()
        setupUpperPicker()
        setupWeeklyRecyclerView()
        setupFilterButtons()
        setupActionButtons()
        updatePickerFilter()
    }

    private fun initExerciseDatabase() {
        allExercises.clear()
        // Không dụng cụ
        allExercises.add(LibraryExercise("pushup", "Hít Đất (Push-up)", "15 lần", "Ngực & vai", "Không dụng cụ", "Bodyweight", 15))
        allExercises.add(LibraryExercise("situp", "Gập Bụng (Sit-up)", "20 lần", "Cơ bụng", "Không dụng cụ", "Bodyweight", 20))
        allExercises.add(LibraryExercise("squat", "Ngồi Xổm (Squats)", "20 lần", "Cơ đùi & mông", "Không dụng cụ", "Bodyweight", 20))
        allExercises.add(LibraryExercise("plank", "Plank Căng Cơ", "45 giây", "Cơ lõi Core", "Không dụng cụ", "Bodyweight", 45))
        allExercises.add(LibraryExercise("sideplank", "Plank Nghiêng (Side Plank)", "30 giây", "Cơ liên sườn & eo", "Không dụng cụ", "Bodyweight", 30))
        allExercises.add(LibraryExercise("jumping_jacks", "Jumping Jacks", "30 lần", "Cardio đốt mỡ", "Không dụng cụ", "Bodyweight", 30))
        allExercises.add(LibraryExercise("lunges", "Lunge Chùng Chân", "15 lần", "Đùi & khớp gối", "Không dụng cụ", "Bodyweight", 15))
        allExercises.add(LibraryExercise("mountain_climber", "Leo Núi (Mountain Climbers)", "30 giây", "Bụng dưới", "Không dụng cụ", "Bodyweight", 30))

        // Tại nhà
        allExercises.add(LibraryExercise("db_curl", "Cuốn Tạ Tay (Dumbbell Curl)", "12 lần", "Bắp tay trước", "Tại nhà", "Tạ đơn", 12))
        allExercises.add(LibraryExercise("db_shoulder_press", "Đẩy Vai Tạ Đơn (Shoulder Press)", "12 lần", "Cơ vai", "Tại nhà", "Tạ đơn", 12))
        allExercises.add(LibraryExercise("goblet_squat", "Goblet Squat (Squat Ôm Tạ)", "15 lần", "Đùi & mông", "Tại nhà", "Tạ đơn", 15))
        allExercises.add(LibraryExercise("tricep_dips", "Tricep Dips (Ghế Tựa)", "15 lần", "Tay sau", "Tại nhà", "Ghế tập", 15))
        allExercises.add(LibraryExercise("band_lateral_walk", "Bước Ngang Dây Kháng Lực", "20 bước", "Cơ mông nhỡ", "Tại nhà", "Miniband", 20))
        allExercises.add(LibraryExercise("db_rdl", "Dumbbell Romanian Deadlift", "12 lần", "Đùi sau", "Tại nhà", "Tạ đơn", 12))

        // Phòng gym
        allExercises.add(LibraryExercise("barbell_bench_press", "Nằm Đẩy Tạ Đòn (Bench Press)", "10 lần", "Cơ ngực dày", "Phòng gym", "Tạ đòn", 10))
        allExercises.add(LibraryExercise("lat_pulldown", "Kéo Cáp Xô Lưng (Lat Pulldown)", "12 lần", "Lưng xô V-taper", "Phòng gym", "Máy kéo cáp", 12))
        allExercises.add(LibraryExercise("barbell_squat", "Gánh Tạ Đòn (Barbell Squat)", "10 lần", "Đùi trước & sau", "Phòng gym", "Khung gánh", 10))
        allExercises.add(LibraryExercise("cable_tricep_pushdown", "Kéo Cáp Tay Sau (Pushdown)", "12 lần", "Tay sau sắc nét", "Phòng gym", "Cáp thừng", 12))
        allExercises.add(LibraryExercise("leg_press", "Đạp Đùi Máy Nghiêng (Leg Press)", "12 lần", "Đùi nặng", "Phòng gym", "Máy Leg Press", 12))
        allExercises.add(LibraryExercise("seated_cable_row", "Kéo Cáp Ngồi (Seated Row)", "12 lần", "Lưng giữa", "Phòng gym", "Máy Row", 12))
    }

    private fun setupUpperPicker() {
        binding.rvHorizontalExercises.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHorizontalExercises.adapter = horizontalPickerAdapter
    }

    private fun setupWeeklyRecyclerView() {
        weeklyAdapter = WeeklyScheduleAdapter(weeklySchedule) { dayIndex, exerciseIndex ->
            weeklySchedule[dayIndex].exercises.removeAt(exerciseIndex)
            weeklyAdapter.notifyItemChanged(dayIndex)
        }
        binding.rvWeeklyDays.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWeeklyDays.adapter = weeklyAdapter
    }

    private fun setupFilterButtons() {
        binding.btnPickerNoEquip.setOnClickListener {
            currentCategory = "Không dụng cụ"
            updatePickerFilter()
        }
        binding.btnPickerHome.setOnClickListener {
            currentCategory = "Tại nhà"
            updatePickerFilter()
        }
        binding.btnPickerGym.setOnClickListener {
            currentCategory = "Phòng gym"
            updatePickerFilter()
        }
    }

    private fun updatePickerFilter() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary)
        val transparentColor = Color.TRANSPARENT
        val white = ContextCompat.getColor(requireContext(), R.color.tri_force_white)
        val silver = ContextCompat.getColor(requireContext(), R.color.tri_force_silver)

        binding.btnPickerNoEquip.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == "Không dụng cụ") activeColor else transparentColor)
        binding.btnPickerNoEquip.setTextColor(if (currentCategory == "Không dụng cụ") white else silver)

        binding.btnPickerHome.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == "Tại nhà") activeColor else transparentColor)
        binding.btnPickerHome.setTextColor(if (currentCategory == "Tại nhà") white else silver)

        binding.btnPickerGym.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == "Phòng gym") activeColor else transparentColor)
        binding.btnPickerGym.setTextColor(if (currentCategory == "Phòng gym") white else silver)

        val filtered = allExercises.filter { it.category == currentCategory }
        horizontalPickerAdapter.submitList(filtered)
    }

    private fun showAssignDayDialog(exercise: LibraryExercise) {
        val dayNames = weeklySchedule.map { it.dayName }.toTypedArray()
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Gán \"${exercise.name}\" vào ngày nào?")
            .setItems(dayNames) { _, which ->
                weeklySchedule[which].exercises.add(exercise)
                weeklyAdapter.notifyItemChanged(which)
                Toast.makeText(
                    requireContext(),
                    "Đã thêm vào ${dayNames[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun setupActionButtons() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCancelPlan.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSavePlan.setOnClickListener {
            savePlanAndNavigateHome()
        }
    }

    private fun savePlanAndNavigateHome() {
        val planName = binding.etPlanName.text?.toString()?.trim().let {
            if (it.isNullOrEmpty()) "Lịch tập tùy chỉnh hàng tuần" else it
        }

        val totalAssigned = weeklySchedule.sumOf { it.exercises.size }
        if (totalAssigned == 0) {
            Toast.makeText(requireContext(), "Vui lòng gán ít nhất 1 bài tập vào lịch tuần!", Toast.LENGTH_SHORT).show()
            return
        }

        // Serialize to JSON and persist
        try {
            val planId = "plan_${System.currentTimeMillis()}"
            val rootJson = JSONObject()
            rootJson.put("planId", planId)
            rootJson.put("planName", planName)
            rootJson.put("createdAt", System.currentTimeMillis())

            val daysArray = JSONArray()
            for (day in weeklySchedule) {
                val dayObj = JSONObject()
                dayObj.put("dayName", day.dayName)
                dayObj.put("dayKey", day.dayKey)

                val exercisesArray = JSONArray()
                for (ex in day.exercises) {
                    val exObj = JSONObject()
                    exObj.put("id", ex.id)
                    exObj.put("name", ex.name)
                    exObj.put("target", ex.target)
                    exObj.put("desc", ex.desc)
                    exObj.put("category", ex.category)
                    exObj.put("equipment", ex.equipment)
                    exObj.put("targetCount", ex.targetCount)
                    exercisesArray.put(exObj)
                }
                dayObj.put("exercises", exercisesArray)
                daysArray.put(dayObj)
            }
            rootJson.put("days", daysArray)

            // 1. Local persistence as saved template
            val prefs = requireContext().getSharedPreferences("tri_force_custom_weekly_plan", Context.MODE_PRIVATE)
            val existingPlansStr = prefs.getString("all_saved_plans_json", "[]") ?: "[]"
            val plansArray = JSONArray(existingPlansStr)
            plansArray.put(rootJson)

            prefs.edit()
                .putString("all_saved_plans_json", plansArray.toString())
                .apply()

            // 2. Cloud persistence to Firebase Firestore per user
            val uid = auth.currentUser?.uid
            if (uid != null) {
                val planMap = hashMapOf(
                    "planId" to planId,
                    "planName" to planName,
                    "planJson" to rootJson.toString(),
                    "createdAt" to System.currentTimeMillis()
                )

                db.collection("users").document(uid).collection("custom_plans").document(planId)
                    .set(planMap)
                    .addOnSuccessListener {
                        // Synced successfully
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
            }

            Toast.makeText(requireContext(), "Đã lưu tiến trình mẫu \"$planName\"!", Toast.LENGTH_LONG).show()

            // Navigate to Home Fragment
            findNavController().navigate(R.id.home_fragment)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Lỗi lưu lịch tập: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Top Horizontal Exercise Picker Adapter
    class HorizontalPickerAdapter(
        private val onAddClick: (LibraryExercise) -> Unit
    ) : RecyclerView.Adapter<HorizontalPickerAdapter.ViewHolder>() {

        private val items = mutableListOf<LibraryExercise>()

        fun submitList(newItems: List<LibraryExercise>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHorizontalPickerExerciseBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemHorizontalPickerExerciseBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LibraryExercise) {
                binding.tvPickerBadge.text = item.equipment
                binding.tvPickerName.text = item.name
                binding.tvPickerTarget.text = item.target

                binding.btnAddToDay.setOnClickListener {
                    onAddClick(item)
                }
            }
        }
    }

    // Bottom 7-Day Schedule Adapter
    class WeeklyScheduleAdapter(
        private val days: List<DaySchedule>,
        private val onDeleteExercise: (dayIndex: Int, exerciseIndex: Int) -> Unit
    ) : RecyclerView.Adapter<WeeklyScheduleAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDayScheduleCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(days[position], position)
        }

        override fun getItemCount(): Int = days.size

        inner class ViewHolder(private val binding: ItemDayScheduleCardBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(day: DaySchedule, dayIndex: Int) {
                binding.tvDayTitle.text = day.dayName
                val count = day.exercises.size

                if (count == 0) {
                    binding.tvDayStatusBadge.text = "Nghỉ ngơi"
                    binding.tvDayStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                    binding.tvDayStatusBadge.setBackgroundColor(Color.parseColor("#2664748B"))
                    binding.tvExerciseCount.text = "0 bài tập"
                    binding.tvEmptyDayPrompt.visibility = View.VISIBLE
                    binding.chipGroupExercises.removeAllViews()
                } else {
                    binding.tvDayStatusBadge.text = "Có lịch tập"
                    binding.tvDayStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.mp_color_primary_variant))
                    binding.tvDayStatusBadge.setBackgroundColor(Color.parseColor("#260066FF"))
                    binding.tvExerciseCount.text = "$count bài tập"
                    binding.tvEmptyDayPrompt.visibility = View.GONE

                    binding.chipGroupExercises.removeAllViews()
                    day.exercises.forEachIndexed { exIndex, ex ->
                        val chip = Chip(binding.root.context).apply {
                            text = "${ex.name} (${ex.target})"
                            isCloseIconVisible = true
                            setChipBackgroundColorResource(R.color.tri_force_navy)
                            setTextColor(Color.WHITE)
                            setCloseIconTintResource(R.color.tri_force_error)
                            setOnCloseIconClickListener {
                                onDeleteExercise(dayIndex, exIndex)
                            }
                        }
                        binding.chipGroupExercises.addView(chip)
                    }
                }
            }
        }
    }
}
