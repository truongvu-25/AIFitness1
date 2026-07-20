package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
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
import com.google.mediapipe.examples.poselandmarker.StepCounterService
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentUserProfile: UserProfile? = null

    private val stepsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StepCounterService.ACTION_STEPS_UPDATED) {
                val steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS, 0)
                val calories = intent.getFloatExtra(StepCounterService.EXTRA_CALORIES, 0f)
                displayStepData(steps, calories)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start Background Step Counter Service automatically
        StepCounterService.startService(requireContext())

        loadUserProfile()
        loadInitialStepData()

        binding.btnEditProfile.setOnClickListener {
            val bundle = Bundle().apply {
                putBoolean("isEditMode", true)
            }
            findNavController().navigate(R.id.action_profile_to_user_info, bundle)
        }

        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(StepCounterService.ACTION_STEPS_UPDATED)
        androidx.core.content.ContextCompat.registerReceiver(
            requireContext(),
            stepsReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadInitialStepData()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireActivity().unregisterReceiver(stepsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadInitialStepData() {
        val steps = StepCounterService.getSavedSteps(requireContext())
        val calo = StepCounterService.getSavedCalories(requireContext())
        displayStepData(steps, calo)
    }

    private fun displayStepData(steps: Int, calories: Float) {
        binding.tvProfileSteps.text = "$steps bước"
        binding.tvProfileCalories.text = String.format(Locale.US, "%.1f kcal", calories)

        currentUserProfile?.let { profile ->
            updateBmiAdvice(profile.bmiType, calories, steps)
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""
        binding.tvProfileEmail.text = email

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        currentUserProfile = profile
                        displayProfileData(profile)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Lỗi tải hồ sơ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayProfileData(profile: UserProfile) {
        binding.tvProfileName.text = profile.fullName
        binding.tvProfileAge.text = profile.age.toString()
        binding.tvProfileHeight.text = "${profile.height} cm"
        binding.tvProfileWeight.text = "${profile.weight} kg"
        binding.tvProfileBmi.text = profile.bmi.toString()

        val (bmiLabel, bmiColor) = when (profile.bmiType) {
            "GAY" -> Pair("GẦY", "#F57C00")        // Orange
            "CAN DOI" -> Pair("CÂN ĐỐI", "#388E3C") // Green
            else -> Pair("THỪA CÂN", "#D32F2F")     // Red
        }

        binding.tvProfileBmiType.text = bmiLabel
        binding.tvProfileBmiType.setBackgroundColor(Color.parseColor(bmiColor))

        val currentSteps = StepCounterService.getSavedSteps(requireContext())
        val currentCalories = StepCounterService.getSavedCalories(requireContext())
        updateBmiAdvice(profile.bmiType, currentCalories, currentSteps)
    }

    private fun updateBmiAdvice(bmiType: String, calories: Float, steps: Int) {
        val title: String
        val message: String
        val bgColor: String
        val strokeColor: String
        val titleColor: String

        val caloStr = String.format(Locale.US, "%.1f", calories)

        when (bmiType) {
            "GAY" -> {
                if (calories > 180f) {
                    title = "⚠️ CẢNH BÁO TIÊU THỤ CALO NĂNG LƯỢNG CAO"
                    message = "Hôm nay bạn đã đi $steps bước (~$caloStr kcal). Với thể trạng GẦY, việc tiêu thụ calo nhiều có thể làm giảm cân thêm. Hãy nạp bổ sung 300-500 kcal từ thực phẩm giàu Protein (thịt, trứng, sữa) và Tinh bột để bù lại năng lượng tiêu hao và hỗ trợ tăng cân an toàn!"
                    bgColor = "#FFF3E0"     // Light Orange
                    strokeColor = "#FF9800" // Orange
                    titleColor = "#E65100"  // Dark Orange
                } else {
                    title = "💡 LỜI KHUYÊN DUY TRÌ THỂ TRẠNG GẦY"
                    message = "Mức vận động hôm nay của bạn ($steps bước, ~$caloStr kcal) rất vừa phải và tốt cho sức khỏe tim mạch. Hãy duy trì lối sống này kết hợp chế độ ăn dinh dưỡng đa lượng để cải thiện thể trạng tốt nhất nhé!"
                    bgColor = "#E8F5E9"     // Light Green
                    strokeColor = "#4CAF50" // Green
                    titleColor = "#1B5E20"  // Dark Green
                }
            }
            "CAN DOI" -> {
                if (calories >= 240f) {
                    title = "🎉 PHONG ĐỘ VẬN ĐỘNG TÍCH CỰC"
                    message = "Rất xuất sắc! Bạn đã đi được $steps bước và tiêu thụ ~$caloStr kcal hôm nay. Hãy tiếp tục giữ vững phong độ vận động tuyệt vời này để duy trì vóc dáng cân đối và độ dẻo dai dài lâu!"
                    bgColor = "#E8F5E9"     // Light Green
                    strokeColor = "#4CAF50" // Green
                    titleColor = "#1B5E20"  // Dark Green
                } else {
                    title = "🏃 LỜI KHUYÊN TĂNG CƯỜNG VẬN ĐỘNG"
                    message = "Mức vận động hôm nay của bạn ($steps bước, ~$caloStr kcal) còn hơi khiêm tốn. Bạn nên dành 15-20 phút đi bộ nhẹ nhàng hoặc hoàn thành bài tập trong ứng dụng để giữ dáng và tăng sức bền cơ thể!"
                    bgColor = "#E1F5FE"     // Light Blue
                    strokeColor = "#0288D1" // Blue
                    titleColor = "#01579B"  // Dark Blue
                }
            }
            else -> { // "THUA CAN"
                if (calories >= 240f) {
                    title = "🔥 KẾT QUẢ ĐỐT MỠ THỪA XUẤT SẮC"
                    message = "Tuyệt vời! Bạn đã đi $steps bước và tiêu thụ thành công ~$caloStr kcal mỡ thừa hôm nay. Sự kiên trì đi bộ và luyện tập này sẽ giúp bạn mau chóng đạt được chỉ số cân nặng lý tưởng!"
                    bgColor = "#E8F5E9"     // Light Green
                    strokeColor = "#4CAF50" // Green
                    titleColor = "#1B5E20"  // Dark Green
                } else {
                    title = "⚠️ KHUYẾN KHÍCH ĐỐT CHÁY CALO MỠ THỪA"
                    message = "Hôm nay bạn mới tiêu thụ ~$caloStr kcal ($steps bước), mức vận động này còn thấp với người THỪA CÂN. Hãy đứng dậy đi dạo hoặc thực hiện bài tập Squat/Jumping Jack để kích hoạt quá trình đốt mỡ ngay nhé!"
                    bgColor = "#FFEBEE"     // Light Red
                    strokeColor = "#E53935" // Red
                    titleColor = "#B71C1C"  // Dark Red
                }
            }
        }

        binding.cardBmiAdvice.setCardBackgroundColor(Color.parseColor(bgColor))
        binding.cardBmiAdvice.strokeColor = Color.parseColor(strokeColor)
        binding.tvBmiAdviceTitle.text = title
        binding.tvBmiAdviceTitle.setTextColor(Color.parseColor(titleColor))
        binding.tvBmiAdviceMessage.text = message
    }

    private fun performLogout() {
        // Sign out from FirebaseAuth
        auth.signOut()

        // Cancel scheduled reminders upon sign out
        NotificationHelper.cancelReminder(requireContext())

        Toast.makeText(context, "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show()

        // Navigate back to Login fragment
        findNavController().navigate(R.id.action_profile_to_login)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
