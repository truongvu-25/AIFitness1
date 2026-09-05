package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

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
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.service.StepCounterService
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper
import java.text.NumberFormat
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

        val currentLanguage = LocaleHelper.getLanguage(requireContext())
        binding.tvCurrentLanguage.setText(
            if (currentLanguage == LocaleHelper.LANG_VI) {
                R.string.profile_language_vi
            } else {
                R.string.profile_language_en
            }
        )
        binding.btnLanguage.setOnClickListener {
            val newLanguage = if (currentLanguage == LocaleHelper.LANG_VI) {
                LocaleHelper.LANG_EN
            } else {
                LocaleHelper.LANG_VI
            }
            LocaleHelper.setLocale(requireContext(), newLanguage)
            requireActivity().recreate()
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
        loadUserProfile()
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
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
        }
        binding.tvProfileSteps.text = getString(R.string.value_steps, steps)
        binding.tvProfileCalories.text =
            getString(R.string.value_calories, numberFormat.format(calories))
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(context, R.string.camera_sign_in_required, Toast.LENGTH_SHORT).show()
            return
        }
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
                Toast.makeText(
                    context,
                    getString(R.string.profile_load_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun displayProfileData(profile: UserProfile) {
        binding.tvProfileName.text = profile.fullName
        binding.tvProfileAge.text = profile.age.toString()
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
        }
        binding.tvProfileHeight.text =
            getString(R.string.value_centimeters, numberFormat.format(profile.height))
        binding.tvProfileWeight.text =
            getString(R.string.value_kilograms, numberFormat.format(profile.weight))
        binding.tvProfileBmi.text = numberFormat.format(profile.bmi)

        val (bmiLabel, bmiColor) = when (profile.bmiType) {
            "GAY" -> Pair(getString(R.string.bmi_type_underweight), "#F57C00")
            "CAN DOI" -> Pair(getString(R.string.bmi_type_balanced), "#388E3C")
            else -> Pair(getString(R.string.bmi_type_overweight), "#D32F2F")
        }

        binding.tvProfileBmiType.text = bmiLabel
        binding.tvProfileBmiType.setBackgroundColor(Color.parseColor(bmiColor))
    }

    private fun performLogout() {
        // Sign out from FirebaseAuth
        auth.signOut()

        // Cancel scheduled reminders upon sign out
        NotificationHelper.cancelReminder(requireContext())
        StepCounterService.stopService(requireContext())

        Toast.makeText(context, R.string.profile_logged_out, Toast.LENGTH_SHORT).show()

        // Navigate back to Login fragment
        findNavController().navigate(R.id.action_profile_to_login)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
