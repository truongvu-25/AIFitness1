package com.google.mediapipe.examples.poselandmarker.ui.fragment.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
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
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Smooth staggered entrance animation for all components
        playEntranceAnimation()

        // Check if user is already logged in (persistence session check)
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserProfileAndNavigate(currentUser.uid)
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    private fun playEntranceAnimation() {
        // Animate Header (Logo + Title + Slogan)
        binding.headerLayout.alpha = 0f
        binding.headerLayout.translationY = 45f
        binding.headerLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animate Login Form Card
        binding.cardLogin.alpha = 0f
        binding.cardLogin.translationY = 65f
        binding.cardLogin.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(480)
            .setStartDelay(100)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animate Bottom Redirect Prompt
        binding.redirectLayout.alpha = 0f
        binding.redirectLayout.translationY = 35f
        binding.redirectLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = getString(R.string.err_empty_email)
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = getString(R.string.err_empty_password)
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        checkUserProfileAndNavigate(user.uid)
                    } else {
                        setLoading(false)
                        Toast.makeText(context, R.string.system_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(
                        context,
                        getString(
                            R.string.login_failed_detail,
                            task.exception?.localizedMessage.orEmpty()
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun checkUserProfileAndNavigate(uid: String) {
        setLoading(true)
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null && profile.fullName.isNotEmpty() && profile.height > 0) {
                        // Check if weekly update is needed (7 days)
                        val lastBmiUpdated = profile.lastBmiUpdatedTime
                        val diffMs = System.currentTimeMillis() - lastBmiUpdated
                        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                        if (lastBmiUpdated > 0 && diffMs >= sevenDaysMs) {
                            findNavController().navigate(R.id.action_login_to_update_bmi)
                        } else {
                            findNavController().navigate(R.id.action_login_to_workout_calendar)
                        }
                    } else {
                        // Profile exists but is incomplete, collect user stats
                        findNavController().navigate(R.id.action_login_to_user_info)
                    }
                } else {
                    // New user database entry is missing, route to info input
                    findNavController().navigate(R.id.action_login_to_user_info)
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.calendar_profile_load_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (isLoading) {
            binding.btnLogin.visibility = View.GONE
            binding.loadingProgress.visibility = View.VISIBLE
        } else {
            binding.btnLogin.visibility = View.VISIBLE
            binding.loadingProgress.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
