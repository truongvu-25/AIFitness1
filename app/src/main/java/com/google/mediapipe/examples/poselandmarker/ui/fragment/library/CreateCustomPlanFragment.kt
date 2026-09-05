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
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCatalog
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCategory
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

    private val weeklySchedule by lazy {
        mutableListOf(
            DaySchedule(getString(R.string.weekday_monday), "mon"),
            DaySchedule(getString(R.string.weekday_tuesday), "tue"),
            DaySchedule(getString(R.string.weekday_wednesday), "wed"),
            DaySchedule(getString(R.string.weekday_thursday), "thu"),
            DaySchedule(getString(R.string.weekday_friday), "fri"),
            DaySchedule(getString(R.string.weekday_saturday), "sat"),
            DaySchedule(getString(R.string.weekday_sunday), "sun")
        )
    }
    private lateinit var weeklyAdapter: WeeklyScheduleAdapter

    private var currentCategory: ExerciseCategory? = null

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
        allExercises += ExerciseCatalog.all(requireContext()).map { exercise ->
            LibraryExercise(
                id = exercise.id,
                name = exercise.name,
                target = exercise.targetText,
                desc = exercise.summary,
                category = exercise.category,
                categoryLabel = exercise.categoryLabel,
                equipment = exercise.equipment,
                targetCount = exercise.defaultTarget,
                videoUrl = exercise.videoUrl
            )
        }
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
        binding.btnPickerNoEquip.setText(R.string.tab_no_equipment)
        binding.btnPickerHome.setText(R.string.subtab_home)
        binding.btnPickerGym.setText(R.string.subtab_gym)

        binding.btnPickerNoEquip.setOnClickListener {
            currentCategory = null
            updatePickerFilter()
        }
        binding.btnPickerHome.setOnClickListener {
            currentCategory = ExerciseCategory.UPPER_CORE
            updatePickerFilter()
        }
        binding.btnPickerGym.setOnClickListener {
            currentCategory = ExerciseCategory.LOWER_CARDIO
            updatePickerFilter()
        }
    }

    private fun updatePickerFilter() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary)
        val transparentColor = Color.TRANSPARENT
        val white = ContextCompat.getColor(requireContext(), R.color.tri_force_white)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.tri_force_text_secondary)

        binding.btnPickerNoEquip.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == null) activeColor else transparentColor)
        binding.btnPickerNoEquip.setTextColor(if (currentCategory == null) white else inactiveText)

        binding.btnPickerHome.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == ExerciseCategory.UPPER_CORE) activeColor else transparentColor)
        binding.btnPickerHome.setTextColor(if (currentCategory == ExerciseCategory.UPPER_CORE) white else inactiveText)

        binding.btnPickerGym.backgroundTintList =
            ColorStateList.valueOf(if (currentCategory == ExerciseCategory.LOWER_CARDIO) activeColor else transparentColor)
        binding.btnPickerGym.setTextColor(if (currentCategory == ExerciseCategory.LOWER_CARDIO) white else inactiveText)

        val filtered = currentCategory?.let { category ->
            allExercises.filter { it.category == category }
        } ?: allExercises
        horizontalPickerAdapter.submitList(filtered)
    }

    private fun showAssignDayDialog(exercise: LibraryExercise) {
        val dayNames = weeklySchedule.map { it.dayName }.toTypedArray()
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.custom_plan_assign_title, exercise.name))
            .setItems(dayNames) { _, which ->
                val selectedDay = weeklySchedule[which]
                if (selectedDay.exercises.any { it.id == exercise.id }) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.custom_plan_already_added, dayNames[which]),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setItems
                }
                selectedDay.exercises.add(exercise)
                weeklyAdapter.notifyItemChanged(which)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.custom_plan_added, dayNames[which]),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
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
            if (it.isNullOrEmpty()) getString(R.string.custom_plan_default_name) else it
        }

        val totalAssigned = weeklySchedule.sumOf { it.exercises.size }
        if (totalAssigned == 0) {
            Toast.makeText(requireContext(), R.string.custom_plan_empty_error, Toast.LENGTH_SHORT).show()
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
                    exObj.put("category", ex.category.name)
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

            Toast.makeText(
                requireContext(),
                getString(R.string.custom_plan_saved, planName),
                Toast.LENGTH_LONG
            ).show()

            // Navigate to Home Fragment
            findNavController().navigate(R.id.home_fragment)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                getString(R.string.custom_plan_save_error, e.localizedMessage.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
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
                    binding.tvDayStatusBadge.setText(R.string.custom_plan_rest)
                    binding.tvDayStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                    binding.tvDayStatusBadge.setBackgroundColor(Color.parseColor("#2664748B"))
                    binding.tvExerciseCount.text = binding.root.resources.getQuantityString(
                        R.plurals.custom_plan_exercise_count,
                        0,
                        0
                    )
                    binding.tvEmptyDayPrompt.visibility = View.VISIBLE
                    binding.chipGroupExercises.removeAllViews()
                } else {
                    binding.tvDayStatusBadge.setText(R.string.custom_plan_scheduled)
                    binding.tvDayStatusBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.mp_color_primary_variant))
                    binding.tvDayStatusBadge.setBackgroundColor(Color.parseColor("#260066FF"))
                    binding.tvExerciseCount.text = binding.root.resources.getQuantityString(
                        R.plurals.custom_plan_exercise_count,
                        count,
                        count
                    )
                    binding.tvEmptyDayPrompt.visibility = View.GONE

                    binding.chipGroupExercises.removeAllViews()
                    day.exercises.forEachIndexed { exIndex, ex ->
                        val chip = Chip(binding.root.context).apply {
                            text = binding.root.context.getString(
                                R.string.exercise_with_target,
                                ex.name,
                                ex.target
                            )
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
