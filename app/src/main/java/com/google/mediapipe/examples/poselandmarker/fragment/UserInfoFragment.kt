package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.mediapipe.examples.poselandmarker.Exercise
import com.google.mediapipe.examples.poselandmarker.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentUserInfoBinding

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
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
        _binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveInfo.setOnClickListener {
            saveUserInfoAndGeneratePlan()
        }
    }

    private fun saveUserInfoAndGeneratePlan() {
        val uid = auth.currentUser?.uid ?: return
        val fullName = binding.etFullName.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val heightStr = binding.etHeight.text.toString().trim()
        val weightStr = binding.etWeight.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Vui lòng nhập họ và tên"
            return
        }
        val age = ageStr.toIntOrNull()
        if (age == null || age <= 0) {
            binding.etAge.error = "Vui lòng nhập tuổi hợp lệ"
            return
        }
        val height = heightStr.toDoubleOrNull()
        if (height == null || height <= 0) {
            binding.etHeight.error = "Vui lòng nhập chiều cao hợp lệ"
            return
        }
        val weight = weightStr.toDoubleOrNull()
        if (weight == null || weight <= 0) {
            binding.etWeight.error = "Vui lòng nhập cân nặng hợp lệ"
            return
        }

        setLoading(true)

        // Calculate BMI
        val heightInMeters = height / 100.0
        val bmi = weight / (heightInMeters * heightInMeters)
        val formattedBmi = Math.round(bmi * 10.0) / 10.0

        // BMI classification
        val bmiType = when {
            bmi < 18.5 -> "GAY"
            bmi < 25.0 -> "CAN DOI"
            else -> "THUA CAN"
        }

        val profile = UserProfile(
            uid = uid,
            fullName = fullName,
            age = age,
            height = height,
            weight = weight,
            bmi = formattedBmi,
            bmiType = bmiType,
            createdTime = System.currentTimeMillis()
        )

        // Write profile document
        db.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                generateWorkoutPlan(uid, bmiType)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi lưu thông tin: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateWorkoutPlan(uid: String, bmiType: String) {
        val batch = db.batch()

        // Generate customized workouts based on BMI for 30 Days
        for (day in 1..30) {
            val exercises = getExercisesForBmiAndDay(bmiType, day)
            val workoutDay = WorkoutDay(dayIndex = day, exercises = exercises)
            val dayDocRef = db.collection("users").document(uid)
                .collection("workouts").document("day_$day")
            batch.set(dayDocRef, workoutDay)
        }

        // Commit batch writes
        batch.commit()
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(context, "Đã khởi tạo lộ trình tập luyện 30 ngày!", Toast.LENGTH_SHORT).show()
                
                // Enable 8:00 AM notification reminder
                NotificationHelper.scheduleDailyReminder(requireContext())
                
                // Navigate to workout calendar
                findNavController().navigate(R.id.action_user_info_to_workout_calendar)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi tạo lộ trình: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getExercisesForBmiAndDay(bmiType: String, dayIndex: Int): List<Exercise> {
        // Scaling factor for targets based on the week index to simulate progressive overload
        val weekMultiplier = when {
            dayIndex <= 7 -> 1.0     // Week 1: Base targets
            dayIndex <= 14 -> 1.2    // Week 2: +20% targets
            dayIndex <= 21 -> 1.4    // Week 3: +40% targets
            else -> 1.6              // Week 4: +60% targets
        }

        return when (bmiType) {
            "GAY" -> listOf(
                Exercise(
                    id = "pushup",
                    name = "Hít Đất Cơ Bản (Push Up)",
                    targetCount = (10 * weekMultiplier).toInt(),
                    description = "Giữ thẳng người, hạ thấp ngực xuống sát đất rồi đẩy người lên.",
                    videoUrl = "https://example.com/videos/pushup.mp4"
                ),
                Exercise(
                    id = "squat",
                    name = "Ngồi Xổm (Squat)",
                    targetCount = (12 * weekMultiplier).toInt(),
                    description = "Đứng thẳng chân rộng bằng vai, hạ thấp hông xuống sâu như ngồi ghế rồi đứng lên.",
                    videoUrl = "https://example.com/videos/squat.mp4"
                ),
                Exercise(
                    id = "lunge",
                    name = "Chùng Chân (Lunge)",
                    targetCount = (10 * weekMultiplier).toInt(),
                    description = "Bước một chân lên phía trước, hạ thấp gối cho cả hai chân tạo góc vuông rồi rút chân về.",
                    videoUrl = "https://example.com/videos/lunge.mp4"
                )
            )
            "CAN DOI" -> listOf(
                Exercise(
                    id = "pushup",
                    name = "Hít Đất Cơ Bản (Push Up)",
                    targetCount = (15 * weekMultiplier).toInt(),
                    description = "Giữ thẳng lưng, ngực hạ thấp gần chạm sàn, khuỷu tay mở góc 45 độ rồi đẩy lên.",
                    videoUrl = "https://example.com/videos/pushup.mp4"
                ),
                Exercise(
                    id = "squat",
                    name = "Ngồi Xổm (Squat)",
                    targetCount = (15 * weekMultiplier).toInt(),
                    description = "Hạ thấp hông sao cho đùi song song với sàn, giữ thẳng lưng và đầu gối không vượt mũi chân.",
                    videoUrl = "https://example.com/videos/squat.mp4"
                ),
                Exercise(
                    id = "jumpingjacks",
                    name = "Nhảy Dang Tay Chân (Jumping Jacks)",
                    targetCount = (20 * weekMultiplier).toInt(),
                    description = "Nhảy bật rộng chân ra đồng thời vung tay chạm trên đầu, sau đó nhảy thu chân khép tay.",
                    videoUrl = "https://example.com/videos/jumpingjacks.mp4"
                ),
                Exercise(
                    id = "crunch",
                    name = "Gập Bụng (Crunch)",
                    targetCount = (15 * weekMultiplier).toInt(),
                    description = "Nằm ngửa gối co, dùng cơ bụng nâng vai lên khỏi thảm rồi hạ xuống chậm rãi.",
                    videoUrl = "https://example.com/videos/crunch.mp4"
                )
            )
            else -> listOf( // THUA CAN (Focus cardio / Fat burning)
                Exercise(
                    id = "jumpingjacks",
                    name = "Nhảy Dang Tay Chân (Jumping Jacks)",
                    targetCount = (25 * weekMultiplier).toInt(),
                    description = "Nhảy liên tục dang tay chân để làm nóng toàn thân và kích thích tim mạch.",
                    videoUrl = "https://example.com/videos/jumpingjacks.mp4"
                ),
                Exercise(
                    id = "squat",
                    name = "Ngồi Xổm (Squat)",
                    targetCount = (15 * weekMultiplier).toInt(),
                    description = "Kích hoạt các bó cơ đùi trước, đùi sau và cơ mông để đốt cháy tối đa calo.",
                    videoUrl = "https://example.com/videos/squat.mp4"
                ),
                Exercise(
                    id = "highknees",
                    name = "Nâng Cao Đùi (High Knees)",
                    targetCount = (30 * weekMultiplier).toInt(),
                    description = "Chạy tại chỗ nâng cao đùi sao cho đùi vuông góc với thân người.",
                    videoUrl = "https://example.com/videos/highknees.mp4"
                ),
                Exercise(
                    id = "burpee",
                    name = "Bật Nhảy Hít Đất (Burpee)",
                    targetCount = (8 * weekMultiplier).toInt(),
                    description = "Từ đứng thẳng, squat xuống, bật chân ra hít đất, thu chân bật nhảy cao vỗ tay.",
                    videoUrl = "https://example.com/videos/burpee.mp4"
                )
            )
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.btnSaveInfo.visibility = View.GONE
            binding.infoProgress.visibility = View.VISIBLE
        } else {
            binding.btnSaveInfo.visibility = View.VISIBLE
            binding.infoProgress.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
