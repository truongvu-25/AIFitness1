package com.google.mediapipe.examples.poselandmarker.fragment

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentHomeBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemExerciseBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemSavedPlanCardBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnHomeCreatePlan.setOnClickListener {
            findNavController().navigate(R.id.create_custom_plan_fragment)
        }

        binding.btnGoToCurrentWorkout.setOnClickListener {
            findNavController().navigate(R.id.workout_calendar_fragment)
        }

        syncPlansFromCloudAndDisplay()
    }

    override fun onResume() {
        super.onResume()
        syncPlansFromCloudAndDisplay()
    }

    private fun syncPlansFromCloudAndDisplay() {
        // 1. First display local cache immediately
        loadActiveWeeklyPlan()

        // 2. Fetch latest from Firebase Firestore if user is authenticated
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("custom_plans").get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val plansArray = JSONArray()
                    for (doc in querySnapshot.documents) {
                        val planJsonStr = doc.getString("planJson")
                        if (!planJsonStr.isNullOrEmpty()) {
                            try {
                                plansArray.put(JSONObject(planJsonStr))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (plansArray.length() > 0) {
                        val prefs = requireContext().getSharedPreferences("tri_force_custom_weekly_plan", Context.MODE_PRIVATE)
                        prefs.edit().putString("all_saved_plans_json", plansArray.toString()).apply()
                        loadActiveWeeklyPlan()
                    }
                }
            }
            .addOnFailureListener {
                // Ignore or rely on local cache
            }
    }

    private fun loadActiveWeeklyPlan() {
        val prefs = requireContext().getSharedPreferences("tri_force_custom_weekly_plan", Context.MODE_PRIVATE)
        val allPlansStr = prefs.getString("all_saved_plans_json", "[]") ?: "[]"
        val activePlanStr = prefs.getString("active_plan_json", null)

        val plansArray = try {
            JSONArray(allPlansStr)
        } catch (e: Exception) {
            JSONArray()
        }

        if (plansArray.length() == 0 && activePlanStr.isNullOrEmpty()) {
            // Case 1: Chưa có tiến trình nào được lưu
            binding.cardActivePlan.visibility = View.GONE
            binding.tvSavedPlansHeader.visibility = View.GONE
            binding.layoutSavedPlansList.visibility = View.GONE
            binding.tvTodayTitle.visibility = View.GONE
            binding.layoutTodayExercises.removeAllViews()

            binding.cardHomeEmptyPrompt.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "Chưa có tiến trình nào được lưu"
            binding.tvEmptySubtitle.text = "Hãy vào Thư viện và bấm 'Tự tạo lịch tập' để thiết lập tiến trình tập luyện riêng cho bạn."
            binding.btnHomeCreatePlan.visibility = View.VISIBLE
            return
        }

        // Case 2: Đã có tiến trình được lưu
        binding.cardHomeEmptyPrompt.visibility = View.GONE

        // Parse Active Plan
        val activeRootJson = if (!activePlanStr.isNullOrEmpty()) {
            try {
                JSONObject(activePlanStr)
            } catch (e: Exception) {
                if (plansArray.length() > 0) plansArray.getJSONObject(0) else null
            }
        } else if (plansArray.length() > 0) {
            plansArray.getJSONObject(0)
        } else {
            null
        }

        if (activeRootJson != null) {
            renderActivePlanSection(activeRootJson)
        }

        // Render List of Saved Plans
        val activeName = activeRootJson?.optString("planName", "") ?: ""
        renderSavedPlansList(plansArray, activeName)
    }

    private fun renderActivePlanSection(activeJson: JSONObject) {
        binding.cardActivePlan.visibility = View.VISIBLE
        binding.tvTodayTitle.visibility = View.VISIBLE

        val planName = activeJson.optString("planName", "Lịch tập cá nhân")
        val daysArray = activeJson.optJSONArray("days") ?: return

        binding.tvHomePlanName.text = planName

        // Map Calendar.DAY_OF_WEEK (Mon=0..Sun=6)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentDayIndex = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val bubbles = listOf(
            binding.bubbleMon,
            binding.bubbleTue,
            binding.bubbleWed,
            binding.bubbleThu,
            binding.bubbleFri,
            binding.bubbleSat,
            binding.bubbleSun
        )

        val dayNames = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
        val todayExercises = mutableListOf<LibraryExercise>()

        for (i in 0 until 7) {
            val bubble = bubbles[i]
            val dayObj = if (i < daysArray.length()) daysArray.getJSONObject(i) else null
            val exArray = dayObj?.optJSONArray("exercises")
            val exCount = exArray?.length() ?: 0

            if (i == currentDayIndex) {
                // Today -> Solid Blue Highlight
                bubble.setBackgroundResource(R.drawable.bg_bmi_badge)
                bubble.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.mp_color_primary)
                bubble.setTextColor(Color.WHITE)

                if (exArray != null) {
                    for (j in 0 until exCount) {
                        val ex = exArray.getJSONObject(j)
                        todayExercises.add(
                            LibraryExercise(
                                id = ex.optString("id", "ex_$j"),
                                name = ex.optString("name", "Bài tập"),
                                target = ex.optString("target", "15 lần"),
                                desc = ex.optString("desc", ""),
                                category = ex.optString("category", ""),
                                equipment = ex.optString("equipment", ""),
                                targetCount = ex.optInt("targetCount", 15)
                            )
                        )
                    }
                }
            } else if (exCount > 0) {
                // Other day with exercises -> Frosted Dark Glass
                bubble.setBackgroundResource(R.drawable.bg_bmi_badge)
                bubble.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tri_force_navy)
                bubble.setTextColor(ContextCompat.getColor(requireContext(), R.color.mp_color_primary_variant))
            } else {
                // Rest day -> Faint Slate
                bubble.setBackgroundResource(R.drawable.bg_bmi_badge)
                bubble.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tri_force_navy)
                bubble.setTextColor(Color.parseColor("#64748B"))
            }
        }

        // Render today's exercises
        val todayName = dayNames[currentDayIndex]
        binding.tvTodayTitle.text = "BÀI TẬP HÔM NAY ($todayName)"
        binding.layoutTodayExercises.removeAllViews()

        if (todayExercises.isEmpty()) {
            val emptyDayBinding = ItemExerciseBinding.inflate(layoutInflater, binding.layoutTodayExercises, false)
            emptyDayBinding.tvExerciseName.text = "Hôm nay là ngày nghỉ ngơi"
            emptyDayBinding.tvExerciseTarget.text = "Thư giãn & Phục hồi cơ bắp"
            emptyDayBinding.tvExerciseDesc.text = "Ăn uống đủ chất đạm và ngủ đủ 8 tiếng để chuẩn bị cho buổi tập kế tiếp."
            emptyDayBinding.btnWatchVideo.visibility = View.GONE
            emptyDayBinding.btnStartExercise.visibility = View.GONE
            binding.layoutTodayExercises.addView(emptyDayBinding.root)
        } else {
            for (ex in todayExercises) {
                val itemBinding = ItemExerciseBinding.inflate(layoutInflater, binding.layoutTodayExercises, false)
                itemBinding.tvExerciseName.text = ex.name
                itemBinding.tvExerciseTarget.text = "Mục tiêu: ${ex.target}"
                itemBinding.tvExerciseDesc.text = ex.desc

                itemBinding.btnWatchVideo.setOnClickListener {
                    Toast.makeText(requireContext(), "Hướng dẫn bài tập: ${ex.name}", Toast.LENGTH_SHORT).show()
                }

                itemBinding.btnStartExercise.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("exerciseId", ex.id)
                        putString("exerciseName", ex.name)
                        putInt("targetCount", ex.targetCount)
                        putInt("dayIndex", currentDayIndex + 1)
                    }
                    findNavController().navigate(R.id.action_home_to_camera, bundle)
                }

                binding.layoutTodayExercises.addView(itemBinding.root)
            }
        }
    }

    private fun renderSavedPlansList(plansArray: JSONArray, currentActivePlanName: String) {
        if (plansArray.length() == 0) {
            binding.tvSavedPlansHeader.visibility = View.GONE
            binding.layoutSavedPlansList.visibility = View.GONE
            return
        }

        binding.tvSavedPlansHeader.visibility = View.VISIBLE
        binding.layoutSavedPlansList.visibility = View.VISIBLE
        binding.layoutSavedPlansList.removeAllViews()

        for (i in 0 until plansArray.length()) {
            val planObj = plansArray.getJSONObject(i)
            val planName = planObj.optString("planName", "Lịch tập ${i + 1}")
            val days = planObj.optJSONArray("days")
            val workoutDaysCount = if (days != null) {
                var c = 0
                for (d in 0 until days.length()) {
                    if (days.getJSONObject(d).optJSONArray("exercises")?.length() ?: 0 > 0) c++
                }
                c
            } else 0

            val itemBinding = ItemSavedPlanCardBinding.inflate(layoutInflater, binding.layoutSavedPlansList, false)
            itemBinding.tvSavedPlanName.text = planName
            itemBinding.tvSavedPlanDesc.text = "$workoutDaysCount buổi tập/tuần • Lặp lại hàng tuần"

            val isActive = (planName == currentActivePlanName)
            if (isActive) {
                itemBinding.tvActiveIndicator.visibility = View.VISIBLE
                itemBinding.btnStartSavedPlan.text = "ĐANG TẬP"
                itemBinding.btnStartSavedPlan.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tri_force_success)
                itemBinding.btnStartSavedPlan.isEnabled = false
            } else {
                itemBinding.tvActiveIndicator.visibility = View.GONE
                itemBinding.btnStartSavedPlan.text = "BẮT ĐẦU"
                itemBinding.btnStartSavedPlan.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.mp_color_primary)
                itemBinding.btnStartSavedPlan.isEnabled = true

                itemBinding.btnStartSavedPlan.setOnClickListener {
                    showSwitchPlanConfirmationDialog(planObj, planName)
                }
            }

            binding.layoutSavedPlansList.addView(itemBinding.root)
        }
    }

    private fun showSwitchPlanConfirmationDialog(newPlanObj: JSONObject, planName: String) {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Xác nhận đổi tiến trình tập")
            .setMessage("Bạn có chắc chắn muốn hủy tiến trình hiện tại để bắt đầu tiến trình \"$planName\" này không?")
            .setPositiveButton("Bắt đầu ngay") { _, _ ->
                val prefs = requireContext().getSharedPreferences("tri_force_custom_weekly_plan", Context.MODE_PRIVATE)
                prefs.edit().putString("active_plan_json", newPlanObj.toString()).apply()

                // Update active on Firestore as well
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    db.collection("users").document(uid).update("activeCustomPlanJson", newPlanObj.toString())
                }

                Toast.makeText(requireContext(), "Đã áp dụng tiến trình \"$planName\"!", Toast.LENGTH_SHORT).show()
                loadActiveWeeklyPlan()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
