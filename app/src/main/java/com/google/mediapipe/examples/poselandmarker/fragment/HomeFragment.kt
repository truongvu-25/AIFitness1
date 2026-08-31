package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentHomeBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemExerciseBinding
import org.json.JSONObject
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadActiveWeeklyPlan()

        binding.btnHomeCreatePlan.setOnClickListener {
            findNavController().navigate(R.id.create_custom_plan_fragment)
        }
    }

    override fun onResume() {
        super.onResume()
        loadActiveWeeklyPlan()
    }

    private fun loadActiveWeeklyPlan() {
        val prefs = requireContext().getSharedPreferences("tri_force_custom_weekly_plan", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("active_plan_json", null)

        if (jsonString.isNullOrEmpty()) {
            // No custom plan exists yet
            binding.cardActivePlan.visibility = View.GONE
            binding.tvTodayTitle.visibility = View.GONE
            binding.cardHomeEmptyPrompt.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "Chưa Có Lịch Tập Tuần"
            binding.tvEmptySubtitle.text = "Hãy vào Thư viện và bấm 'Tự tạo lịch tập' để thiết lập tiến trình tập luyện riêng cho bạn."
            binding.btnHomeCreatePlan.visibility = View.VISIBLE
            binding.layoutTodayExercises.removeAllViews()
            return
        }

        try {
            val rootJson = JSONObject(jsonString)
            val planName = rootJson.optString("planName", "Lịch tập tùy chỉnh")
            val daysArray = rootJson.optJSONArray("days") ?: return

            binding.cardActivePlan.visibility = View.VISIBLE
            binding.tvTodayTitle.visibility = View.VISIBLE
            binding.cardHomeEmptyPrompt.visibility = View.GONE
            binding.tvHomePlanName.text = planName

            // Map Calendar.DAY_OF_WEEK to 0..6 (Mon -> 0, Tue -> 1, ..., Sun -> 6)
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

            var todayExercises = mutableListOf<LibraryExercise>()

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

                    // Parse today's exercises
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
                    // Other day with exercises -> Frosted Dark Glass with Cyan Border
                    bubble.setBackgroundResource(R.drawable.bg_bmi_badge)
                    bubble.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tri_force_navy)
                    bubble.setTextColor(ContextCompat.getColor(requireContext(), R.color.mp_color_primary_variant))
                } else {
                    // Rest day -> Faint Slate Glass
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
                // Today is Rest Day
                binding.cardHomeEmptyPrompt.visibility = View.VISIBLE
                binding.tvEmptyTitle.text = "Hôm Nay Là Ngày Nghỉ Ngơi"
                binding.tvEmptySubtitle.text = "Hãy thư giãn cơ bắp, ăn uống đủ chất và nạp năng lượng cho buổi tập tiếp theo nhé!"
                binding.btnHomeCreatePlan.visibility = View.GONE
            } else {
                binding.cardHomeEmptyPrompt.visibility = View.GONE

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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
