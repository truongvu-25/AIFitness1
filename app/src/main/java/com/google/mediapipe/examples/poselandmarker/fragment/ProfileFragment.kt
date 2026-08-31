package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(StepCounterService.ACTION_STEPS_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(stepsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireActivity().registerReceiver(stepsReceiver, filter)
        }
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
