package com.google.mediapipe.examples.poselandmarker.fragment

import android.graphics.Color
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
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
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
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()

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

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""
        binding.tvProfileEmail.text = email

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
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
