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
import com.google.mediapipe.examples.poselandmarker.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.UserExercise
import com.google.mediapipe.examples.poselandmarker.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentUserInfoBinding

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private var isEditMode = false
    private var originalCreatedTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        isEditMode = arguments?.getBoolean("isEditMode", false) ?: false
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

        if (isEditMode) {
            setupEditMode()
        }

        binding.btnSaveInfo.setOnClickListener {
            saveUserInfo()
        }
    }

    private fun setupEditMode() {
        binding.tvUserInfoTitle.text = "Chỉnh Sửa Hồ Sơ"
        binding.btnSaveInfo.text = "LƯU THAY ĐỔI"
        
        val uid = auth.currentUser?.uid ?: return
        setLoading(true)
        
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        binding.etFullName.setText(profile.fullName)
                        binding.etAge.setText(profile.age.toString())
                        binding.etHeight.setText(profile.height.toString())
                        binding.etWeight.setText(profile.weight.toString())
                        originalCreatedTime = profile.createdTime
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserInfo() {
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

        val createdTime = if (isEditMode) originalCreatedTime else System.currentTimeMillis()

        val profile = UserProfile(
            uid = uid,
            fullName = fullName,
            age = age,
            height = height,
            weight = weight,
            bmi = formattedBmi,
            bmiType = bmiType,
            createdTime = createdTime,
            lastBmiUpdatedTime = System.currentTimeMillis()
        )

        db.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                if (isEditMode) {
                    setLoading(false)
                    Toast.makeText(context, "Cập nhật hồ sơ thành công!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    generateWorkoutPlan(uid, bmiType)
                }
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

        batch.commit()
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(context, "Đã tạo lộ trình tập luyện 30 ngày!", Toast.LENGTH_SHORT).show()
                NotificationHelper.scheduleDailyReminder(requireContext())
                findNavController().navigate(R.id.action_user_info_to_workout_calendar)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi tạo lộ trình: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getExercisesForBmiAndDay(bmiType: String, dayIndex: Int): List<UserExercise> {
        val weekMultiplier = when {
            dayIndex <= 7 -> 1.0     // Week 1
            dayIndex <= 14 -> 1.2    // Week 2
            dayIndex <= 21 -> 1.4    // Week 3
            else -> 1.6              // Week 4
        }

        return when (bmiType) {
            "GAY" -> { // Focus on muscle building & strength
                if (dayIndex % 2 != 0) { // Odd Days
                    listOf(
                        UserExercise("pushup", (10 * weekMultiplier).toInt()),
                        UserExercise("squat", (12 * weekMultiplier).toInt()),
                        UserExercise("plank", (2 * weekMultiplier).toInt())
                    )
                } else { // Even Days
                    listOf(
                        UserExercise("splitsquat", (10 * weekMultiplier).toInt()),
                        UserExercise("situp", (12 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (2 * weekMultiplier).toInt())
                    )
                }
            }
            "CAN DOI" -> { // Balanced general fitness
                if (dayIndex % 2 != 0) { // Odd Days
                    listOf(
                        UserExercise("pushup", (15 * weekMultiplier).toInt()),
                        UserExercise("squat", (15 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt()),
                        UserExercise("plank", (3 * weekMultiplier).toInt())
                    )
                } else { // Even Days
                    listOf(
                        UserExercise("splitsquat", (12 * weekMultiplier).toInt()),
                        UserExercise("situp", (15 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (3 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt())
                    )
                }
            }
            else -> { // THUA CAN - High-intensity fat burning
                if (dayIndex % 2 != 0) { // Odd Days
                    listOf(
                        UserExercise("jumpingjack", (30 * weekMultiplier).toInt()),
                        UserExercise("squat", (20 * weekMultiplier).toInt()),
                        UserExercise("situp", (20 * weekMultiplier).toInt()),
                        UserExercise("plank", (3 * weekMultiplier).toInt())
                    )
                } else { // Even Days
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
