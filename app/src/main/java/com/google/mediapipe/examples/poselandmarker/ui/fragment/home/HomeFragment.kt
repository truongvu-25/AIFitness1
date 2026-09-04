package com.google.mediapipe.examples.poselandmarker.ui.fragment.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentHomeBinding
import com.google.mediapipe.examples.poselandmarker.databinding.ItemSavedPlanCardBinding
import com.google.mediapipe.examples.poselandmarker.model.UserExercise
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.service.StepCounterService
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentDayIndex: Int = 1
    private var createdTime: Long = 0L


    // =============================================================
    // STEP COUNTER
    // =============================================================

    private val stepsReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            if (
                intent?.action ==
                StepCounterService.ACTION_STEPS_UPDATED
            ) {

                val steps =
                    intent.getIntExtra(
                        StepCounterService.EXTRA_STEPS,
                        0
                    )

                val calories =
                    intent.getFloatExtra(
                        StepCounterService.EXTRA_CALORIES,
                        0f
                    )

                displayActivityData(
                    steps,
                    calories
                )
            }
        }
    }


    // =============================================================
    // LIFECYCLE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentHomeBinding.inflate(
                inflater,
                container,
                false
            )

        return binding.root
    }


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        StepCounterService.startService(
            requireContext()
        )

        setupActions()
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        val filter =
            IntentFilter(
                StepCounterService.ACTION_STEPS_UPDATED
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            requireActivity().registerReceiver(
                stepsReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            requireActivity().registerReceiver(
                stepsReceiver,
                filter
            )
        }

        refreshHome()
    }


    override fun onPause() {
        super.onPause()

        try {

            requireActivity().unregisterReceiver(
                stepsReceiver
            )

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // =============================================================
    // ACTIONS
    // =============================================================

    private fun setupActions() {

        binding.btnHomeCreatePlan
            .setOnClickListener {

                navigateSafely(
                    R.id.create_custom_plan_fragment
                )
            }


        binding.btnGoToCurrentWorkout
            .setOnClickListener {

                navigateSafely(
                    R.id.workout_calendar_fragment
                )
            }


        binding.cardActivePlan
            .setOnClickListener {

                navigateSafely(
                    R.id.workout_calendar_fragment
                )
            }


        binding.btnQuickCalendar
            .setOnClickListener {

                navigateSafely(
                    R.id.workout_calendar_fragment
                )
            }


        binding.btnQuickLibrary
            .setOnClickListener {

                navigateSafely(
                    R.id.library_fragment
                )
            }


        binding.btnQuickCreatePlan
            .setOnClickListener {

                navigateSafely(
                    R.id.create_custom_plan_fragment
                )
            }


        binding.btnQuickProfile
            .setOnClickListener {

                navigateSafely(
                    R.id.profile_fragment
                )
            }
    }


    private fun navigateSafely(
        destinationId: Int
    ) {

        if (!isAdded) {
            return
        }

        try {

            findNavController().navigate(
                destinationId
            )

        } catch (_: Exception) {
            // Prevent duplicate navigation tap crashes.
        }
    }


    // =============================================================
    // REFRESH HOME
    // =============================================================

    private fun refreshHome() {

        if (!isAdded || _binding == null) {
            return
        }

        loadInitialActivityData()

        loadDashboardFromFirebase()

        syncPlansFromCloudAndDisplay()
    }


    // =============================================================
    // STEP + CALORIE DASHBOARD
    // =============================================================

    private fun loadInitialActivityData() {

        val ctx =
            context ?: return

        val steps =
            StepCounterService
                .getSavedSteps(ctx)

        val calories =
            StepCounterService
                .getSavedCalories(ctx)

        displayActivityData(
            steps,
            calories
        )
    }


    private fun displayActivityData(
        steps: Int,
        calories: Float
    ) {

        if (_binding == null) {
            return
        }

        val formatter =
            NumberFormat.getIntegerInstance(
                Locale.forLanguageTag("vi-VN")
            )

        binding.tvHomeSteps.text =
            formatter.format(steps)

        binding.tvHomeCalories.text =
            String.format(
                Locale.US,
                "%.0f",
                calories
            )
    }


    // =============================================================
    // USER + CURRENT 30 DAY PLAN
    // =============================================================

    private fun loadDashboardFromFirebase() {

        val uid =
            auth.currentUser?.uid

        if (uid.isNullOrEmpty()) {

            showNoActivePlanState()
            return
        }


        db.collection("users")
            .document(uid)
            .get()

            .addOnSuccessListener { document ->

                if (
                    !isAdded ||
                    _binding == null
                ) {
                    return@addOnSuccessListener
                }


                if (!document.exists()) {

                    showNoActivePlanState()
                    return@addOnSuccessListener
                }


                val profile =
                    document.toObject(
                        UserProfile::class.java
                    )


                if (profile == null) {

                    showNoActivePlanState()
                    return@addOnSuccessListener
                }


                // =================================================
                // USER NAME
                // =================================================

                val fallbackName =
                    auth.currentUser
                        ?.email
                        ?.substringBefore("@")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Bạn"


                binding.tvHomeGreetingName.text =
                    profile.fullName
                        .takeIf { it.isNotBlank() }
                        ?: fallbackName


                // =================================================
                // PLAN
                // =================================================

                createdTime =
                    profile.createdTime


                val customPlanName =
                    document.getString(
                        "customPlanName"
                    )


                val planName =
                    if (
                        !customPlanName.isNullOrBlank()
                    ) {

                        customPlanName

                    } else {

                        getDefaultPlanName(
                            profile.bmiType
                        )
                    }


                if (createdTime > 0L) {

                    currentDayIndex =
                        calculateCurrentDay(
                            createdTime
                        )


                    val progress =
                        (
                                currentDayIndex /
                                        30f *
                                        100f
                                )
                            .roundToInt()
                            .coerceIn(
                                0,
                                100
                            )


                    binding.cardActivePlan.visibility =
                        View.VISIBLE


                    binding.cardHomeEmptyPrompt.visibility =
                        View.GONE


                    binding.tvHomePlanName.text =
                        planName


                    binding.tvHomePlanDay.text =
                        "Ngày $currentDayIndex/30"


                    binding.tvHomePlanProgress.text =
                        "$progress%"


                    binding.progressHomePlan.progress =
                        progress


                    loadTodayWorkout(
                        uid,
                        currentDayIndex
                    )


                } else {

                    showNoActivePlanState()
                }
            }

            .addOnFailureListener {

                if (_binding != null) {

                    showNoActivePlanState()
                }
            }
    }


    private fun showNoActivePlanState() {

        if (_binding == null) {
            return
        }


        val fallbackName =
            auth.currentUser
                ?.email
                ?.substringBefore("@")
                ?.takeIf { it.isNotBlank() }
                ?: "Bạn"


        binding.tvHomeGreetingName.text =
            fallbackName


        binding.cardActivePlan.visibility =
            View.GONE


        binding.cardHomeEmptyPrompt.visibility =
            View.VISIBLE


        binding.tvHomeTodayExercises.text =
            "0/0"
    }


    private fun calculateCurrentDay(
        planCreatedTime: Long
    ): Int {

        val elapsed =
            (
                    System.currentTimeMillis() -
                            planCreatedTime
                    )
                .coerceAtLeast(0L)


        val oneDay =
            24L *
                    60L *
                    60L *
                    1000L


        return (
                elapsed /
                        oneDay
                )
            .toInt()
            .plus(1)
            .coerceIn(
                1,
                30
            )
    }


    private fun getDefaultPlanName(
        bmiType: String
    ): String {

        return when (bmiType) {

            "GAY" ->
                "Tăng Cân & Tăng Cơ"

            "CAN DOI" ->
                "Săn Chắc Thể Hình"

            else ->
                "Đốt Mỡ & Giảm Cân"
        }
    }


    // =============================================================
    // TODAY WORKOUT METRIC
    // =============================================================

    private fun loadTodayWorkout(
        uid: String,
        dayIndex: Int
    ) {

        db.collection("users")
            .document(uid)
            .collection("workouts")
            .document(
                "day_$dayIndex"
            )
            .get()

            .addOnSuccessListener { document ->

                if (
                    !isAdded ||
                    _binding == null
                ) {
                    return@addOnSuccessListener
                }


                if (!document.exists()) {

                    binding.tvHomeTodayExercises.text =
                        "0/0"

                    return@addOnSuccessListener
                }


                val workoutDay =
                    document.toObject(
                        WorkoutDay::class.java
                    )


                if (workoutDay == null) {

                    binding.tvHomeTodayExercises.text =
                        "0/0"

                    return@addOnSuccessListener
                }


                if (workoutDay.isRestDay) {

                    binding.tvHomeTodayExercises.text =
                        "NGHỈ"

                    return@addOnSuccessListener
                }


                val total =
                    workoutDay.exercises.size


                val completed =
                    workoutDay.exercises
                        .count {
                            it.status == 1
                        }


                binding.tvHomeTodayExercises.text =
                    "$completed/$total"
            }

            .addOnFailureListener {

                if (_binding != null) {

                    binding.tvHomeTodayExercises.text =
                        "0/0"
                }
            }
    }


    // =============================================================
    // CUSTOM PLAN CLOUD SYNC
    // KEEP EXISTING FIREBASE STRUCTURE
    // =============================================================

    private fun syncPlansFromCloudAndDisplay() {

        val ctx =
            context ?: return


        if (
            !isAdded ||
            _binding == null
        ) {
            return
        }


        loadSavedPlansFromLocal()


        val uid =
            auth.currentUser?.uid
                ?: return


        db.collection("users")
            .document(uid)
            .collection("custom_plans")
            .get()

            .addOnSuccessListener { querySnapshot ->

                if (
                    !isAdded ||
                    _binding == null
                ) {
                    return@addOnSuccessListener
                }


                val currentContext =
                    context
                        ?: return@addOnSuccessListener


                if (!querySnapshot.isEmpty) {

                    val plansArray =
                        JSONArray()


                    for (
                    doc in
                    querySnapshot.documents
                    ) {

                        val planJsonStr =
                            doc.getString(
                                "planJson"
                            )


                        if (
                            !planJsonStr
                                .isNullOrEmpty()
                        ) {

                            try {

                                plansArray.put(
                                    JSONObject(
                                        planJsonStr
                                    )
                                )

                            } catch (e: Exception) {

                                e.printStackTrace()
                            }
                        }
                    }


                    val prefs =
                        currentContext
                            .getSharedPreferences(
                                "tri_force_custom_weekly_plan",
                                Context.MODE_PRIVATE
                            )


                    prefs.edit()
                        .putString(
                            "all_saved_plans_json",
                            plansArray.toString()
                        )
                        .apply()


                    loadSavedPlansFromLocal()
                }
            }
    }


    private fun loadSavedPlansFromLocal() {

        val ctx =
            context ?: return


        if (
            !isAdded ||
            _binding == null
        ) {
            return
        }


        val prefs =
            ctx.getSharedPreferences(
                "tri_force_custom_weekly_plan",
                Context.MODE_PRIVATE
            )


        val allPlansStr =
            prefs.getString(
                "all_saved_plans_json",
                "[]"
            ) ?: "[]"


        val activePlanStr =
            prefs.getString(
                "active_plan_json",
                null
            )


        val plansArray =
            try {
                val parsed = JSONArray(allPlansStr)
                if (parsed.length() == 0) {
                    getDefaultSuggestedPlans()
                } else {
                    parsed
                }
            } catch (_: Exception) {
                getDefaultSuggestedPlans()
            }


        val activePlanName =
            if (
                activePlanStr
                    .isNullOrEmpty()
            ) {

                ""

            } else {

                try {

                    JSONObject(
                        activePlanStr
                    ).optString(
                        "planName",
                        ""
                    )

                } catch (_: Exception) {

                    ""
                }
            }


        renderSavedPlansList(
            plansArray,
            activePlanName
        )
    }

    private fun getDefaultSuggestedPlans(): JSONArray {
        val array = JSONArray()

        // 1. Lộ trình Toàn Thân Tinh Gọn (Full-Body)
        val p1 = JSONObject().apply {
            put("planId", "preset_fullbody")
            put("planName", "Lộ trình Toàn Thân Tinh Gọn (Full-Body)")
            put("createdAt", 1700000000000L)
            put("isPreset", true)
            val days = JSONArray()
            val dayData = listOf(
                Pair("Thứ Hai", "mon") to listOf(
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("squat", "Ngồi Xổm (Squats)", 20),
                    Triple("plank", "Plank Căng Cơ", 45)
                ),
                Pair("Thứ Ba", "tue") to listOf(
                    Triple("splitsquat", "Ngồi Xổm Một Chân (Split Squat)", 15),
                    Triple("situp", "Gập Bụng (Sit-up)", 20),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 30)
                ),
                Pair("Thứ Tư", "wed") to emptyList(),
                Pair("Thứ Năm", "thu") to listOf(
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("squat", "Ngồi Xổm (Squats)", 20),
                    Triple("jumpingjack", "Jumping Jacks", 30),
                    Triple("plank", "Plank Căng Cơ", 45)
                ),
                Pair("Thứ Sáu", "fri") to listOf(
                    Triple("splitsquat", "Ngồi Xổm Một Chân (Split Squat)", 15),
                    Triple("situp", "Gập Bụng (Sit-up)", 20),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 30)
                ),
                Pair("Thứ Bảy", "sat") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 35),
                    Triple("squat", "Ngồi Xổm (Squats)", 20),
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("plank", "Plank Căng Cơ", 50)
                ),
                Pair("Chủ Nhật", "sun") to emptyList()
            )
            for ((dayInfo, exercises) in dayData) {
                val dayObj = JSONObject().apply {
                    put("dayName", dayInfo.first)
                    put("dayKey", dayInfo.second)
                    val exArr = JSONArray()
                    for (ex in exercises) {
                        exArr.put(JSONObject().apply {
                            put("id", ex.first)
                            put("name", ex.second)
                            put("targetCount", ex.third)
                        })
                    }
                    put("exercises", exArr)
                }
                days.put(dayObj)
            }
            put("days", days)
        }
        array.put(p1)

        // 2. Lộ trình Siết Cơ Bụng & Lõi Cốt (Core & Abs)
        val p2 = JSONObject().apply {
            put("planId", "preset_core")
            put("planName", "Lộ trình Siết Bụng & Core (Abs Pro)")
            put("createdAt", 1700000001000L)
            put("isPreset", true)
            val days = JSONArray()
            val dayData = listOf(
                Pair("Thứ Hai", "mon") to listOf(
                    Triple("situp", "Gập Bụng (Sit-up)", 25),
                    Triple("plank", "Plank Căng Cơ", 45),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 30),
                    Triple("pushup", "Hít Đất (Push-up)", 12)
                ),
                Pair("Thứ Ba", "tue") to listOf(
                    Triple("squat", "Ngồi Xổm (Squats)", 20),
                    Triple("jumpingjack", "Jumping Jacks", 30),
                    Triple("plank", "Plank Căng Cơ", 45)
                ),
                Pair("Thứ Tư", "wed") to listOf(
                    Triple("situp", "Gập Bụng (Sit-up)", 25),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 40),
                    Triple("plank", "Plank Căng Cơ", 60)
                ),
                Pair("Thứ Năm", "thu") to emptyList(),
                Pair("Thứ Sáu", "fri") to listOf(
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("situp", "Gập Bụng (Sit-up)", 20),
                    Triple("plank", "Plank Căng Cơ", 45),
                    Triple("jumpingjack", "Jumping Jacks", 30)
                ),
                Pair("Thứ Bảy", "sat") to listOf(
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 35),
                    Triple("situp", "Gập Bụng (Sit-up)", 25),
                    Triple("squat", "Ngồi Xổm (Squats)", 20),
                    Triple("plank", "Plank Căng Cơ", 50)
                ),
                Pair("Chủ Nhật", "sun") to emptyList()
            )
            for ((dayInfo, exercises) in dayData) {
                val dayObj = JSONObject().apply {
                    put("dayName", dayInfo.first)
                    put("dayKey", dayInfo.second)
                    val exArr = JSONArray()
                    for (ex in exercises) {
                        exArr.put(JSONObject().apply {
                            put("id", ex.first)
                            put("name", ex.second)
                            put("targetCount", ex.third)
                        })
                    }
                    put("exercises", exArr)
                }
                days.put(dayObj)
            }
            put("days", days)
        }
        array.put(p2)

        // 3. Lộ trình Đốt Mỡ Thần Tốc (HIIT Fat Burn)
        val p3 = JSONObject().apply {
            put("planId", "preset_hiit")
            put("planName", "Lộ trình Đốt Mỡ Thần Tốc (HIIT Fat Burn)")
            put("createdAt", 1700000002000L)
            put("isPreset", true)
            val days = JSONArray()
            val dayData = listOf(
                Pair("Thứ Hai", "mon") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 35),
                    Triple("squat", "Ngồi Xổm (Squats)", 25),
                    Triple("situp", "Gập Bụng (Sit-up)", 20),
                    Triple("plank", "Plank Căng Cơ", 45)
                ),
                Pair("Thứ Ba", "tue") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 35),
                    Triple("splitsquat", "Ngồi Xổm Một Chân (Split Squat)", 15),
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 35)
                ),
                Pair("Thứ Tư", "wed") to emptyList(),
                Pair("Thứ Năm", "thu") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 40),
                    Triple("squat", "Ngồi Xổm (Squats)", 25),
                    Triple("splitsquat", "Ngồi Xổm Một Chân (Split Squat)", 15),
                    Triple("plank", "Plank Căng Cơ", 50)
                ),
                Pair("Thứ Sáu", "fri") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 35),
                    Triple("situp", "Gập Bụng (Sit-up)", 25),
                    Triple("pushup", "Hít Đất (Push-up)", 15),
                    Triple("sideplank", "Plank Nghiêng (Side Plank)", 35)
                ),
                Pair("Thứ Bảy", "sat") to listOf(
                    Triple("jumpingjack", "Jumping Jacks", 40),
                    Triple("squat", "Ngồi Xổm (Squats)", 25),
                    Triple("situp", "Gập Bụng (Sit-up)", 20),
                    Triple("plank", "Plank Căng Cơ", 45)
                ),
                Pair("Chủ Nhật", "sun") to emptyList()
            )
            for ((dayInfo, exercises) in dayData) {
                val dayObj = JSONObject().apply {
                    put("dayName", dayInfo.first)
                    put("dayKey", dayInfo.second)
                    val exArr = JSONArray()
                    for (ex in exercises) {
                        exArr.put(JSONObject().apply {
                            put("id", ex.first)
                            put("name", ex.second)
                            put("targetCount", ex.third)
                        })
                    }
                    put("exercises", exArr)
                }
                days.put(dayObj)
            }
            put("days", days)
        }
        array.put(p3)

        return array
    }


    // =============================================================
    // SAVED PLAN LIST
    // =============================================================

    private fun renderSavedPlansList(
        plansArray: JSONArray,
        currentActivePlanName: String
    ) {

        val ctx =
            context ?: return


        if (
            !isAdded ||
            _binding == null
        ) {
            return
        }


        if (
            plansArray.length() == 0
        ) {

            binding.tvSavedPlansHeader.visibility =
                View.GONE

            binding.layoutSavedPlansList.visibility =
                View.GONE

            return
        }


        val hasCustom = (0 until plansArray.length()).any {
            !plansArray.getJSONObject(it).optBoolean("isPreset", false)
        }
        binding.tvSavedPlansHeader.text = if (hasCustom) {
            "LỊCH TẬP ĐÃ LƯU & GỢI Ý"
        } else {
            "GỢI Ý LỘ TRÌNH TẬP LUYỆN"
        }
        binding.tvSavedPlansHeader.visibility =
            View.VISIBLE


        binding.layoutSavedPlansList.visibility =
            View.VISIBLE


        binding.layoutSavedPlansList
            .removeAllViews()


        for (
        i in
        0 until plansArray.length()
        ) {

            val planObj =
                plansArray
                    .getJSONObject(i)


            val planId =
                planObj.optString(
                    "planId",
                    ""
                )


            val planName =
                planObj.optString(
                    "planName",
                    "Lịch tập ${i + 1}"
                )


            val days =
                planObj.optJSONArray(
                    "days"
                )


            var workoutDaysCount =
                0


            if (days != null) {

                for (
                dayIndex in
                0 until days.length()
                ) {

                    val count =
                        days
                            .getJSONObject(
                                dayIndex
                            )
                            .optJSONArray(
                                "exercises"
                            )
                            ?.length()
                            ?: 0


                    if (count > 0) {

                        workoutDaysCount++
                    }
                }
            }


            val itemBinding =
                ItemSavedPlanCardBinding
                    .inflate(
                        layoutInflater,
                        binding.layoutSavedPlansList,
                        false
                    )


            itemBinding.tvSavedPlanName.text =
                planName


            itemBinding.tvSavedPlanDesc.text =
                "$workoutDaysCount buổi tập/tuần • Lặp lại hàng tuần"


            val isActive =
                currentActivePlanName
                    .isNotEmpty() &&
                        planName ==
                        currentActivePlanName


            if (isActive) {

                itemBinding.tvActiveIndicator.visibility =
                    View.VISIBLE


                itemBinding.btnStartSavedPlan.text =
                    "ĐANG TẬP"


                itemBinding.btnStartSavedPlan
                    .backgroundTintList =
                    ContextCompat.getColorStateList(
                        ctx,
                        R.color.tri_force_success
                    )


                itemBinding.btnStartSavedPlan.isEnabled =
                    false


            } else {

                itemBinding.tvActiveIndicator.visibility =
                    View.GONE


                itemBinding.btnStartSavedPlan.text =
                    "ÁP DỤNG"


                itemBinding.btnStartSavedPlan
                    .backgroundTintList =
                    ContextCompat.getColorStateList(
                        ctx,
                        R.color.tri_force_blue
                    )


                itemBinding.btnStartSavedPlan.isEnabled =
                    true


                itemBinding.btnStartSavedPlan
                    .setOnClickListener {

                        showApplyPlanConfirmationDialog(
                            planObj,
                            planName
                        )
                    }


                itemBinding.cardSavedPlanRoot
                    .setOnClickListener {

                        showApplyPlanConfirmationDialog(
                            planObj,
                            planName
                        )
                    }
            }


            val isPreset = planObj.optBoolean("isPreset", false)
            if (isPreset) {
                itemBinding.btnDeletePlan.visibility = View.GONE
            } else {
                itemBinding.btnDeletePlan.visibility = View.VISIBLE
                itemBinding.btnDeletePlan
                    .setOnClickListener {

                        showDeletePlanConfirmationDialog(
                            planId,
                            planName,
                            i
                        )
                    }
            }


            binding.layoutSavedPlansList
                .addView(
                    itemBinding.root
                )
        }
    }


    // =============================================================
    // DELETE CUSTOM PLAN
    // =============================================================

    private fun showDeletePlanConfirmationDialog(
        planId: String,
        planName: String,
        position: Int
    ) {

        val ctx =
            context ?: return


        AlertDialog.Builder(ctx)
            .setTitle(
                "Xác nhận xóa tiến trình"
            )
            .setMessage(
                "Bạn có chắc chắn muốn xóa tiến trình \"$planName\" không?"
            )
            .setPositiveButton(
                "Xóa"
            ) { _, _ ->

                deletePlanLocallyAndCloud(
                    planId,
                    planName,
                    position
                )
            }
            .setNegativeButton(
                "Hủy",
                null
            )
            .show()
    }


    private fun deletePlanLocallyAndCloud(
        planId: String,
        planName: String,
        position: Int
    ) {

        val ctx =
            context ?: return


        val prefs =
            ctx.getSharedPreferences(
                "tri_force_custom_weekly_plan",
                Context.MODE_PRIVATE
            )


        val allPlansStr =
            prefs.getString(
                "all_saved_plans_json",
                "[]"
            ) ?: "[]"


        try {

            val plansArray =
                JSONArray(
                    allPlansStr
                )


            val updatedArray =
                JSONArray()


            for (
            i in
            0 until plansArray.length()
            ) {

                val obj =
                    plansArray
                        .getJSONObject(i)


                val id =
                    obj.optString(
                        "planId",
                        ""
                    )


                val name =
                    obj.optString(
                        "planName",
                        ""
                    )


                val isTarget =
                    if (
                        planId.isNotEmpty()
                    ) {

                        id == planId

                    } else {

                        name == planName &&
                                i == position
                    }


                if (!isTarget) {

                    updatedArray.put(
                        obj
                    )
                }
            }


            val activeStr =
                prefs.getString(
                    "active_plan_json",
                    null
                )


            var clearActive =
                false


            if (activeStr != null) {

                val activeObj =
                    JSONObject(
                        activeStr
                    )


                val activeId =
                    activeObj.optString(
                        "planId",
                        ""
                    )


                val activeName =
                    activeObj.optString(
                        "planName",
                        ""
                    )


                clearActive =
                    (
                            planId.isNotEmpty() &&
                                    activeId == planId
                            ) ||
                            (
                                    planId.isEmpty() &&
                                            activeName == planName
                                    )
            }


            val editor =
                prefs.edit()
                    .putString(
                        "all_saved_plans_json",
                        updatedArray.toString()
                    )


            if (clearActive) {

                editor.remove(
                    "active_plan_json"
                )
            }


            editor.apply()


            val uid =
                auth.currentUser?.uid


            if (
                !uid.isNullOrEmpty() &&
                planId.isNotEmpty()
            ) {

                db.collection("users")
                    .document(uid)
                    .collection("custom_plans")
                    .document(planId)
                    .delete()
            }


            Toast.makeText(
                ctx,
                "Đã xóa tiến trình \"$planName\"",
                Toast.LENGTH_SHORT
            ).show()


            loadSavedPlansFromLocal()


        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // =============================================================
    // APPLY CUSTOM PLAN
    // =============================================================

    private fun showApplyPlanConfirmationDialog(
        newPlanObj: JSONObject,
        planName: String
    ) {

        val ctx =
            context ?: return


        AlertDialog.Builder(ctx)
            .setTitle(
                "Xác nhận thay đổi lịch tập"
            )
            .setMessage(
                "Bạn có chắc chắn muốn áp dụng tiến trình \"$planName\" vào lịch tập 30 ngày không?"
            )
            .setPositiveButton(
                "Đồng ý đổi"
            ) { _, _ ->

                applyPlanTo30DaySchedule(
                    newPlanObj,
                    planName
                )
            }
            .setNegativeButton(
                "Hủy",
                null
            )
            .show()
    }


    private fun applyPlanTo30DaySchedule(
        planObj: JSONObject,
        planName: String
    ) {

        val daysArray =
            planObj.optJSONArray(
                "days"
            ) ?: return


        val uid =
            auth.currentUser?.uid
                ?: return


        val ctx =
            context ?: return


        val prefs =
            ctx.getSharedPreferences(
                "tri_force_custom_weekly_plan",
                Context.MODE_PRIVATE
            )


        prefs.edit()
            .putString(
                "active_plan_json",
                planObj.toString()
            )
            .apply()


        val daysMap =
            HashMap<
                    Int,
                    List<UserExercise>
                    >()


        for (
        i in
        0 until daysArray.length()
        ) {

            val dayObj =
                daysArray
                    .getJSONObject(i)


            val dayKey =
                dayObj.optString(
                    "dayKey",
                    "mon"
                )


            val exercisesJson =
                dayObj.optJSONArray(
                    "exercises"
                )


            val userExercises =
                mutableListOf<UserExercise>()


            if (exercisesJson != null) {

                for (
                j in
                0 until exercisesJson.length()
                ) {

                    val exObj =
                        exercisesJson
                            .getJSONObject(j)


                    userExercises.add(

                        UserExercise(

                            exerciseId =
                                exObj.optString(
                                    "id",
                                    "pushup"
                                ),

                            targetCount =
                                exObj.optInt(
                                    "targetCount",
                                    15
                                ),

                            status = 0
                        )
                    )
                }
            }


            val calendarDay =
                when (dayKey) {

                    "mon" ->
                        Calendar.MONDAY

                    "tue" ->
                        Calendar.TUESDAY

                    "wed" ->
                        Calendar.WEDNESDAY

                    "thu" ->
                        Calendar.THURSDAY

                    "fri" ->
                        Calendar.FRIDAY

                    "sat" ->
                        Calendar.SATURDAY

                    "sun" ->
                        Calendar.SUNDAY

                    else ->
                        Calendar.MONDAY
                }


            daysMap[
                calendarDay
            ] = userExercises
        }


        val newCreatedTime =
            System.currentTimeMillis()


        val batch =
            db.batch()


        val workoutsRef =
            db.collection("users")
                .document(uid)
                .collection("workouts")


        val calendar =
            Calendar.getInstance()


        for (
        dayNum in
        1..30
        ) {

            val dayMs =
                newCreatedTime +
                        (
                                dayNum - 1
                                ) *
                        24L *
                        60L *
                        60L *
                        1000L


            calendar.timeInMillis =
                dayMs


            val dayOfWeek =
                calendar.get(
                    Calendar.DAY_OF_WEEK
                )


            val dayExercises =
                daysMap[
                    dayOfWeek
                ] ?: emptyList()


            val workoutDay =
                WorkoutDay(

                    dayIndex =
                        dayNum,

                    isRestDay =
                        dayExercises.isEmpty(),

                    exercises =
                        dayExercises
                )


            batch.set(
                workoutsRef.document(
                    "day_$dayNum"
                ),
                workoutDay
            )
        }


        val userDocRef =
            db.collection("users")
                .document(uid)


        batch.update(
            userDocRef,
            mapOf(
                "createdTime" to
                        newCreatedTime,
                "customPlanName" to
                        planName,
                "activeCustomPlanJson" to
                        planObj.toString()
            )
        )


        batch.commit()

            .addOnSuccessListener {

                if (
                    !isAdded ||
                    _binding == null
                ) {
                    return@addOnSuccessListener
                }


                Toast.makeText(
                    context,
                    "Đã áp dụng \"$planName\" vào lịch tập 30 ngày!",
                    Toast.LENGTH_LONG
                ).show()


                findNavController().navigate(
                    R.id.workout_calendar_fragment
                )
            }

            .addOnFailureListener { e ->

                if (
                    !isAdded ||
                    _binding == null
                ) {
                    return@addOnFailureListener
                }


                Toast.makeText(
                    context,
                    "Lỗi cập nhật lịch tập: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // =============================================================
    // CLEANUP
    // =============================================================

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}