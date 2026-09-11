package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

import com.google.mediapipe.examples.poselandmarker.utils.addOnViewSuccessListener
import com.google.mediapipe.examples.poselandmarker.utils.addOnViewFailureListener
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

        val callbackOwner = viewLifecycleOwner
        db.collection("users").document(uid).get()
            .addOnViewSuccessListener(callbackOwner) { document ->
                if (!isAdded || _binding == null ||
                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewSuccessListener
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        binding.etUpdateHeight.setText(profile.height.toString())
                        binding.etUpdateWeight.setText(profile.weight.toString())
                    }
                }
            }
            .addOnViewFailureListener(callbackOwner) { e ->
                if (!isAdded || _binding == null ||
                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewFailureListener
                setLoading(false)
                Toast.makeText(context, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performBmiUpdate() {
        val heightStr = binding.etUpdateHeight.text.toString().trim()
        val weightStr = binding.etUpdateWeight.text.toString().trim()

        val height = heightStr.toDoubleOrNull()
        if (height == null || !height.isFinite() || height <= 0) {
            binding.etUpdateHeight.error = "Vui lòng nhập chiều cao hợp lệ"
            return
        }
        val weight = weightStr.toDoubleOrNull()
        if (weight == null || !weight.isFinite() || weight <= 0) {
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

        val callbackOwner = viewLifecycleOwner
        db.collection("users").document(uid).get()
            .addOnViewSuccessListener(callbackOwner) { document ->
                if (!isAdded || _binding == null ||
                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewSuccessListener
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

                        db.collection("users").document(uid).update(mapOf(
                            "height" to updatedProfile.height,
                            "weight" to updatedProfile.weight,
                            "bmi" to updatedProfile.bmi,
                            "bmiType" to updatedProfile.bmiType,
                            "lastBmiUpdatedTime" to updatedProfile.lastBmiUpdatedTime
                        ))
                            .addOnViewSuccessListener(callbackOwner) {
                                if (!isAdded || _binding == null ||
                                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewSuccessListener
                                setLoading(false)
                                Toast.makeText(context, "Cập nhật chỉ số cơ thể thành công!", Toast.LENGTH_SHORT).show()
                                
                                // Reset / go to calendar
                                findNavController().navigate(R.id.action_update_bmi_to_workout_calendar)
                            }
                            .addOnViewFailureListener(callbackOwner) { e ->
                                if (!isAdded || _binding == null ||
                                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewFailureListener
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
            .addOnViewFailureListener(callbackOwner) { e ->
                if (!isAdded || _binding == null ||
                    findNavController().currentDestination?.id != R.id.update_bmi_fragment) return@addOnViewFailureListener
                setLoading(false)
                Toast.makeText(context, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
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
