package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.data.WorkoutSyncScheduler
import com.google.mediapipe.examples.poselandmarker.health.HealthConnectManager
import com.google.mediapipe.examples.poselandmarker.service.StepCounterService
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentUserProfile: UserProfile? = null
    private var changingReminderSwitchProgrammatically = false
    private var localSteps = 0
    private var localCalories = 0f
    private var healthConnectSteps: Long? = null

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.permissions)) {
            WorkoutSyncScheduler.enqueue(requireContext())
        }
        refreshHealthConnectStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (_binding == null) return@registerForActivityResult

        if (granted) {
            setReminderEnabled(true)
        } else {
            updateReminderSwitch(false)
            NotificationHelper.setReminderEnabled(requireContext(), false)
            Toast.makeText(
                requireContext(),
                "Bạn cần cho phép thông báo để bật nhắc lịch tập.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
        setupReminderSettings()
        setupHealthConnect()

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

    private fun setupReminderSettings() {
        updateReminderSwitch(
            NotificationHelper.isReminderEnabled(requireContext()) && hasNotificationPermission()
        )
        renderReminderTime()

        binding.switchWorkoutReminder.setOnCheckedChangeListener { _, checked ->
            if (changingReminderSwitchProgrammatically) return@setOnCheckedChangeListener

            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setReminderEnabled(checked)
            }
        }

        binding.rowWorkoutReminder.setOnClickListener {
            showReminderTimePicker()
        }
    }

    private fun setupHealthConnect() {
        binding.rowHealthConnect.setOnClickListener {
            when (HealthConnectManager.sdkStatus(requireContext())) {
                HealthConnectClient.SDK_AVAILABLE -> lifecycleScope.launch {
                    if (HealthConnectManager.hasPermissions(requireContext())) {
                        refreshHealthConnectStatus()
                    } else {
                        healthPermissionLauncher.launch(HealthConnectManager.permissions)
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    val provider = HealthConnectManager.PROVIDER_PACKAGE_NAME
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$provider"))
                    runCatching { startActivity(marketIntent) }.onFailure {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$provider")
                            )
                        )
                    }
                }
                else -> Toast.makeText(
                    requireContext(),
                    "Thiết bị này chưa hỗ trợ Health Connect.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        refreshHealthConnectStatus()
    }

    private fun refreshHealthConnectStatus() {
        if (_binding == null) return
        when (HealthConnectManager.sdkStatus(requireContext())) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                binding.tvHealthConnectStatus.text = "Cần cài đặt hoặc cập nhật • chạm để mở"
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                binding.tvHealthConnectStatus.text = "Không được hỗ trợ trên thiết bị này"
            }
            else -> viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    if (HealthConnectManager.hasPermissions(requireContext())) {
                        HealthConnectManager.readTodaySteps(requireContext())
                    } else null
                }.onSuccess { steps ->
                    if (_binding == null) return@onSuccess
                    healthConnectSteps = steps
                    binding.tvHealthConnectStatus.text = if (steps == null) {
                        "Chưa kết nối • chạm để cấp quyền"
                    } else {
                        "Đã kết nối • ${steps.coerceAtMost(Int.MAX_VALUE.toLong())} bước hôm nay"
                    }
                    renderStepData()
                }.onFailure {
                    if (_binding != null) {
                        binding.tvHealthConnectStatus.text = "Tạm thời chưa đọc được dữ liệu"
                    }
                }
            }
        }
    }

    private fun showReminderTimePicker() {
        val context = requireContext()
        val currentHour = NotificationHelper.getReminderHour(context)
        val currentMinute = NotificationHelper.getReminderMinute(context)

        TimePickerDialog(
            context,
            { _, hour, minute ->
                NotificationHelper.setReminderTime(context, hour, minute)
                renderReminderTime()
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }

    private fun setReminderEnabled(enabled: Boolean) {
        NotificationHelper.setReminderEnabled(requireContext(), enabled)
        updateReminderSwitch(enabled)
        renderReminderTime()
    }

    private fun updateReminderSwitch(enabled: Boolean) {
        changingReminderSwitchProgrammatically = true
        binding.switchWorkoutReminder.isChecked = enabled
        changingReminderSwitchProgrammatically = false
    }

    private fun renderReminderTime() {
        val context = requireContext()
        val enabled = NotificationHelper.isReminderEnabled(context)
        val time = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            NotificationHelper.getReminderHour(context),
            NotificationHelper.getReminderMinute(context)
        )
        binding.tvWorkoutReminderTime.text = when {
            enabled && !hasNotificationPermission() ->
                "Cần cấp quyền thông báo để nhắc lúc $time"
            enabled ->
                "Hằng ngày lúc $time • Chỉ nhắc khi còn bài tập"
            else ->
                "Đang tắt • Chạm để chọn giờ"
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
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
        refreshHealthConnectStatus()
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

        localSteps = steps
        localCalories = calories
        renderStepData()
    }

    private fun renderStepData() {
        if (_binding == null) return
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
        }
        val displayedSteps = maxOf(localSteps.toLong(), healthConnectSteps ?: 0L)
        val displayedCalories = if (displayedSteps > localSteps && localSteps > 0) {
            localCalories * displayedSteps / localSteps
        } else {
            localCalories.toDouble()
        }
        binding.tvProfileSteps.text = getString(R.string.value_steps, displayedSteps.toInt())
        binding.tvProfileCalories.text =
            getString(R.string.value_calories, numberFormat.format(displayedCalories))
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

        val (bmiLabel, bmiColorRes) = when (profile.bmiType) {
            "GAY" -> Pair(getString(R.string.bmi_type_underweight), R.color.tri_force_warning)
            "CAN DOI" -> Pair(getString(R.string.bmi_type_balanced), R.color.tri_force_success)
            else -> Pair(getString(R.string.bmi_type_overweight), R.color.tri_force_error)
        }

        binding.tvProfileBmiType.text = bmiLabel
        binding.tvProfileBmiType.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bmiColorRes))
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
