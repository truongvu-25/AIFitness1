package com.google.mediapipe.examples.poselandmarker.fragment

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.Exercise
import com.google.mediapipe.examples.poselandmarker.ExerciseDetails
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserProfile
import com.google.mediapipe.examples.poselandmarker.UserExercise
import com.google.mediapipe.examples.poselandmarker.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentWorkoutCalendarBinding
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
    private var createdTime: Long = System.currentTimeMillis()
    private var userBmiType: String = "CAN DOI"

    private var pendingExercise: Exercise? = null

    // Memory cache for static exercise details
    private val exercisesCache = HashMap<String, ExerciseDetails>()
    // Memory list for 30 days of workout data
    private val workoutDaysMap = HashMap<Int, WorkoutDay>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingExercise?.let { openWorkoutCamera(it) }
        } else {
            Toast.makeText(context, "Ứng dụng cần quyền Camera để hiển thị khung xương tập luyện AI.", Toast.LENGTH_LONG).show()
        }
    }

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
        _binding = FragmentWorkoutCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        loadMasterExercisesAndInit()

        binding.btnResetPlan.setOnClickListener {
            showResetPlanDialog()
        }
    }

    private fun setupRecyclerViews() {
        daysAdapter = WorkoutDaysAdapter(emptyList(), 0) { dayNum ->
            selectedDayIndex = dayNum
            onDaySelected(dayNum)
        }
        binding.rvDays.adapter = daysAdapter

        exercisesAdapter = ExercisesAdapter(
            emptyList(),
            onStartClicked = { exercise ->
                checkCameraPermissionAndStart(exercise)
            },
            onWatchVideoClicked = { exercise ->
                showVideoTutorialDialog(exercise)
            }
        )
        binding.rvExercises.adapter = exercisesAdapter
    }

    private fun loadMasterExercisesAndInit() {
        if (!isAdded || _binding == null) return
        binding.calendarProgress.visibility = View.VISIBLE
        db.collection("exercises").get()
            .addOnSuccessListener { result ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                exercisesCache.clear()
                for (document in result) {
                    val details = document.toObject(ExerciseDetails::class.java)
                    exercisesCache[details.id] = details
                }
                loadUserProfileAndPlan()
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.calendarProgress.visibility = View.GONE
                context?.let { Toast.makeText(it, "Lỗi tải kho bài tập: ${e.message}", Toast.LENGTH_SHORT).show() }
                loadUserProfileAndPlan()
            }
    }

    private fun loadUserProfileAndPlan() {
        if (uid.isEmpty()) return
        if (!isAdded || _binding == null) return
        
        binding.calendarProgress.visibility = View.VISIBLE
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        val lastBmiUpdated = profile.lastBmiUpdatedTime
                        val diffMs = System.currentTimeMillis() - lastBmiUpdated
                        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                        if (lastBmiUpdated > 0 && diffMs >= sevenDaysMs) {
                            binding.calendarProgress.visibility = View.GONE
                            findNavController().navigate(R.id.action_workout_calendar_to_update_bmi)
                            return@addOnSuccessListener
                        }

                        userBmiType = profile.bmiType
                        createdTime = profile.createdTime

                        val customPlanName = document.getString("customPlanName")
                        val planTypeLabel = if (!customPlanName.isNullOrEmpty()) {
                            "Lộ trình: $customPlanName"
                        } else {
                            when (userBmiType) {
                                "GAY" -> "Lộ trình: Tăng Cân & Tăng Cơ (Gầy)"
                                "CAN DOI" -> "Lộ trình: Săn Chắc Thể Hình (Cân đối)"
                                else -> "Lộ trình: Đốt Mỡ & Giảm Cân (Thừa cân)"
                            }
                        }
                        binding.tvPlanType.text = planTypeLabel

                        val diffPlanMs = System.currentTimeMillis() - createdTime
                        val calculatedDayIdx = (diffPlanMs / (24 * 60 * 60 * 1000)).toInt() + 1
                        currentDayIndex = calculatedDayIdx.coerceIn(1, 30)
                        selectedDayIndex = currentDayIndex

                        loadAll30DaysWorkoutData()
                    }
                } else {
                    binding.calendarProgress.visibility = View.GONE
                    context?.let { Toast.makeText(it, "Không tìm thấy hồ sơ người dùng.", Toast.LENGTH_SHORT).show() }
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.calendarProgress.visibility = View.GONE
                context?.let { Toast.makeText(it, "Lỗi kết nối database: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
    }

    private fun loadAll30DaysWorkoutData() {
        if (uid.isEmpty()) return
        if (!isAdded || _binding == null) return
        
        db.collection("users").document(uid)
            .collection("workouts").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.calendarProgress.visibility = View.GONE
                workoutDaysMap.clear()
                for (doc in snapshot) {
                    val workoutDay = doc.toObject(WorkoutDay::class.java)
                    workoutDaysMap[workoutDay.dayIndex] = workoutDay
                }

                // Build DayItemUI list for adapter
                val dayUiList = ArrayList<DayItemUI>()
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

                for (dayNum in 1..30) {
                    val dayMs = createdTime + (dayNum - 1) * 24L * 60 * 60 * 1000
                    val dateFormatted = dateFormat.format(Date(dayMs))
                    
                    val workoutDay = workoutDaysMap[dayNum]
                    val isRestDay = workoutDay?.isRestDay ?: isRestDayForBmi(userBmiType, dayNum)
                    val exercises = workoutDay?.exercises ?: emptyList()
                    val completedCount = exercises.count { it.status == 1 }

                    dayUiList.add(
                        DayItemUI(
                            dayIndex = dayNum,
                            dateFormatted = dateFormatted,
                            isRestDay = isRestDay,
                            isCurrentDay = (dayNum == currentDayIndex),
                            isPastDay = (dayNum < currentDayIndex),
                            isFutureDay = (dayNum > currentDayIndex),
                            totalExercises = exercises.size,
                            completedExercises = completedCount
                        )
                    )
                }

                daysAdapter.updateData(dayUiList, selectedDayIndex - 1)

                binding.rvDays.post {
                    if (_binding != null) {
                        binding.rvDays.scrollToPosition((selectedDayIndex - 1).coerceAtLeast(0))
                    }
                }

                onDaySelected(selectedDayIndex)
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.calendarProgress.visibility = View.GONE
                context?.let { Toast.makeText(it, "Lỗi tải dữ liệu 30 ngày: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
    }

    private fun onDaySelected(dayNum: Int) {
        val workoutDay = workoutDaysMap[dayNum]
        val isRestDay = workoutDay?.isRestDay ?: isRestDayForBmi(userBmiType, dayNum)
        val exercises = workoutDay?.exercises ?: emptyList()
        val completedCount = exercises.count { it.status == 1 }

        val dayMs = createdTime + (dayNum - 1) * 24L * 60 * 60 * 1000
        val dateFormatted = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(dayMs))

        binding.tvSelectedDay.text = "Bài tập Ngày $dayNum ($dateFormatted)"

        // Update notice banner logic
        if (isRestDay) {
            binding.cardStatusNotice.visibility = View.VISIBLE
            binding.cardStatusNotice.setCardBackgroundColor(Color.parseColor("#FFF8E1"))
            binding.cardStatusNotice.strokeColor = Color.parseColor("#FFB300")
            binding.ivNoticeIcon.setImageResource(android.R.drawable.ic_dialog_info)
            binding.tvNoticeMessage.text = "Hôm nay là Ngày Nghỉ Ngơi ($dateFormatted)! Hãy thả lỏng cơ bắp, ăn uống đủ chất và ngủ đủ 8 tiếng để phục hồi."
            binding.tvNoticeMessage.setTextColor(Color.parseColor("#F57F17"))
            
            exercisesAdapter.updateData(emptyList())
        } else {
            if (dayNum < currentDayIndex && completedCount == 0) {
                binding.cardStatusNotice.visibility = View.VISIBLE
                binding.cardStatusNotice.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.cardStatusNotice.strokeColor = Color.parseColor("#EF5350")
                binding.ivNoticeIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                binding.tvNoticeMessage.text = "Bạn đã bỏ qua lịch tập Ngày $dayNum ($dateFormatted)! Hãy cố gắng duy trì thói quen tập luyện đều đặn."
                binding.tvNoticeMessage.setTextColor(Color.parseColor("#C62828"))
            } else {
                binding.cardStatusNotice.visibility = View.GONE
            }

            val displayExercises = exercises.map { userEx ->
                val details = exercisesCache[userEx.exerciseId]
                Exercise(
                    id = userEx.exerciseId,
                    name = details?.name ?: userEx.exerciseId,
                    targetCount = userEx.targetCount,
                    status = userEx.status,
                    description = details?.description ?: "",
                    videoUrl = details?.videoUrl ?: "",
                    isTimed = details?.isTimed ?: false,
                    unit = details?.unit ?: "lần"
                )
            }
            exercisesAdapter.updateData(displayExercises)
        }
    }

    private fun showResetPlanDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tạo lộ trình mới")
            .setMessage("Bạn có chắc chắn muốn xóa lộ trình hiện tại và tạo lộ trình 30 ngày mới dựa trên thông số cơ thể hiện tại không?")
            .setPositiveButton("Đồng ý") { dialog, _ ->
                dialog.dismiss()
                resetWorkoutPlan()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun isRestDayForBmi(bmiType: String, dayIndex: Int): Boolean {
        return when (bmiType) {
            "GAY" -> dayIndex in listOf(4, 7, 11, 14, 18, 21, 25, 28)
            "CAN DOI" -> dayIndex in listOf(4, 8, 12, 16, 20, 24, 28)
            else -> dayIndex in listOf(5, 10, 15, 20, 25, 30) // THUA CAN
        }
    }

    private fun resetWorkoutPlan() {
        if (uid.isEmpty()) return
        if (!isAdded || _binding == null) return
        binding.calendarProgress.visibility = View.VISIBLE

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        val batch = db.batch()
                        for (day in 1..30) {
                            val isRestDay = isRestDayForBmi(profile.bmiType, day)
                            val exercises = if (isRestDay) emptyList() else getExercisesForBmiAndDay(profile.bmiType, day)
                            val workoutDay = WorkoutDay(dayIndex = day, exercises = exercises, isRestDay = isRestDay)
                            val docRef = db.collection("users").document(uid)
                                .collection("workouts").document("day_$day")
                            batch.set(docRef, workoutDay)
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                if (!isAdded || _binding == null) return@addOnSuccessListener
                                val updatedProfile = profile.copy(createdTime = System.currentTimeMillis())
                                db.collection("users").document(uid).set(updatedProfile)
                                    .addOnSuccessListener {
                                        if (!isAdded || _binding == null) return@addOnSuccessListener
                                        binding.calendarProgress.visibility = View.GONE
                                        context?.let { Toast.makeText(it, "Đã tạo lộ trình 30 ngày tập luyện mới!", Toast.LENGTH_SHORT).show() }
                                        loadUserProfileAndPlan()
                                    }
                            }
                            .addOnFailureListener { e ->
                                if (!isAdded || _binding == null) return@addOnFailureListener
                                binding.calendarProgress.visibility = View.GONE
                                context?.let { Toast.makeText(it, "Lỗi tạo lại lộ trình: ${e.message}", Toast.LENGTH_SHORT).show() }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.calendarProgress.visibility = View.GONE
                context?.let { Toast.makeText(it, "Lỗi tải thông tin: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
    }

    private fun getExercisesForBmiAndDay(bmiType: String, dayIndex: Int): List<UserExercise> {
        val weekMultiplier = when {
            dayIndex <= 7 -> 1.0
            dayIndex <= 14 -> 1.2
            dayIndex <= 21 -> 1.4
            else -> 1.6
        }

        return when (bmiType) {
            "GAY" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", (10 * weekMultiplier).toInt()),
                        UserExercise("squat", (12 * weekMultiplier).toInt()),
                        UserExercise("plank", (30 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", (10 * weekMultiplier).toInt()),
                        UserExercise("situp", (12 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (30 * weekMultiplier).toInt())
                    )
                }
            }
            "CAN DOI" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", (15 * weekMultiplier).toInt()),
                        UserExercise("squat", (15 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt()),
                        UserExercise("plank", (40 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", (12 * weekMultiplier).toInt()),
                        UserExercise("situp", (15 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (40 * weekMultiplier).toInt()),
                        UserExercise("jumpingjack", (25 * weekMultiplier).toInt())
                    )
                }
            }
            else -> { // THUA CAN
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("jumpingjack", (30 * weekMultiplier).toInt()),
                        UserExercise("squat", (20 * weekMultiplier).toInt()),
                        UserExercise("situp", (20 * weekMultiplier).toInt()),
                        UserExercise("plank", (45 * weekMultiplier).toInt())
                    )
                } else {
                    listOf(
                        UserExercise("jumpingjack", (30 * weekMultiplier).toInt()),
                        UserExercise("splitsquat", (15 * weekMultiplier).toInt()),
                        UserExercise("sideplank", (45 * weekMultiplier).toInt()),
                        UserExercise("pushup", (12 * weekMultiplier).toInt())
                    )
                }
            }
        }
    }

    private fun getMediaUri(context: Context, videoUrl: String): Uri {
        return if (videoUrl.startsWith("asset:///")) {
            val assetPath = videoUrl.substringAfter("asset:///")
            val fileName = assetPath.substringAfterLast("/")
            val cacheFile = File(context.cacheDir, fileName)
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                try {
                    context.assets.open(assetPath).use { inputStream ->
                        FileOutputStream(cacheFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Uri.fromFile(cacheFile)
        } else {
            Uri.parse(videoUrl)
        }
    }

    private fun showVideoTutorialDialog(exercise: Exercise) {
        val url = exercise.videoUrl
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể mở ứng dụng YouTube.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_video_player)
        
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val textureView = dialog.findViewById<TextureView>(R.id.fullscreenTextureView)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.fullscreenVideoProgress)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnFullscreenClose)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvFullscreenVideoTitle)
        val layoutCenterReplay = dialog.findViewById<View>(R.id.layoutCenterReplay)
        val cardReplayButton = dialog.findViewById<View>(R.id.cardReplayButton)

        tvTitle.text = "Hướng dẫn: ${exercise.name}"

        var mediaPlayer: MediaPlayer? = MediaPlayer()

        fun playFromStart() {
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                layoutCenterReplay.visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun initMediaPlayer(surface: Surface) {
            try {
                mediaPlayer?.reset()
                mediaPlayer?.setSurface(surface)

                var sourceSet = false
                if (url.startsWith("raw/")) {
                    val rawName = url.substringAfter("raw/")
                    val resId = requireContext().resources.getIdentifier(rawName, "raw", requireContext().packageName)
                    if (resId != 0) {
                        val afd = requireContext().resources.openRawResourceFd(resId)
                        mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        sourceSet = true
                    }
                } else if (url.startsWith("asset:///")) {
                    try {
                        val assetPath = url.substringAfter("asset:///")
                        val afd = requireContext().assets.openFd(assetPath)
                        mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        sourceSet = true
                    } catch (e: Exception) {
                        val rawName = when (exercise.id) {
                            "pushup" -> "hit_dat_tri_force"
                            "situp" -> "gap_bung_tri_force"
                            else -> ""
                        }
                        if (rawName.isNotEmpty()) {
                            val resId = requireContext().resources.getIdentifier(rawName, "raw", requireContext().packageName)
                            if (resId != 0) {
                                val afd = requireContext().resources.openRawResourceFd(resId)
                                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                sourceSet = true
                            }
                        }
                    }
                }

                if (!sourceSet) {
                    mediaPlayer?.setDataSource(requireContext(), Uri.parse(url))
                }

                mediaPlayer?.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                mediaPlayer?.isLooping = false

                mediaPlayer?.setOnPreparedListener { mp ->
                    progressBar.visibility = View.GONE
                    layoutCenterReplay.visibility = View.GONE
                    mp.start()
                }

                mediaPlayer?.setOnCompletionListener {
                    layoutCenterReplay.visibility = View.VISIBLE
                }

                mediaPlayer?.setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Không thể tải video hướng dẫn.", Toast.LENGTH_SHORT).show()
                    true
                }

                mediaPlayer?.prepareAsync()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Lỗi phát video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                val surface = Surface(surfaceTexture)
                initMediaPlayer(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        cardReplayButton.setOnClickListener {
            playFromStart()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndStart(exercise: Exercise) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openWorkoutCamera(exercise)
        } else {
            pendingExercise = exercise
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openWorkoutCamera(exercise: Exercise) {
        val bundle = Bundle().apply {
            putString("exerciseId", exercise.id)
            putString("exerciseName", exercise.name)
            putInt("targetCount", exercise.targetCount)
            putInt("dayIndex", selectedDayIndex)
        }
        findNavController().navigate(R.id.action_workout_calendar_to_camera, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- UI Data Holder for Horizontal Day Card ---
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

    // --- HORIZONTAL RECYCLERVIEW ADAPTER FOR 6-COLOR STATUS DAYS ---
    private class WorkoutDaysAdapter(
        private var days: List<DayItemUI>,
        private var selectedPosition: Int,
        private val onDaySelected: (Int) -> Unit
    ) : RecyclerView.Adapter<WorkoutDaysAdapter.DayViewHolder>() {

        fun updateData(newDays: List<DayItemUI>, defaultSelectedIdx: Int) {
            this.days = newDays
            this.selectedPosition = defaultSelectedIdx
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val item = days[position]
            val isSelected = (position == selectedPosition)
            holder.bind(item, isSelected)
            
            holder.itemView.setOnClickListener {
                if (position != selectedPosition) {
                    val oldPos = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)
                    onDaySelected(item.dayIndex)
                }
            }
        }

        override fun getItemCount(): Int = days.size

        class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardDay: MaterialCardView = itemView.findViewById(R.id.cardDay)
            private val tvCalendarDate: TextView = itemView.findViewById(R.id.tvCalendarDate)
            private val tvDayNum: TextView = itemView.findViewById(R.id.tvDayNum)
            private val tvDayLabel: TextView = itemView.findViewById(R.id.tvDayLabel)

            fun bind(item: DayItemUI, isSelected: Boolean) {
                tvCalendarDate.text = item.dateFormatted

                // Apply 6 Color Status Rules
                when {
                    item.isCurrentDay -> { // 1. CURRENT DAY -> TRI FORCE ELECTRIC BLUE (#0066FF)
                        cardDay.setCardBackgroundColor(Color.parseColor("#0066FF"))
                        cardDay.strokeColor = Color.parseColor("#00D2FF")
                        tvCalendarDate.setTextColor(Color.parseColor("#E0F2FE"))
                        tvDayNum.text = String.format("%02d", item.dayIndex)
                        tvDayNum.setTextColor(Color.WHITE)
                        tvDayLabel.text = "Hôm nay"
                        tvDayLabel.setTextColor(Color.parseColor("#BAE6FD"))
                    }
                    item.isRestDay -> { // REST DAY -> Amber Glass
                        cardDay.setCardBackgroundColor(Color.parseColor("#331E293B"))
                        cardDay.strokeColor = Color.parseColor("#F59E0B")
                        tvCalendarDate.setTextColor(Color.parseColor("#FBBF24"))
                        tvDayNum.text = "NGHỈ"
                        tvDayNum.setTextColor(Color.parseColor("#F59E0B"))
                        tvDayLabel.text = "Thư giãn"
                        tvDayLabel.setTextColor(Color.parseColor("#94A3B8"))
                    }
                    item.isPastDay -> { // PAST WORKOUT DAYS
                        when {
                            item.completedExercises > 0 && item.completedExercises == item.totalExercises -> {
                                // ALL COMPLETED -> Emerald Glass
                                cardDay.setCardBackgroundColor(Color.parseColor("#33064E3B"))
                                cardDay.strokeColor = Color.parseColor("#10B981")
                                tvCalendarDate.setTextColor(Color.parseColor("#6EE7B7"))
                                tvDayNum.text = String.format("%02d", item.dayIndex)
                                tvDayNum.setTextColor(Color.parseColor("#34D399"))
                                tvDayLabel.text = "Xong"
                                tvDayLabel.setTextColor(Color.parseColor("#A7F3D0"))
                            }
                            item.completedExercises > 0 -> {
                                // PARTIALLY COMPLETED -> Amber Glass
                                cardDay.setCardBackgroundColor(Color.parseColor("#3378350F"))
                                cardDay.strokeColor = Color.parseColor("#F59E0B")
                                tvCalendarDate.setTextColor(Color.parseColor("#FCD34D"))
                                tvDayNum.text = String.format("%02d", item.dayIndex)
                                tvDayNum.setTextColor(Color.parseColor("#FBBF24"))
                                tvDayLabel.text = "Dở dang"
                                tvDayLabel.setTextColor(Color.parseColor("#FDE68A"))
                            }
                            else -> {
                                // MISSED -> Red Glass
                                cardDay.setCardBackgroundColor(Color.parseColor("#337F1D1D"))
                                cardDay.strokeColor = Color.parseColor("#EF4444")
                                tvCalendarDate.setTextColor(Color.parseColor("#FCA5A5"))
                                tvDayNum.text = String.format("%02d", item.dayIndex)
                                tvDayNum.setTextColor(Color.parseColor("#F87171"))
                                tvDayLabel.text = "Bỏ qua"
                                tvDayLabel.setTextColor(Color.parseColor("#FECACA"))
                            }
                        }
                    }
                    else -> { // FUTURE WORKOUT DAYS -> Dark Glass
                        cardDay.setCardBackgroundColor(Color.parseColor("#CC0A192F"))
                        cardDay.strokeColor = Color.parseColor("#1E3A8A")
                        tvCalendarDate.setTextColor(Color.parseColor("#94A3B8"))
                        tvDayNum.text = String.format("%02d", item.dayIndex)
                        tvDayNum.setTextColor(Color.WHITE)
                        tvDayLabel.text = "Ngày"
                        tvDayLabel.setTextColor(Color.parseColor("#64748B"))
                    }
                }

                // If selected, add thicker stroke border to highlight selection
                if (isSelected) {
                    cardDay.strokeWidth = 6 // thicker stroke
                } else {
                    cardDay.strokeWidth = 3
                }
            }
        }
    }

    // --- VERTICAL RECYCLERVIEW ADAPTER FOR EXERCISES ---
    private class ExercisesAdapter(
        private var exercises: List<Exercise>,
        private val onStartClicked: (Exercise) -> Unit,
        private val onWatchVideoClicked: (Exercise) -> Unit
    ) : RecyclerView.Adapter<ExercisesAdapter.ExerciseViewHolder>() {

        fun updateData(newExercises: List<Exercise>) {
            this.exercises = newExercises
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise, parent, false)
            return ExerciseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
            holder.bind(exercises[position], onStartClicked, onWatchVideoClicked)
        }

        override fun getItemCount(): Int = exercises.size

        class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvExerciseName)
            private val tvTarget: TextView = itemView.findViewById(R.id.tvExerciseTarget)
            private val tvDesc: TextView = itemView.findViewById(R.id.tvExerciseDesc)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            private val btnWatchVideo: Button = itemView.findViewById(R.id.btnWatchVideo)
            private val btnStart: Button = itemView.findViewById(R.id.btnStartExercise)

            fun bind(
                exercise: Exercise,
                onStartClicked: (Exercise) -> Unit,
                onWatchVideoClicked: (Exercise) -> Unit
            ) {
                tvName.text = exercise.name
                tvTarget.text = "Mục tiêu: ${exercise.targetCount} ${exercise.unit}"
                tvDesc.text = exercise.description

                if (exercise.status == 1) { // Completed
                    tvStatus.visibility = View.VISIBLE
                    btnStart.text = "TẬP LẠI"
                    btnStart.setBackgroundColor(Color.parseColor("#757575"))
                } else { // Pending
                    tvStatus.visibility = View.GONE
                    btnStart.text = "BẮT ĐẦU"
                    btnStart.setBackgroundColor(Color.parseColor("#0066FF"))
                }

                btnWatchVideo.setOnClickListener {
                    onWatchVideoClicked(exercise)
                }

                btnStart.setOnClickListener {
                    onStartClicked(exercise)
                }
            }
        }
    }
}
