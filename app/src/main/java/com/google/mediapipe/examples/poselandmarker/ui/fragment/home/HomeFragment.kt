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
import com.google.mediapipe.examples.poselandmarker.model.ExerciseCatalog
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

    private data class PresetPlan(
        val id: String,
        val nameRes: Int,
        val createdAt: Long,
        val schedule: List<Pair<String, List<Pair<String, Int>>>>
    )

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
                Locale.getDefault()
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
                        ?: getString(R.string.home_greeting_default)


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
                        getString(R.string.home_plan_day_format, currentDayIndex)


                    binding.tvHomePlanProgress.text =
                        getString(R.string.home_percent_format, progress)


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
                ?: getString(R.string.home_greeting_default)


        binding.tvHomeGreetingName.text =
            fallbackName


        binding.cardActivePlan.visibility =
            View.GONE


        binding.cardHomeEmptyPrompt.visibility =
            View.VISIBLE


        binding.tvHomeTodayExercises.text =
            getString(R.string.home_count_placeholder)
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
                getString(R.string.home_plan_default_underweight)

            "CAN DOI" ->
                getString(R.string.home_plan_default_balanced)

            else ->
                getString(R.string.home_plan_default_overweight)
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
                        getString(R.string.home_count_placeholder)

                    return@addOnSuccessListener
                }


                val workoutDay =
                    document.toObject(
                        WorkoutDay::class.java
                    )


                if (workoutDay == null) {

                    binding.tvHomeTodayExercises.text =
                        getString(R.string.home_count_placeholder)

                    return@addOnSuccessListener
                }


                if (workoutDay.isRestDay) {

                    binding.tvHomeTodayExercises.text =
                        getString(R.string.home_plan_rest)

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
                    getString(R.string.home_completed_count_format, completed, total)
            }

            .addOnFailureListener {

                if (_binding != null) {

                    binding.tvHomeTodayExercises.text =
                        getString(R.string.home_count_placeholder)
                }
            }
    }


    // =============================================================
    // CUSTOM PLAN CLOUD SYNC
    // KEEP EXISTING FIREBASE STRUCTURE
    // =============================================================

    private fun syncPlansFromCloudAndDisplay() {

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
                        getDefaultSuggestedPlans()


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


        val activePlanKey =
            if (
                activePlanStr
                    .isNullOrEmpty()
            ) {

                ""

            } else {

                try {

                    val activePlan = JSONObject(activePlanStr)
                    activePlan.optString("planId").takeIf { it.isNotEmpty() }
                        ?: activePlan.optString("planName", "")

                } catch (_: Exception) {

                    ""
                }
            }


        renderSavedPlansList(
            plansArray,
            activePlanKey
        )
    }

    private fun getDefaultSuggestedPlans(): JSONArray {
        val plans = listOf(
            PresetPlan(
                "preset_fullbody",
                R.string.preset_fullbody_name,
                1700000000000L,
                listOf(
                    "mon" to listOf("pushup" to 15, "squat" to 20, "plank" to 45),
                    "tue" to listOf("splitsquat" to 15, "situp" to 20, "sideplank" to 30),
                    "wed" to emptyList(),
                    "thu" to listOf("pushup" to 15, "squat" to 20, "jumpingjack" to 30, "plank" to 45),
                    "fri" to listOf("splitsquat" to 15, "situp" to 20, "sideplank" to 30),
                    "sat" to listOf("jumpingjack" to 35, "squat" to 20, "pushup" to 15, "plank" to 50),
                    "sun" to emptyList()
                )
            ),
            PresetPlan(
                "preset_core",
                R.string.preset_core_name,
                1700000001000L,
                listOf(
                    "mon" to listOf("situp" to 25, "plank" to 45, "sideplank" to 30, "pushup" to 12),
                    "tue" to listOf("squat" to 20, "jumpingjack" to 30, "plank" to 45),
                    "wed" to listOf("situp" to 25, "sideplank" to 40, "plank" to 60),
                    "thu" to emptyList(),
                    "fri" to listOf("pushup" to 15, "situp" to 20, "plank" to 45, "jumpingjack" to 30),
                    "sat" to listOf("sideplank" to 35, "situp" to 25, "squat" to 20, "plank" to 50),
                    "sun" to emptyList()
                )
            ),
            PresetPlan(
                "preset_hiit",
                R.string.preset_hiit_name,
                1700000002000L,
                listOf(
                    "mon" to listOf("jumpingjack" to 35, "squat" to 25, "situp" to 20, "plank" to 45),
                    "tue" to listOf("jumpingjack" to 35, "splitsquat" to 15, "pushup" to 15, "sideplank" to 35),
                    "wed" to emptyList(),
                    "thu" to listOf("jumpingjack" to 40, "squat" to 25, "splitsquat" to 15, "plank" to 50),
                    "fri" to listOf("jumpingjack" to 35, "situp" to 25, "pushup" to 15, "sideplank" to 35),
                    "sat" to listOf("jumpingjack" to 40, "squat" to 25, "situp" to 20, "plank" to 45),
                    "sun" to emptyList()
                )
            )
        )
        val exercisesById = ExerciseCatalog.all(requireContext()).associateBy { it.id }

        return JSONArray().apply {
            plans.forEach { plan ->
                put(JSONObject().apply {
                    put("planId", plan.id)
                    put("planName", getString(plan.nameRes))
                    put("createdAt", plan.createdAt)
                    put("isPreset", true)
                    put("days", JSONArray().apply {
                        plan.schedule.forEach { (dayKey, exercises) ->
                            put(JSONObject().apply {
                                put("dayName", getString(weekdayNameRes(dayKey)))
                                put("dayKey", dayKey)
                                put("exercises", JSONArray().apply {
                                    exercises.forEach { (exerciseId, targetCount) ->
                                        put(JSONObject().apply {
                                            put("id", exerciseId)
                                            put("name", exercisesById[exerciseId]?.name ?: exerciseId)
                                            put("targetCount", targetCount)
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        }
    }

    private fun weekdayNameRes(dayKey: String): Int = when (dayKey) {
        "mon" -> R.string.weekday_monday
        "tue" -> R.string.weekday_tuesday
        "wed" -> R.string.weekday_wednesday
        "thu" -> R.string.weekday_thursday
        "fri" -> R.string.weekday_friday
        "sat" -> R.string.weekday_saturday
        else -> R.string.weekday_sunday
    }


    // =============================================================
    // SAVED PLAN LIST
    // =============================================================

    private fun renderSavedPlansList(
        plansArray: JSONArray,
        currentActivePlanKey: String
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
            getString(R.string.home_saved_and_suggested)
        } else {
            getString(R.string.home_suggested_plans)
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
                    getString(R.string.home_plan_fallback_format, i + 1)
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
                getString(R.string.home_workouts_per_week, workoutDaysCount)


            val isActive =
                currentActivePlanKey
                    .isNotEmpty() &&
                        (planId.takeIf { it.isNotEmpty() } ?: planName) ==
                        currentActivePlanKey


            if (isActive) {

                itemBinding.tvActiveIndicator.visibility =
                    View.VISIBLE


                itemBinding.btnStartSavedPlan.text =
                    getString(R.string.home_plan_active)


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
                    getString(R.string.action_apply)


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
                R.string.home_delete_plan_title
            )
            .setMessage(
                getString(R.string.home_delete_plan_message, planName)
            )
            .setPositiveButton(
                R.string.action_delete
            ) { _, _ ->

                deletePlanLocallyAndCloud(
                    planId,
                    planName,
                    position
                )
            }
            .setNegativeButton(
                R.string.action_cancel,
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
                getString(R.string.home_plan_deleted, planName),
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
                R.string.home_apply_plan_title
            )
            .setMessage(
                getString(R.string.home_apply_plan_message, planName)
            )
            .setPositiveButton(
                R.string.home_apply_plan_confirm
            ) { _, _ ->

                applyPlanTo30DaySchedule(
                    newPlanObj,
                    planName
                )
            }
            .setNegativeButton(
                R.string.action_cancel,
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
                    getString(R.string.home_plan_applied, planName),
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
                    getString(R.string.home_plan_apply_error, e.localizedMessage.orEmpty()),
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
