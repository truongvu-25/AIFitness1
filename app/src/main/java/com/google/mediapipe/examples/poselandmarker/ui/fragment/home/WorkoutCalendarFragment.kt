package com.google.mediapipe.examples.poselandmarker.ui.fragment.home

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentWorkoutCalendarBinding
import com.google.mediapipe.examples.poselandmarker.model.Exercise
import com.google.mediapipe.examples.poselandmarker.model.ExerciseDetails
import com.google.mediapipe.examples.poselandmarker.model.UserExercise
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class WorkoutCalendarFragment : Fragment() {

    private var _binding: FragmentWorkoutCalendarBinding? = null
    private val binding get() = _binding!!


    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var uid: String = ""


    private lateinit var daysAdapter: WorkoutDaysAdapter
    private lateinit var exercisesAdapter: ExercisesAdapter


    private var selectedDayIndex: Int = 1
    private var currentDayIndex: Int = 1

    private var createdTime: Long =
        System.currentTimeMillis()

    private var userBmiType: String =
        "CAN DOI"


    private var pendingExercise: Exercise? =
        null


    // Cache exercise master data.
    private val exercisesCache =
        HashMap<String, ExerciseDetails>()


    // 30-day workout memory cache.
    private val workoutDaysMap =
        HashMap<Int, WorkoutDay>()


    // =============================================================
    // CAMERA PERMISSION
    // =============================================================

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->

            if (isGranted) {

                pendingExercise?.let {
                    openWorkoutCamera(it)
                }

            } else {

                Toast.makeText(
                    context,
                    "Ứng dụng cần quyền Camera để hiển thị khung xương tập luyện AI.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // =============================================================
    // LIFECYCLE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        auth =
            FirebaseAuth.getInstance()

        db =
            FirebaseFirestore.getInstance()

        uid =
            auth.currentUser?.uid ?: ""
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentWorkoutCalendarBinding.inflate(
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


        setupRecyclerViews()

        loadMasterExercisesAndInit()


        binding.btnResetPlan
            .setOnClickListener {

                showResetPlanDialog()
            }
    }


    // =============================================================
    // RECYCLER VIEWS
    // =============================================================

    private fun setupRecyclerViews() {

        daysAdapter =
            WorkoutDaysAdapter(
                emptyList(),
                0
            ) { dayNum ->

                selectedDayIndex =
                    dayNum

                onDaySelected(
                    dayNum
                )
            }


        binding.rvDays.adapter =
            daysAdapter


        exercisesAdapter =
            ExercisesAdapter(

                emptyList(),

                onStartClicked = { exercise ->

                    checkCameraPermissionAndStart(
                        exercise
                    )
                },

                onWatchVideoClicked = { exercise ->

                    showVideoTutorialDialog(
                        exercise
                    )
                }
            )


        binding.rvExercises.adapter =
            exercisesAdapter
    }


    // =============================================================
    // MASTER EXERCISE DATA
    // =============================================================

    private fun loadMasterExercisesAndInit() {

        if (!isAdded || _binding == null) {
            return
        }


        binding.calendarProgress.visibility =
            View.VISIBLE


        db.collection("exercises")
            .get()

            .addOnSuccessListener { result ->

                if (!isAdded || _binding == null) {
                    return@addOnSuccessListener
                }


                exercisesCache.clear()


                for (document in result) {

                    val details =
                        document.toObject(
                            ExerciseDetails::class.java
                        )


                    exercisesCache[details.id] =
                        details
                }


                loadUserProfileAndPlan()
            }

            .addOnFailureListener { e ->

                if (!isAdded || _binding == null) {
                    return@addOnFailureListener
                }


                binding.calendarProgress.visibility =
                    View.GONE


                context?.let {

                    Toast.makeText(
                        it,
                        "Lỗi tải kho bài tập: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }


                loadUserProfileAndPlan()
            }
    }


    // =============================================================
    // PROFILE + ACTIVE PLAN
    // =============================================================

    private fun loadUserProfileAndPlan() {

        if (uid.isEmpty()) {
            return
        }


        if (!isAdded || _binding == null) {
            return
        }


        binding.calendarProgress.visibility =
            View.VISIBLE


        db.collection("users")
            .document(uid)
            .get()

            .addOnSuccessListener { document ->

                if (!isAdded || _binding == null) {
                    return@addOnSuccessListener
                }


                if (document.exists()) {

                    val profile =
                        document.toObject(
                            UserProfile::class.java
                        )


                    if (profile != null) {

                        // =================================================
                        // BMI UPDATE CHECK
                        // =================================================

                        val lastBmiUpdated =
                            profile.lastBmiUpdatedTime


                        val diffMs =
                            System.currentTimeMillis() -
                                    lastBmiUpdated


                        val sevenDaysMs =
                            7L *
                                    24 *
                                    60 *
                                    60 *
                                    1000


                        if (
                            lastBmiUpdated > 0 &&
                            diffMs >= sevenDaysMs
                        ) {

                            binding.calendarProgress.visibility =
                                View.GONE


                            findNavController().navigate(
                                R.id.action_workout_calendar_to_update_bmi
                            )

                            return@addOnSuccessListener
                        }


                        userBmiType =
                            profile.bmiType


                        createdTime =
                            profile.createdTime


                        // =================================================
                        // PLAN NAME
                        // =================================================

                        val customPlanName =
                            document.getString(
                                "customPlanName"
                            )


                        val planTypeLabel =

                            if (!customPlanName.isNullOrEmpty()) {

                                "Lộ trình: $customPlanName"

                            } else {

                                when (userBmiType) {

                                    "GAY" ->
                                        "Lộ trình: Tăng Cân & Tăng Cơ"

                                    "CAN DOI" ->
                                        "Lộ trình: Săn Chắc Thể Hình"

                                    else ->
                                        "Lộ trình: Đốt Mỡ & Giảm Cân"
                                }
                            }


                        binding.tvPlanType.text =
                            planTypeLabel


                        // =================================================
                        // CURRENT DAY INDEX
                        // =================================================

                        val diffPlanMs =
                            System.currentTimeMillis() -
                                    createdTime


                        val calculatedDayIdx =
                            (
                                    diffPlanMs /
                                            (
                                                    24 *
                                                            60 *
                                                            60 *
                                                            1000
                                                    )
                                    ).toInt() + 1


                        currentDayIndex =
                            calculatedDayIdx
                                .coerceIn(
                                    1,
                                    30
                                )


                        selectedDayIndex =
                            currentDayIndex


                        loadAll30DaysWorkoutData()
                    }


                } else {

                    binding.calendarProgress.visibility =
                        View.GONE


                    context?.let {

                        Toast.makeText(
                            it,
                            "Không tìm thấy hồ sơ người dùng.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            .addOnFailureListener { e ->

                if (!isAdded || _binding == null) {
                    return@addOnFailureListener
                }


                binding.calendarProgress.visibility =
                    View.GONE


                context?.let {

                    Toast.makeText(
                        it,
                        "Lỗi kết nối database: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }


    // =============================================================
    // LOAD ALL 30 DAYS
    // =============================================================

    private fun loadAll30DaysWorkoutData() {

        if (uid.isEmpty()) {
            return
        }


        if (!isAdded || _binding == null) {
            return
        }


        db.collection("users")
            .document(uid)
            .collection("workouts")
            .get()

            .addOnSuccessListener { snapshot ->

                if (!isAdded || _binding == null) {
                    return@addOnSuccessListener
                }


                binding.calendarProgress.visibility =
                    View.GONE


                workoutDaysMap.clear()


                for (doc in snapshot) {

                    val workoutDay =
                        doc.toObject(
                            WorkoutDay::class.java
                        )


                    workoutDaysMap[
                        workoutDay.dayIndex
                    ] = workoutDay
                }


                val dayUiList =
                    ArrayList<DayItemUI>()


                val dateFormat =
                    SimpleDateFormat(
                        "dd/MM",
                        Locale.getDefault()
                    )


                for (dayNum in 1..30) {

                    val dayMs =
                        createdTime +
                                (dayNum - 1) *
                                24L *
                                60 *
                                60 *
                                1000


                    val dateFormatted =
                        dateFormat.format(
                            Date(dayMs)
                        )


                    val workoutDay =
                        workoutDaysMap[
                            dayNum
                        ]


                    val isRestDay =
                        workoutDay?.isRestDay
                            ?: isRestDayForBmi(
                                userBmiType,
                                dayNum
                            )


                    val exercises =
                        workoutDay?.exercises
                            ?: emptyList()


                    val completedCount =
                        exercises.count {
                            it.status == 1
                        }


                    dayUiList.add(

                        DayItemUI(

                            dayIndex =
                                dayNum,

                            dateFormatted =
                                dateFormatted,

                            isRestDay =
                                isRestDay,

                            isCurrentDay =
                                dayNum ==
                                        currentDayIndex,

                            isPastDay =
                                dayNum <
                                        currentDayIndex,

                            isFutureDay =
                                dayNum >
                                        currentDayIndex,

                            totalExercises =
                                exercises.size,

                            completedExercises =
                                completedCount
                        )
                    )
                }


                daysAdapter.updateData(
                    dayUiList,
                    selectedDayIndex - 1
                )


                binding.rvDays.post {

                    if (_binding != null) {

                        binding.rvDays.scrollToPosition(
                            (
                                    selectedDayIndex -
                                            1
                                    ).coerceAtLeast(0)
                        )
                    }
                }


                onDaySelected(
                    selectedDayIndex
                )
            }

            .addOnFailureListener { e ->

                if (!isAdded || _binding == null) {
                    return@addOnFailureListener
                }


                binding.calendarProgress.visibility =
                    View.GONE


                context?.let {

                    Toast.makeText(
                        it,
                        "Lỗi tải dữ liệu 30 ngày: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }


    // =============================================================
    // DAY SELECTION
    // =============================================================

    private fun onDaySelected(
        dayNum: Int
    ) {

        val workoutDay =
            workoutDaysMap[
                dayNum
            ]


        val isRestDay =
            workoutDay?.isRestDay
                ?: isRestDayForBmi(
                    userBmiType,
                    dayNum
                )


        val exercises =
            workoutDay?.exercises
                ?: emptyList()


        val completedCount =
            exercises.count {
                it.status == 1
            }


        val dayMs =
            createdTime +
                    (dayNum - 1) *
                    24L *
                    60 *
                    60 *
                    1000


        val dateFormatted =
            SimpleDateFormat(
                "dd/MM",
                Locale.getDefault()
            ).format(
                Date(dayMs)
            )


        binding.tvSelectedDay.text =
            "Bài tập Ngày $dayNum • $dateFormatted"


        val ctx =
            context ?: return


        // =========================================================
        // REST DAY
        // =========================================================

        if (isRestDay) {

            binding.cardStatusNotice.visibility =
                View.VISIBLE


            binding.cardStatusNotice
                .setCardBackgroundColor(
                    ContextCompat.getColor(
                        ctx,
                        R.color.tri_force_warning_bg
                    )
                )


            binding.cardStatusNotice.strokeColor =
                ContextCompat.getColor(
                    ctx,
                    R.color.tri_force_warning_border
                )


            binding.ivNoticeIcon.setImageResource(
                android.R.drawable.ic_dialog_info
            )


            binding.ivNoticeIcon.imageTintList =
                ContextCompat.getColorStateList(
                    ctx,
                    R.color.tri_force_warning
                )


            binding.tvNoticeMessage.text =
                "Ngày nghỉ ngơi • Hãy thả lỏng cơ bắp, ăn uống đủ chất và ngủ đủ giấc để phục hồi."


            binding.tvNoticeMessage.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.tri_force_warning
                )
            )


            exercisesAdapter.updateData(
                emptyList()
            )


        } else {

            // =====================================================
            // MISSED DAY
            // =====================================================

            if (
                dayNum < currentDayIndex &&
                completedCount == 0
            ) {

                binding.cardStatusNotice.visibility =
                    View.VISIBLE


                binding.cardStatusNotice
                    .setCardBackgroundColor(
                        ContextCompat.getColor(
                            ctx,
                            R.color.tri_force_error_bg
                        )
                    )


                binding.cardStatusNotice.strokeColor =
                    ContextCompat.getColor(
                        ctx,
                        R.color.tri_force_error_border
                    )


                binding.ivNoticeIcon.setImageResource(
                    android.R.drawable.ic_dialog_alert
                )


                binding.ivNoticeIcon.imageTintList =
                    ContextCompat.getColorStateList(
                        ctx,
                        R.color.tri_force_error
                    )


                binding.tvNoticeMessage.text =
                    "Bạn đã bỏ qua buổi tập Ngày $dayNum • Hãy quay lại nhịp luyện tập và tiếp tục duy trì thói quen."


                binding.tvNoticeMessage.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        R.color.tri_force_error
                    )
                )


            } else {

                binding.cardStatusNotice.visibility =
                    View.GONE
            }


            // =====================================================
            // BUILD EXERCISE UI
            // =====================================================

            val displayExercises =
                exercises.map { userEx ->

                    val details =
                        exercisesCache[
                            userEx.exerciseId
                        ]


                    Exercise(

                        id =
                            userEx.exerciseId,

                        name =
                            details?.name
                                ?: userEx.exerciseId,

                        targetCount =
                            userEx.targetCount,

                        status =
                            userEx.status,

                        description =
                            details?.description
                                ?: "",

                        videoUrl =
                            details?.videoUrl
                                ?: "",

                        isTimed =
                            details?.isTimed
                                ?: false,

                        unit =
                            details?.unit
                                ?: "lần"
                    )
                }


            exercisesAdapter.updateData(
                displayExercises
            )
        }
    }


    // =============================================================
    // RESET PLAN
    // =============================================================

    private fun showResetPlanDialog() {

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Tạo lộ trình mới"
            )

            .setMessage(
                "Bạn có chắc chắn muốn xóa lộ trình hiện tại và tạo lộ trình 30 ngày mới dựa trên thông số cơ thể hiện tại không?"
            )

            .setPositiveButton(
                "Đồng ý"
            ) { dialog, _ ->

                dialog.dismiss()

                resetWorkoutPlan()
            }

            .setNegativeButton(
                "Hủy"
            ) { dialog, _ ->

                dialog.dismiss()
            }

            .show()
    }


    // =============================================================
    // DEFAULT REST DAYS
    // =============================================================

    private fun isRestDayForBmi(
        bmiType: String,
        dayIndex: Int
    ): Boolean {

        return when (bmiType) {

            "GAY" ->
                dayIndex in listOf(
                    4, 7, 11, 14,
                    18, 21, 25, 28
                )

            "CAN DOI" ->
                dayIndex in listOf(
                    4, 8, 12, 16,
                    20, 24, 28
                )

            else ->
                dayIndex in listOf(
                    5, 10, 15,
                    20, 25, 30
                )
        }
    }


    // =============================================================
    // RESET 30 DAY PLAN
    // =============================================================

    private fun resetWorkoutPlan() {

        if (uid.isEmpty()) {
            return
        }


        if (!isAdded || _binding == null) {
            return
        }


        binding.calendarProgress.visibility =
            View.VISIBLE


        db.collection("users")
            .document(uid)
            .get()

            .addOnSuccessListener { document ->

                if (!isAdded || _binding == null) {
                    return@addOnSuccessListener
                }


                if (document.exists()) {

                    val profile =
                        document.toObject(
                            UserProfile::class.java
                        )


                    if (profile != null) {

                        val batch =
                            db.batch()


                        for (day in 1..30) {

                            val isRestDay =
                                isRestDayForBmi(
                                    profile.bmiType,
                                    day
                                )


                            val exercises =

                                if (isRestDay) {

                                    emptyList()

                                } else {

                                    getExercisesForBmiAndDay(
                                        profile.bmiType,
                                        day
                                    )
                                }


                            val workoutDay =
                                WorkoutDay(

                                    dayIndex =
                                        day,

                                    exercises =
                                        exercises,

                                    isRestDay =
                                        isRestDay
                                )


                            val docRef =
                                db.collection("users")
                                    .document(uid)
                                    .collection("workouts")
                                    .document(
                                        "day_$day"
                                    )


                            batch.set(
                                docRef,
                                workoutDay
                            )
                        }


                        batch.commit()

                            .addOnSuccessListener {

                                if (
                                    !isAdded ||
                                    _binding == null
                                ) {
                                    return@addOnSuccessListener
                                }


                                val updatedProfile =
                                    profile.copy(
                                        createdTime =
                                            System.currentTimeMillis()
                                    )


                                db.collection("users")
                                    .document(uid)
                                    .set(updatedProfile)

                                    .addOnSuccessListener {

                                        if (
                                            !isAdded ||
                                            _binding == null
                                        ) {
                                            return@addOnSuccessListener
                                        }


                                        binding.calendarProgress.visibility =
                                            View.GONE


                                        context?.let {

                                            Toast.makeText(
                                                it,
                                                "Đã tạo lộ trình 30 ngày tập luyện mới!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }


                                        loadUserProfileAndPlan()
                                    }
                            }

                            .addOnFailureListener { e ->

                                if (
                                    !isAdded ||
                                    _binding == null
                                ) {
                                    return@addOnFailureListener
                                }


                                binding.calendarProgress.visibility =
                                    View.GONE


                                context?.let {

                                    Toast.makeText(
                                        it,
                                        "Lỗi tạo lại lộ trình: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                }
            }

            .addOnFailureListener { e ->

                if (!isAdded || _binding == null) {
                    return@addOnFailureListener
                }


                binding.calendarProgress.visibility =
                    View.GONE


                context?.let {

                    Toast.makeText(
                        it,
                        "Lỗi tải thông tin: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }


    // =============================================================
    // DEFAULT EXERCISES BY BMI + DAY
    // =============================================================

    private fun getExercisesForBmiAndDay(
        bmiType: String,
        dayIndex: Int
    ): List<UserExercise> {

        val weekMultiplier =
            when {

                dayIndex <= 7 ->
                    1.0

                dayIndex <= 14 ->
                    1.2

                dayIndex <= 21 ->
                    1.4

                else ->
                    1.6
            }


        return when (bmiType) {

            // =====================================================
            // GAIN WEIGHT / MUSCLE
            // =====================================================

            "GAY" -> {

                if (dayIndex % 2 != 0) {

                    listOf(

                        UserExercise(
                            "pushup",
                            (10 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "squat",
                            (12 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "plank",
                            (30 * weekMultiplier).toInt()
                        )
                    )

                } else {

                    listOf(

                        UserExercise(
                            "splitsquat",
                            (10 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "situp",
                            (12 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "sideplank",
                            (30 * weekMultiplier).toInt()
                        )
                    )
                }
            }


            // =====================================================
            // BALANCED
            // =====================================================

            "CAN DOI" -> {

                if (dayIndex % 2 != 0) {

                    listOf(

                        UserExercise(
                            "pushup",
                            (15 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "squat",
                            (15 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "jumpingjack",
                            (25 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "plank",
                            (40 * weekMultiplier).toInt()
                        )
                    )

                } else {

                    listOf(

                        UserExercise(
                            "splitsquat",
                            (12 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "situp",
                            (15 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "sideplank",
                            (40 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "jumpingjack",
                            (25 * weekMultiplier).toInt()
                        )
                    )
                }
            }


            // =====================================================
            // WEIGHT LOSS
            // =====================================================

            else -> {

                if (dayIndex % 2 != 0) {

                    listOf(

                        UserExercise(
                            "jumpingjack",
                            (30 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "squat",
                            (20 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "situp",
                            (20 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "plank",
                            (45 * weekMultiplier).toInt()
                        )
                    )

                } else {

                    listOf(

                        UserExercise(
                            "jumpingjack",
                            (30 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "splitsquat",
                            (15 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "sideplank",
                            (45 * weekMultiplier).toInt()
                        ),

                        UserExercise(
                            "pushup",
                            (12 * weekMultiplier).toInt()
                        )
                    )
                }
            }
        }
    }


    // =============================================================
    // VIDEO URI
    // =============================================================

    private fun getMediaUri(
        context: Context,
        videoUrl: String
    ): Uri {

        return if (
            videoUrl.startsWith(
                "asset:///"
            )
        ) {

            val assetPath =
                videoUrl.substringAfter(
                    "asset:///"
                )


            val fileName =
                assetPath.substringAfterLast(
                    "/"
                )


            val cacheFile =
                File(
                    context.cacheDir,
                    fileName
                )


            if (
                !cacheFile.exists() ||
                cacheFile.length() == 0L
            ) {

                try {

                    context.assets
                        .open(assetPath)
                        .use { inputStream ->

                            FileOutputStream(
                                cacheFile
                            ).use { outputStream ->

                                inputStream.copyTo(
                                    outputStream
                                )
                            }
                        }

                } catch (e: Exception) {

                    e.printStackTrace()
                }
            }


            Uri.fromFile(
                cacheFile
            )


        } else {

            Uri.parse(
                videoUrl
            )
        }
    }


    // =============================================================
    // VIDEO TUTORIAL
    // =============================================================

    private fun showVideoTutorialDialog(
        exercise: Exercise
    ) {

        val url =
            exercise.videoUrl


        // =========================================================
        // YOUTUBE
        // =========================================================

        if (
            url.contains("youtube.com") ||
            url.contains("youtu.be")
        ) {

            try {

                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                    )


                startActivity(
                    intent
                )


            } catch (e: Exception) {

                Toast.makeText(
                    context,
                    "Không thể mở ứng dụng YouTube.",
                    Toast.LENGTH_SHORT
                ).show()
            }


            return
        }


        // =========================================================
        // INTERNAL FULLSCREEN PLAYER
        // =========================================================

        val dialog =
            Dialog(
                requireContext(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen
            )


        dialog.setContentView(
            R.layout.dialog_fullscreen_video_player
        )


        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )


        val textureView =
            dialog.findViewById<TextureView>(
                R.id.fullscreenTextureView
            )


        val progressBar =
            dialog.findViewById<ProgressBar>(
                R.id.fullscreenVideoProgress
            )


        val btnClose =
            dialog.findViewById<ImageButton>(
                R.id.btnFullscreenClose
            )


        val tvTitle =
            dialog.findViewById<TextView>(
                R.id.tvFullscreenVideoTitle
            )


        val layoutCenterReplay =
            dialog.findViewById<View>(
                R.id.layoutCenterReplay
            )


        val cardReplayButton =
            dialog.findViewById<View>(
                R.id.cardReplayButton
            )


        tvTitle.text =
            "Hướng dẫn: ${exercise.name}"


        var mediaPlayer: MediaPlayer? =
            MediaPlayer()


        fun playFromStart() {

            try {

                mediaPlayer?.seekTo(0)

                mediaPlayer?.start()

                layoutCenterReplay.visibility =
                    View.GONE

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }


        fun initMediaPlayer(
            surface: Surface
        ) {

            try {

                mediaPlayer?.reset()

                mediaPlayer?.setSurface(
                    surface
                )


                if (
                    url.startsWith(
                        "asset:///"
                    )
                ) {

                    val assetPath =
                        url.substringAfter(
                            "asset:///"
                        )


                    val afd =
                        requireContext()
                            .assets
                            .openFd(
                                assetPath
                            )


                    mediaPlayer?.setDataSource(
                        afd.fileDescriptor,
                        afd.startOffset,
                        afd.length
                    )


                    afd.close()


                } else {

                    mediaPlayer?.setDataSource(
                        requireContext(),
                        Uri.parse(url)
                    )
                }


                mediaPlayer?.setVideoScalingMode(
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                )


                mediaPlayer?.isLooping =
                    false


                mediaPlayer?.setOnPreparedListener { mp ->

                    progressBar.visibility =
                        View.GONE


                    layoutCenterReplay.visibility =
                        View.GONE


                    mp.start()
                }


                mediaPlayer?.setOnCompletionListener {

                    layoutCenterReplay.visibility =
                        View.VISIBLE
                }


                mediaPlayer?.setOnErrorListener { _, _, _ ->

                    progressBar.visibility =
                        View.GONE


                    Toast.makeText(
                        context,
                        "Không thể tải video hướng dẫn.",
                        Toast.LENGTH_SHORT
                    ).show()


                    true
                }


                mediaPlayer?.prepareAsync()


            } catch (e: Exception) {

                progressBar.visibility =
                    View.GONE


                Toast.makeText(
                    context,
                    "Lỗi phát video: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        textureView.surfaceTextureListener =
            object :
                TextureView.SurfaceTextureListener {


                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {

                    val surface =
                        Surface(
                            surfaceTexture
                        )


                    initMediaPlayer(
                        surface
                    )
                }


                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    // No-op
                }


                override fun onSurfaceTextureDestroyed(
                    surface: SurfaceTexture
                ): Boolean {

                    try {

                        mediaPlayer?.stop()

                        mediaPlayer?.release()

                        mediaPlayer =
                            null

                    } catch (e: Exception) {

                        e.printStackTrace()
                    }


                    return true
                }


                override fun onSurfaceTextureUpdated(
                    surface: SurfaceTexture
                ) {
                    // No-op
                }
            }


        cardReplayButton
            .setOnClickListener {

                playFromStart()
            }


        btnClose
            .setOnClickListener {

                dialog.dismiss()
            }


        dialog.setOnDismissListener {

            try {

                mediaPlayer?.stop()

                mediaPlayer?.release()

                mediaPlayer =
                    null

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }


        dialog.show()
    }


    // =============================================================
    // CAMERA
    // =============================================================

    private fun checkCameraPermissionAndStart(
        exercise: Exercise
    ) {

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            openWorkoutCamera(
                exercise
            )


        } else {

            pendingExercise =
                exercise


            requestPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    private fun openWorkoutCamera(
        exercise: Exercise
    ) {

        val bundle =
            Bundle().apply {

                putString(
                    "exerciseId",
                    exercise.id
                )

                putString(
                    "exerciseName",
                    exercise.name
                )

                putInt(
                    "targetCount",
                    exercise.targetCount
                )

                putInt(
                    "dayIndex",
                    selectedDayIndex
                )
            }


        findNavController().navigate(
            R.id.action_workout_calendar_to_camera,
            bundle
        )
    }


    // =============================================================
    // DESTROY
    // =============================================================

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }


    // =============================================================
    // DAY UI MODEL
    // =============================================================

    data class DayItemUI(

        val dayIndex: Int,

        val dateFormatted: String,

        val isRestDay: Boolean,

        val isCurrentDay: Boolean,

        val isPastDay: Boolean,

        val isFutureDay: Boolean,

        val totalExercises: Int,

        val completedExercises: Int
    )


    // =============================================================
    // 30-DAY HORIZONTAL ADAPTER
    //
    // FINAL VISUAL LOGIC:
    //
    // Current   = Performance Blue
    // Completed = Soft Green
    // Partial   = Soft Amber
    // Missed    = Soft Red
    // Rest      = Soft Amber/Gray
    // Future    = White/Silver
    //
    // Không còn dark rainbow cards.
    // =============================================================

    private class WorkoutDaysAdapter(

        private var days:
        List<DayItemUI>,

        private var selectedPosition:
        Int,

        private val onDaySelected:
            (Int) -> Unit

    ) : RecyclerView.Adapter<
            WorkoutDaysAdapter.DayViewHolder
            >() {


        fun updateData(
            newDays: List<DayItemUI>,
            defaultSelectedIdx: Int
        ) {

            days =
                newDays


            selectedPosition =
                defaultSelectedIdx


            notifyDataSetChanged()
        }


        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): DayViewHolder {

            val view =
                LayoutInflater.from(
                    parent.context
                ).inflate(
                    R.layout.item_workout_day,
                    parent,
                    false
                )


            return DayViewHolder(
                view
            )
        }


        override fun onBindViewHolder(
            holder: DayViewHolder,
            position: Int
        ) {

            val item =
                days[position]


            val isSelected =
                position ==
                        selectedPosition


            holder.bind(
                item,
                isSelected
            )


            holder.itemView
                .setOnClickListener {

                    if (
                        position !=
                        selectedPosition
                    ) {

                        val oldPos =
                            selectedPosition


                        selectedPosition =
                            position


                        if (
                            oldPos in
                            days.indices
                        ) {

                            notifyItemChanged(
                                oldPos
                            )
                        }


                        notifyItemChanged(
                            selectedPosition
                        )


                        onDaySelected(
                            item.dayIndex
                        )
                    }
                }
        }


        override fun getItemCount():
                Int = days.size


        class DayViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(
            itemView
        ) {

            private val cardDay:
                    MaterialCardView =
                itemView.findViewById(
                    R.id.cardDay
                )


            private val tvCalendarDate:
                    TextView =
                itemView.findViewById(
                    R.id.tvCalendarDate
                )


            private val tvDayNum:
                    TextView =
                itemView.findViewById(
                    R.id.tvDayNum
                )


            private val tvDayLabel:
                    TextView =
                itemView.findViewById(
                    R.id.tvDayLabel
                )


            private fun color(
                colorRes: Int
            ): Int {

                return ContextCompat.getColor(
                    itemView.context,
                    colorRes
                )
            }


            private fun dpToPx(
                dp: Float
            ): Int {

                return (
                        dp *
                                itemView.resources
                                    .displayMetrics
                                    .density
                        ).toInt()
            }


            fun bind(
                item: DayItemUI,
                isSelected: Boolean
            ) {

                tvCalendarDate.text =
                    item.dateFormatted


                // =================================================
                // CURRENT DAY
                // =================================================

                when {

                    item.isCurrentDay -> {

                        cardDay.setCardBackgroundColor(
                            color(
                                R.color.tri_force_blue
                            )
                        )


                        cardDay.strokeColor =
                            color(
                                R.color.tri_force_blue_dark
                            )


                        tvCalendarDate.setTextColor(
                            color(
                                R.color.tri_force_blue_light
                            )
                        )


                        tvDayNum.text =
                            String.format(
                                Locale.getDefault(),
                                "%02d",
                                item.dayIndex
                            )


                        tvDayNum.setTextColor(
                            color(
                                R.color.tri_force_white
                            )
                        )


                        tvDayLabel.text =
                            "Hôm nay"


                        tvDayLabel.setTextColor(
                            color(
                                R.color.tri_force_text_on_dark
                            )
                        )
                    }


                    // =============================================
                    // REST
                    // =============================================

                    item.isRestDay -> {

                        cardDay.setCardBackgroundColor(
                            color(
                                R.color.tri_force_warning_bg
                            )
                        )


                        cardDay.strokeColor =
                            color(
                                R.color.tri_force_warning_border
                            )


                        tvCalendarDate.setTextColor(
                            color(
                                R.color.tri_force_warning
                            )
                        )


                        tvDayNum.text =
                            "NGHỈ"


                        tvDayNum.setTextColor(
                            color(
                                R.color.tri_force_warning
                            )
                        )


                        tvDayLabel.text =
                            "Phục hồi"


                        tvDayLabel.setTextColor(
                            color(
                                R.color.tri_force_text_secondary
                            )
                        )
                    }


                    // =============================================
                    // PAST DAYS
                    // =============================================

                    item.isPastDay -> {

                        when {

                            // -------------------------------------
                            // COMPLETED
                            // -------------------------------------

                            item.totalExercises > 0 &&
                                    item.completedExercises ==
                                    item.totalExercises -> {

                                cardDay.setCardBackgroundColor(
                                    color(
                                        R.color.tri_force_success_bg
                                    )
                                )


                                cardDay.strokeColor =
                                    color(
                                        R.color.tri_force_success_border
                                    )


                                tvCalendarDate.setTextColor(
                                    color(
                                        R.color.tri_force_success
                                    )
                                )


                                tvDayNum.text =
                                    String.format(
                                        Locale.getDefault(),
                                        "%02d",
                                        item.dayIndex
                                    )


                                tvDayNum.setTextColor(
                                    color(
                                        R.color.tri_force_success
                                    )
                                )


                                tvDayLabel.text =
                                    "Hoàn tất"


                                tvDayLabel.setTextColor(
                                    color(
                                        R.color.tri_force_success
                                    )
                                )
                            }


                            // -------------------------------------
                            // PARTIAL
                            // -------------------------------------

                            item.completedExercises > 0 -> {

                                cardDay.setCardBackgroundColor(
                                    color(
                                        R.color.tri_force_warning_bg
                                    )
                                )


                                cardDay.strokeColor =
                                    color(
                                        R.color.tri_force_warning_border
                                    )


                                tvCalendarDate.setTextColor(
                                    color(
                                        R.color.tri_force_warning
                                    )
                                )


                                tvDayNum.text =
                                    String.format(
                                        Locale.getDefault(),
                                        "%02d",
                                        item.dayIndex
                                    )


                                tvDayNum.setTextColor(
                                    color(
                                        R.color.tri_force_warning
                                    )
                                )


                                tvDayLabel.text =
                                    "Dở dang"


                                tvDayLabel.setTextColor(
                                    color(
                                        R.color.tri_force_warning
                                    )
                                )
                            }


                            // -------------------------------------
                            // MISSED
                            // -------------------------------------

                            else -> {

                                cardDay.setCardBackgroundColor(
                                    color(
                                        R.color.tri_force_error_bg
                                    )
                                )


                                cardDay.strokeColor =
                                    color(
                                        R.color.tri_force_error_border
                                    )


                                tvCalendarDate.setTextColor(
                                    color(
                                        R.color.tri_force_error
                                    )
                                )


                                tvDayNum.text =
                                    String.format(
                                        Locale.getDefault(),
                                        "%02d",
                                        item.dayIndex
                                    )


                                tvDayNum.setTextColor(
                                    color(
                                        R.color.tri_force_error
                                    )
                                )


                                tvDayLabel.text =
                                    "Bỏ qua"


                                tvDayLabel.setTextColor(
                                    color(
                                        R.color.tri_force_error
                                    )
                                )
                            }
                        }
                    }


                    // =============================================
                    // FUTURE
                    // =============================================

                    else -> {

                        cardDay.setCardBackgroundColor(
                            color(
                                R.color.card_background
                            )
                        )


                        cardDay.strokeColor =
                            color(
                                R.color.tri_force_stroke
                            )


                        tvCalendarDate.setTextColor(
                            color(
                                R.color.tri_force_text_tertiary
                            )
                        )


                        tvDayNum.text =
                            String.format(
                                Locale.getDefault(),
                                "%02d",
                                item.dayIndex
                            )


                        tvDayNum.setTextColor(
                            color(
                                R.color.tri_force_text_primary
                            )
                        )


                        tvDayLabel.text =
                            "Ngày"


                        tvDayLabel.setTextColor(
                            color(
                                R.color.tri_force_text_tertiary
                            )
                        )
                    }
                }


                // =================================================
                // SELECTED DAY
                // =================================================

                if (isSelected) {

                    cardDay.strokeColor =
                        color(
                            R.color.tri_force_blue
                        )


                    cardDay.strokeWidth =
                        dpToPx(
                            2f
                        )


                } else {

                    cardDay.strokeWidth =
                        dpToPx(
                            1f
                        )
                }
            }
        }
    }


    // =============================================================
    // EXERCISE ADAPTER
    // =============================================================

    private class ExercisesAdapter(

        private var exercises:
        List<Exercise>,

        private val onStartClicked:
            (Exercise) -> Unit,

        private val onWatchVideoClicked:
            (Exercise) -> Unit

    ) : RecyclerView.Adapter<
            ExercisesAdapter.ExerciseViewHolder
            >() {


        fun updateData(
            newExercises: List<Exercise>
        ) {

            exercises =
                newExercises


            notifyDataSetChanged()
        }


        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ExerciseViewHolder {

            val view =
                LayoutInflater.from(
                    parent.context
                ).inflate(
                    R.layout.item_exercise,
                    parent,
                    false
                )


            return ExerciseViewHolder(
                view
            )
        }


        override fun onBindViewHolder(
            holder: ExerciseViewHolder,
            position: Int
        ) {

            holder.bind(
                exercises[position],
                onStartClicked,
                onWatchVideoClicked
            )
        }


        override fun getItemCount():
                Int = exercises.size


        class ExerciseViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(
            itemView
        ) {


            private val tvName:
                    TextView =
                itemView.findViewById(
                    R.id.tvExerciseName
                )


            private val tvTarget:
                    TextView =
                itemView.findViewById(
                    R.id.tvExerciseTarget
                )


            private val tvDesc:
                    TextView =
                itemView.findViewById(
                    R.id.tvExerciseDesc
                )


            private val tvStatus:
                    TextView =
                itemView.findViewById(
                    R.id.tvStatus
                )


            private val btnWatchVideo:
                    MaterialButton =
                itemView.findViewById(
                    R.id.btnWatchVideo
                )


            private val btnStart:
                    MaterialButton =
                itemView.findViewById(
                    R.id.btnStartExercise
                )


            fun bind(
                exercise: Exercise,
                onStartClicked:
                    (Exercise) -> Unit,
                onWatchVideoClicked:
                    (Exercise) -> Unit
            ) {

                val ctx =
                    itemView.context


                tvName.text =
                    exercise.name


                tvTarget.text =
                    "Mục tiêu: ${exercise.targetCount} ${exercise.unit}"


                tvDesc.text =
                    exercise.description


                // =================================================
                // COMPLETED
                // =================================================

                if (exercise.status == 1) {

                    tvStatus.visibility =
                        View.VISIBLE


                    btnStart.text =
                        "TẬP LẠI"


                    btnStart.backgroundTintList =
                        ContextCompat.getColorStateList(
                            ctx,
                            R.color.tri_force_surface_muted
                        )


                    btnStart.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            R.color.tri_force_text_primary
                        )
                    )


                    // =================================================
                    // PENDING
                    // =================================================

                } else {

                    tvStatus.visibility =
                        View.GONE


                    btnStart.text =
                        "BẮT ĐẦU"


                    btnStart.backgroundTintList =
                        ContextCompat.getColorStateList(
                            ctx,
                            R.color.tri_force_blue
                        )


                    btnStart.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            R.color.tri_force_text_on_primary
                        )
                    )
                }


                btnWatchVideo
                    .setOnClickListener {

                        onWatchVideoClicked(
                            exercise
                        )
                    }


                btnStart
                    .setOnClickListener {

                        onStartClicked(
                            exercise
                        )
                    }
            }
        }
    }
}