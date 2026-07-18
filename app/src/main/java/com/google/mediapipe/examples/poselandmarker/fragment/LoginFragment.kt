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
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
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

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Vui lòng nhập email"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Vui lòng nhập mật khẩu"
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
                        Toast.makeText(context, "Đã xảy ra lỗi hệ thống.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(
                        context,
                        "Đăng nhập thất bại: ${task.exception?.localizedMessage}",
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
                // In case of firestore query failure but auth is successful, default to info collection
                Toast.makeText(context, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_login_to_user_info)
            }
    }

    private fun setLoading(isLoading: Boolean) {
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
