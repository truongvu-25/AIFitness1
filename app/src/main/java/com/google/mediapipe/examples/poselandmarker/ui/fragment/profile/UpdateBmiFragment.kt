package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

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
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentUpdateBmiBinding

class UpdateBmiFragment : Fragment() {

    private var _binding: FragmentUpdateBmiBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var uid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        uid = auth.currentUser?.uid ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdateBmiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentStats()

        binding.btnUpdateBmi.setOnClickListener {
            performBmiUpdate()
        }
    }

    private fun loadCurrentStats() {
        if (uid.isEmpty()) return
        setLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        binding.etUpdateHeight.setText(profile.height.toString())
                        binding.etUpdateWeight.setText(profile.weight.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performBmiUpdate() {
        val heightStr = binding.etUpdateHeight.text.toString().trim()
        val weightStr = binding.etUpdateWeight.text.toString().trim()

        val height = heightStr.toDoubleOrNull()
        if (height == null || height <= 0) {
            binding.etUpdateHeight.error = "Vui lòng nhập chiều cao hợp lệ"
            return
        }
        val weight = weightStr.toDoubleOrNull()
        if (weight == null || weight <= 0) {
            binding.etUpdateWeight.error = "Vui lòng nhập cân nặng hợp lệ"
            return
        }

        setLoading(true)

        // Recalculate BMI
        val heightInMeters = height / 100.0
        val bmi = weight / (heightInMeters * heightInMeters)
        val formattedBmi = Math.round(bmi * 10.0) / 10.0

        // BMI classification
        val bmiType = when {
            bmi < 18.5 -> "GAY"
            bmi < 25.0 -> "CAN DOI"
            else -> "THUA CAN"
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentProfile = document.toObject(UserProfile::class.java)
                    if (currentProfile != null) {
                        val updatedProfile = currentProfile.copy(
                            height = height,
                            weight = weight,
                            bmi = formattedBmi,
                            bmiType = bmiType,
                            lastBmiUpdatedTime = System.currentTimeMillis()
                        )

                        db.collection("users").document(uid).set(updatedProfile)
                            .addOnSuccessListener {
                                setLoading(false)
                                Toast.makeText(context, "Cập nhật chỉ số cơ thể thành công!", Toast.LENGTH_SHORT).show()
                                
                                // Reset / go to calendar
                                findNavController().navigate(R.id.action_login_to_workout_calendar)
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                Toast.makeText(context, "Lỗi lưu cập nhật: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        setLoading(false)
                    }
                } else {
                    setLoading(false)
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(context, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.btnUpdateBmi.visibility = View.GONE
            binding.updateBmiProgress.visibility = View.VISIBLE
        } else {
            binding.btnUpdateBmi.visibility = View.VISIBLE
            binding.updateBmiProgress.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
