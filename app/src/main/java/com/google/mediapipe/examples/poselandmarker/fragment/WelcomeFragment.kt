package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentWelcomeBinding
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Language Toggle UI
        updateLanguageToggleUI()

        binding.cardLanguageToggle.setOnClickListener {
            val currentLang = LocaleHelper.getLanguage(requireContext())
            val newLang = if (currentLang == LocaleHelper.LANG_VI) LocaleHelper.LANG_EN else LocaleHelper.LANG_VI
            LocaleHelper.setLocale(requireContext(), newLang)
            activity?.recreate()
        }

        // Check if user is already logged in (persistence session check)
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserProfileAndNavigate(currentUser.uid)
        }

        // Tap "BẮT ĐẦU NGAY" to go directly to Login
        binding.btnWelcomeStart.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_login)
        }
    }

    private fun updateLanguageToggleUI() {
        val currentLang = LocaleHelper.getLanguage(requireContext())
        val activeColor = ContextCompat.getColor(requireContext(), R.color.mp_color_primary_variant)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.tri_force_slate)

        if (currentLang == LocaleHelper.LANG_VI) {
            binding.tvLangVi.setTextColor(activeColor)
            binding.tvLangEn.setTextColor(inactiveColor)
        } else {
            binding.tvLangVi.setTextColor(inactiveColor)
            binding.tvLangEn.setTextColor(activeColor)
        }
    }

    private fun checkUserProfileAndNavigate(uid: String) {
        setLoading(true)
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null && profile.fullName.isNotEmpty() && profile.height > 0) {
                        val lastBmiUpdated = profile.lastBmiUpdatedTime
                        val diffMs = System.currentTimeMillis() - lastBmiUpdated
                        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                        if (lastBmiUpdated > 0 && diffMs >= sevenDaysMs) {
                            findNavController().navigate(R.id.action_welcome_to_update_bmi)
                        } else {
                            findNavController().navigate(R.id.action_welcome_to_workout_calendar)
                        }
                    } else {
                        findNavController().navigate(R.id.action_welcome_to_user_info)
                    }
                } else {
                    findNavController().navigate(R.id.action_welcome_to_user_info)
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    setLoading(false)
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (isLoading) {
            binding.btnWelcomeStart.visibility = View.GONE
            binding.welcomeProgress.visibility = View.VISIBLE
        } else {
            binding.btnWelcomeStart.visibility = View.VISIBLE
            binding.welcomeProgress.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
