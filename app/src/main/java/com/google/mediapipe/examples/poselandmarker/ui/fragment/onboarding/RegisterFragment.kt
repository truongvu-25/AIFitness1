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
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
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
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Smooth entrance animation
        playEntranceAnimation()

        binding.btnRegister.setOnClickListener {
            performRegistration()
        }

        binding.tvGoToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    private fun playEntranceAnimation() {
        // Animate Header
        binding.headerLayout.alpha = 0f
        binding.headerLayout.translationY = 45f
        binding.headerLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animate Form Card
        binding.cardRegister.alpha = 0f
        binding.cardRegister.translationY = 65f
        binding.cardRegister.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(480)
            .setStartDelay(100)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animate Bottom Redirect
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

    private fun performRegistration() {
        val email = binding.etRegisterEmail.text.toString().trim()
        val password = binding.etRegisterPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etRegisterEmail.error = "Vui lòng nhập email"
            return
        }
        if (password.isEmpty()) {
            binding.etRegisterPassword.error = "Vui lòng nhập mật khẩu"
            return
        }
        if (password.length < 6) {
            binding.etRegisterPassword.error = "Mật khẩu phải có ít nhất 6 ký tự"
            return
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Mật khẩu xác nhận không khớp"
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(context, "Đăng ký tài khoản thành công!", Toast.LENGTH_SHORT).show()
                    // Redirect to collect profile stats
                    findNavController().navigate(R.id.action_register_to_user_info)
                } else {
                    Toast.makeText(
                        context,
                        "Đăng ký thất bại: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (isLoading) {
            binding.btnRegister.visibility = View.GONE
            binding.registerProgress.visibility = View.VISIBLE
        } else {
            binding.btnRegister.visibility = View.VISIBLE
            binding.registerProgress.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
