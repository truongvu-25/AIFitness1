package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
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
        if (uid.isEmpty()) {
            Toast.makeText(context, R.string.camera_sign_in_required, Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        binding.etUpdateHeight.setText(profile.height.toString())
                        binding.etUpdateWeight.setText(profile.weight.toString())
                    } else {
                        Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.profile_load_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun performBmiUpdate() {
        val heightStr = binding.etUpdateHeight.text.toString().trim()
        val weightStr = binding.etUpdateWeight.text.toString().trim()

        val height = heightStr.toDoubleOrNull()
        if (height == null || height !in 80.0..250.0) {
            binding.etUpdateHeight.error = getString(R.string.bmi_invalid_height)
            return
        }
        val weight = weightStr.toDoubleOrNull()
        if (weight == null || weight !in 20.0..400.0) {
            binding.etUpdateWeight.error = getString(R.string.bmi_invalid_weight)
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
                                Toast.makeText(context, R.string.bmi_update_success, Toast.LENGTH_SHORT).show()
                                
                                // Reset / go to calendar
                                findNavController().navigate(
                                    R.id.workout_calendar_fragment,
                                    null,
                                    NavOptions.Builder()
                                        .setPopUpTo(R.id.nav_graph, true)
                                        .setLaunchSingleTop(true)
                                        .build()
                                )
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                Toast.makeText(
                                    context,
                                    getString(R.string.bmi_update_error, e.localizedMessage.orEmpty()),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        setLoading(false)
                        Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.database_error_with_detail, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
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
