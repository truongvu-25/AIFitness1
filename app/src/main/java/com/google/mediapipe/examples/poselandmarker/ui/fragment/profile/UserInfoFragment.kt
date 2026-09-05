package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentUserInfoBinding
import com.google.mediapipe.examples.poselandmarker.databinding.LayoutHeightWheelPickerBinding
import com.google.mediapipe.examples.poselandmarker.model.UserExercise
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.AnswerType
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatAdapter
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatFlowController
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatItem
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatQuestion
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatQuestions
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.HeightWheelAdapter

class UserInfoFragment : Fragment() {

    private var _binding: FragmentUserInfoBinding? = null
    private val binding get() = _binding!!

    private var _wheelBinding: LayoutHeightWheelPickerBinding? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var isEditMode = false
    private var originalCreatedTime = 0L

    private lateinit var controller: ChatFlowController
    private lateinit var chatAdapter: ChatAdapter

    private val questionStartPos = mutableListOf<Int>()

    private var selectedSingleButton: MaterialButton? = null
    private val selectedMultiOptions = mutableSetOf<String>()
    private var pendingEditAnswer: Pair<String, String>? = null

    private var heightWheelAdapter: HeightWheelAdapter? = null
    private val heightValues = (80..250).toList()
    private val heightItemHeightDp = 48

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        controller = ChatFlowController(ChatQuestions.create(requireContext()))
        isEditMode = arguments?.getBoolean("isEditMode", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatAdapter = ChatAdapter(onEditClicked = { questionIndex -> onEditRequested(questionIndex) })
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChat.adapter = chatAdapter

        binding.btnChatContinue.setOnClickListener { onContinueClicked() }

        if (isEditMode) {
            binding.tvUserInfoTitle.setText(R.string.profile_edit_screen_title)
            binding.tvQuestionProgress.visibility = View.GONE
            binding.progressQuestions.visibility = View.GONE
            loadExistingProfileThenPreload()
        } else {
            askCurrentQuestion()
        }
    }

    // ---------- Luồng hỏi-đáp ----------

    private fun askCurrentQuestion() {
        val question = controller.currentQuestion()
        if (question == null) {
            finishAndSave()
            return
        }

        updateProgress()

        // Hiện "đang gõ..." trước, giả lập độ trễ tự nhiên
        chatAdapter.addItem(ChatItem.TypingIndicator)
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)

        binding.rvChat.postDelayed({
            if (_binding == null) return@postDelayed
            chatAdapter.removeLastIfTyping()
            addBotMessage(question)
            renderInputFor(question)
        }, 650)
    }

    private fun updateProgress() {
        val total = controller.allQuestions().size
        val current = (controller.currentIndex + 1).coerceAtMost(total)
        binding.tvQuestionProgress.text = getString(R.string.question_progress, current, total)
        binding.progressQuestions.progress = ((current.toFloat() / total) * 100).toInt()
    }

    private fun addBotMessage(question: ChatQuestion) {
        questionStartPos.add(chatAdapter.itemCount)
        chatAdapter.addItem(ChatItem.BotMessage(question.botText))
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun renderInputFor(question: ChatQuestion) {
        val existingValue = pendingEditAnswer
            ?.takeIf { it.first == question.id }
            ?.second
        pendingEditAnswer = null
        selectedSingleButton = null
        selectedMultiOptions.clear()
        binding.containerChoices.removeAllViews()
        binding.containerHeightWheel.visibility = View.GONE
        binding.containerHeightWheel.removeAllViews()
        heightWheelAdapter = null

        if (question.id == "height") {
            renderHeightWheel(existingValue?.toDoubleOrNull()?.toInt() ?: 170)
            updateContinueButtonState(question)
            return
        }

        updateContinueButtonState(question)

        when (question.answerType) {
            AnswerType.TEXT_INPUT, AnswerType.NUMBER_INPUT -> {
                binding.tilDynamicInput.visibility = View.VISIBLE
                binding.containerChoices.visibility = View.GONE
                binding.tilDynamicInput.hint = question.inputHint
                binding.etDynamicInput.setText(existingValue.orEmpty())
                binding.etDynamicInput.inputType = when {
                    question.answerType == AnswerType.NUMBER_INPUT && question.allowDecimal ->
                        android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    question.answerType == AnswerType.NUMBER_INPUT ->
                        android.text.InputType.TYPE_CLASS_NUMBER
                    else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                }
                binding.etDynamicInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        updateContinueButtonState(question)
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            AnswerType.SINGLE_CHOICE -> {
                binding.tilDynamicInput.visibility = View.GONE
                binding.containerChoices.visibility = View.VISIBLE
                question.options.forEachIndexed { index, optionText ->
                    val btn = LayoutInflater.from(requireContext())
                        .inflate(R.layout.layout_chat_choice_button, binding.containerChoices, false) as MaterialButton
                    btn.text = optionText
                    btn.tag = question.optionValues.getOrElse(index) { optionText }
                    if (existingValue == btn.tag?.toString() || existingValue == optionText) {
                        applySelectedStyle(btn)
                        selectedSingleButton = btn
                    }
                    btn.setOnClickListener {
                        selectedSingleButton?.let { resetChoiceStyle(it) }
                        applySelectedStyle(btn)
                        selectedSingleButton = btn
                        updateContinueButtonState(question)
                    }
                    binding.containerChoices.addView(btn)
                }
            }
            AnswerType.MULTI_CHOICE -> {
                binding.tilDynamicInput.visibility = View.GONE
                binding.containerChoices.visibility = View.VISIBLE
                question.options.forEachIndexed { index, optionText ->
                    val optionValue = question.optionValues.getOrElse(index) { optionText }
                    val btn = LayoutInflater.from(requireContext())
                        .inflate(R.layout.layout_chat_choice_button, binding.containerChoices, false) as MaterialButton
                    btn.text = optionText
                    btn.tag = optionValue
                    val selectedValues = existingValue.orEmpty()
                        .split(",")
                        .map { it.trim() }
                    if (optionValue in selectedValues || optionText in selectedValues) {
                        selectedMultiOptions.add(optionValue)
                        applySelectedStyle(btn)
                    }
                    btn.setOnClickListener {
                        if (selectedMultiOptions.contains(optionValue)) {
                            selectedMultiOptions.remove(optionValue)
                            resetChoiceStyle(btn)
                        } else {
                            selectedMultiOptions.add(optionValue)
                            applySelectedStyle(btn)
                        }
                        updateContinueButtonState(question)
                    }
                    binding.containerChoices.addView(btn)
                }
            }
        }
        updateContinueButtonState(question)
    }

    // ---------- Bánh xe chọn chiều cao ----------

    private fun renderHeightWheel(defaultValue: Int) {
        binding.tilDynamicInput.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
        binding.containerHeightWheel.visibility = View.VISIBLE

        val wheelBinding = LayoutHeightWheelPickerBinding.inflate(
            LayoutInflater.from(requireContext()), binding.containerHeightWheel, true
        )
        _wheelBinding = wheelBinding

        val normalizedDefault = defaultValue.coerceIn(
            heightValues.first(),
            heightValues.last()
        )
        val adapter = HeightWheelAdapter(heightValues)
        heightWheelAdapter = adapter

        wheelBinding.rvHeightWheel.layoutManager = LinearLayoutManager(requireContext())
        wheelBinding.rvHeightWheel.adapter = adapter

        val density = resources.displayMetrics.density
        val itemHeightPx = (heightItemHeightDp * density).toInt()
        val paddingPx = itemHeightPx * 2
        wheelBinding.rvHeightWheel.setPadding(0, paddingPx, 0, paddingPx)

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(wheelBinding.rvHeightWheel)

        wheelBinding.rvHeightWheel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(rv.layoutManager) ?: return
                    val position = rv.getChildAdapterPosition(centerView)
                    if (position in heightValues.indices) {
                        adapter.setCenterValue(heightValues[position])
                    }
                }
            }
        })

        wheelBinding.rvHeightWheel.post {
            val index = heightValues.indexOf(normalizedDefault)
            if (index >= 0) {
                wheelBinding.rvHeightWheel.scrollToPosition(index)
                adapter.setCenterValue(normalizedDefault)
            }
        }
    }

    // ---------- Style nút chọn ----------

    private fun applySelectedStyle(btn: MaterialButton) {
        btn.setBackgroundColor(resources.getColor(R.color.mp_color_primary, null))
        btn.setTextColor(resources.getColor(R.color.tri_force_white, null))
    }

    private fun resetChoiceStyle(btn: MaterialButton) {
        btn.setBackgroundColor(resources.getColor(R.color.tri_force_dark_card, null))
        btn.setTextColor(resources.getColor(R.color.tri_force_text_primary, null))
    }

    private fun updateContinueButtonState(question: ChatQuestion) {
        val hasAnswer = when {
            question.id == "height" -> true
            question.answerType == AnswerType.TEXT_INPUT || question.answerType == AnswerType.NUMBER_INPUT ->
                binding.etDynamicInput.text.toString().trim().isNotEmpty()
            question.answerType == AnswerType.SINGLE_CHOICE -> selectedSingleButton != null
            question.answerType == AnswerType.MULTI_CHOICE -> selectedMultiOptions.isNotEmpty()
            else -> false
        }
        binding.btnChatContinue.isEnabled = hasAnswer
        binding.btnChatContinue.alpha = if (hasAnswer) 1.0f else 0.4f
    }

    // ---------- Xử lý bấm TIẾP TỤC ----------

    private fun onContinueClicked() {
        val question = controller.currentQuestion()
        if (question == null) {
            if (isEditMode) finishAndSave()
            return
        }

        val rawValue: String
        val displayValue: String

        when {
            question.id == "height" -> {
                val value = heightWheelAdapter?.centerValue ?: 170
                rawValue = value.toString()
                displayValue = getString(R.string.value_centimeters, value.toString())
            }
            question.answerType == AnswerType.TEXT_INPUT -> {
                val text = binding.etDynamicInput.text.toString().trim()
                if (text.isEmpty()) {
                    binding.tilDynamicInput.error = getString(R.string.input_required)
                    return
                }
                binding.tilDynamicInput.error = null
                rawValue = text
                displayValue = text
            }
            question.answerType == AnswerType.NUMBER_INPUT -> {
                val text = binding.etDynamicInput.text.toString().trim()
                val number = text.toDoubleOrNull()
                val isValid = when (question.id) {
                    "age" -> number != null && number in 5.0..120.0
                    "weight" -> number != null && number in 20.0..400.0
                    else -> number != null && number > 0
                }
                if (!isValid) {
                    binding.tilDynamicInput.error = getString(R.string.number_invalid)
                    return
                }
                binding.tilDynamicInput.error = null
                rawValue = text
                displayValue = text
            }
            question.answerType == AnswerType.SINGLE_CHOICE -> {
                val selectedButton = selectedSingleButton
                if (selectedButton == null) {
                    Toast.makeText(context, R.string.choice_required, Toast.LENGTH_SHORT).show()
                    return
                }
                rawValue = selectedButton.tag?.toString() ?: selectedButton.text.toString()
                displayValue = selectedButton.text.toString()
            }
            question.answerType == AnswerType.MULTI_CHOICE -> {
                if (selectedMultiOptions.isEmpty()) {
                    Toast.makeText(context, R.string.multi_choice_required, Toast.LENGTH_SHORT).show()
                    return
                }
                rawValue = selectedMultiOptions.joinToString(", ")
                displayValue = question.displayValue(rawValue)
            }
            else -> return
        }

        val questionIndex = controller.currentIndex
        controller.submitAnswer(rawValue)
        chatAdapter.addItem(ChatItem.UserAnswer(displayValue, questionIndex))
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)

        askCurrentQuestion()
    }

    private fun onEditRequested(questionIndex: Int) {
        if (questionIndex >= questionStartPos.size) return
        val question = controller.allQuestions().getOrNull(questionIndex) ?: return
        pendingEditAnswer = question.id to controller.getAnswer(question.id).orEmpty()
        val removeFrom = questionStartPos[questionIndex]
        chatAdapter.removeFromIndex(removeFrom)
        while (questionStartPos.size > questionIndex) questionStartPos.removeAt(questionStartPos.size - 1)
        controller.editAnswerAt(questionIndex)
        binding.panelInput.visibility = View.VISIBLE
        binding.btnChatContinue.visibility = View.VISIBLE
        askCurrentQuestion()
    }

    // ---------- Chế độ Chỉnh sửa hồ sơ ----------

    private fun loadExistingProfileThenPreload() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(context, R.string.camera_sign_in_required, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        setLoading(true)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                setLoading(false)
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    if (profile != null) {
                        originalCreatedTime = profile.createdTime
                        val existing = mapOf(
                            "name" to profile.fullName,
                            "age" to profile.age.toString(),
                            "height" to profile.height.toString(),
                            "weight" to profile.weight.toString(),
                            "fitness_level" to profile.fitnessLevel,
                            "goals" to profile.goals.joinToString(", "),
                            "pullups" to profile.pullupsRange,
                            "pushups" to profile.pushupsRange,
                            "squats" to profile.squatsRange
                        )
                        controller.preload(existing)
                        renderFullTranscript()
                    } else {
                        Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                } else {
                    Toast.makeText(context, R.string.calendar_profile_missing, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.calendar_profile_load_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun renderFullTranscript() {
        controller.allQuestions().forEachIndexed { index, question ->
            questionStartPos.add(chatAdapter.itemCount)
            chatAdapter.addItem(ChatItem.BotMessage(question.botText))
            val rawValue = controller.getAnswer(question.id).orEmpty()
            val display = when (question.id) {
                "height" -> getString(R.string.value_centimeters, rawValue)
                "weight" -> getString(R.string.value_kilograms, rawValue)
                else -> question.displayValue(rawValue)
            }
            chatAdapter.addItem(ChatItem.UserAnswer(display, index))
        }
        binding.tilDynamicInput.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
        binding.containerHeightWheel.visibility = View.GONE
        binding.btnChatContinue.setText(R.string.save_changes)
        binding.btnChatContinue.isEnabled = true
        binding.btnChatContinue.alpha = 1.0f
    }

    // ---------- Lưu dữ liệu ----------

    private fun finishAndSave() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(context, R.string.camera_sign_in_required, Toast.LENGTH_SHORT).show()
            return
        }
        val answers = controller.getAllAnswers()

        val fullName = answers["name"]?.trim().orEmpty()
        val age = answers["age"]?.toIntOrNull()
        val height = answers["height"]?.toDoubleOrNull()
        val weight = answers["weight"]?.toDoubleOrNull()

        if (fullName.isEmpty() || age == null || height == null || weight == null) {
            Toast.makeText(context, R.string.profile_incomplete, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        binding.panelInput.visibility = View.GONE

        val heightInMeters = height / 100.0
        val bmi = weight / (heightInMeters * heightInMeters)
        val formattedBmi = Math.round(bmi * 10.0) / 10.0

        val bmiType = when {
            bmi < 18.5 -> "GAY"
            bmi < 25.0 -> "CAN DOI"
            else -> "THUA CAN"
        }

        val createdTime = if (isEditMode) originalCreatedTime else System.currentTimeMillis()

        val goalsList = answers["goals"]
            ?.split(", ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val profile = UserProfile(
            uid = uid,
            fullName = fullName,
            age = age,
            height = height,
            weight = weight,
            bmi = formattedBmi,
            bmiType = bmiType,
            createdTime = createdTime,
            lastBmiUpdatedTime = System.currentTimeMillis(),
            fitnessLevel = answers["fitness_level"].orEmpty(),
            goals = goalsList,
            pullupsRange = answers["pullups"].orEmpty(),
            pushupsRange = answers["pushups"].orEmpty(),
            squatsRange = answers["squats"].orEmpty()
        )

        db.collection("users").document(uid).set(profile)
            .addOnSuccessListener {
                if (isEditMode) {
                    setLoading(false)
                    Toast.makeText(context, R.string.profile_update_success, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    generateWorkoutPlan(uid, profile)
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.profile_save_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun isRestDayForBmi(bmiType: String, dayIndex: Int): Boolean {
        return when (bmiType) {
            "GAY" -> dayIndex in listOf(4, 7, 11, 14, 18, 21, 25, 28)
            "CAN DOI" -> dayIndex in listOf(4, 8, 12, 16, 20, 24, 28)
            else -> dayIndex in listOf(5, 10, 15, 20, 25, 30)
        }
    }

    private fun generateWorkoutPlan(uid: String, profile: UserProfile) {
        val batch = db.batch()

        for (day in 1..30) {
            val isRestDay = isRestDayForBmi(profile.bmiType, day)
            val exercises = if (isRestDay) emptyList() else getExercisesForProfile(profile, day)
            val workoutDay = WorkoutDay(dayIndex = day, exercises = exercises, isRestDay = isRestDay)
            val dayDocRef = db.collection("users").document(uid)
                .collection("workouts").document("day_$day")
            batch.set(dayDocRef, workoutDay)
        }

        batch.commit()
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(context, R.string.workout_plan_created, Toast.LENGTH_SHORT).show()
                NotificationHelper.scheduleDailyReminder(requireContext())
                findNavController().navigate(R.id.action_user_info_to_workout_calendar)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    context,
                    getString(R.string.workout_plan_create_error, e.localizedMessage.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun fitnessLevelMultiplier(fitnessLevel: String): Double = when {
        fitnessLevel == "beginner" || fitnessLevel.contains("Mới bắt đầu") ||
                fitnessLevel.contains("Beginner", ignoreCase = true) -> 0.8
        fitnessLevel == "advanced" || fitnessLevel.contains("Nâng cao") ||
                fitnessLevel.contains("Advanced", ignoreCase = true) -> 1.3
        else -> 1.0 // Trung bình hoặc chưa có dữ liệu (user cũ)
    }

    /** Chuyển "💪 10 - 30" hoặc "Dưới 5" thành 1 con số trung bình để làm mốc khởi điểm. */
    private fun parseBaselineFromRange(rangeText: String, fallback: Int): Int {
        when (rangeText) {
            "under_5" -> return 3
            "under_10" -> return 7
            "5_10" -> return 7
            "10_20" -> return 15
            "10_30" -> return 20
            "30_50" -> return 40
            "over_20" -> return 25
            "over_50" -> return 55
        }
        val numbers = Regex("\\d+").findAll(rangeText).map { it.value.toInt() }.toList()
        return when {
            numbers.size >= 2 -> (numbers[0] + numbers[1]) / 2
            numbers.size == 1 && (rangeText.contains("Dưới") ||
                    rangeText.contains("Under", ignoreCase = true)) ->
                (numbers[0] - 3).coerceAtLeast(3)
            numbers.size == 1 && (rangeText.contains("Trên") ||
                    rangeText.contains("Over", ignoreCase = true)) -> numbers[0] + 5
            numbers.size == 1 -> numbers[0]
            else -> fallback
        }
    }

    /** Mục tiêu tăng sức mạnh/cơ bắp -> đẩy pushup/squat lên; giảm mỡ -> đẩy jumpingjack lên. */
    private fun goalsBiasFor(exerciseId: String, goals: List<String>): Double {
        val wantsStrength = goals.any {
            it == "strength" || it == "muscle" || it.contains("sức mạnh") ||
                    it.contains("cơ bắp") || it.contains("strength", ignoreCase = true) ||
                    it.contains("muscle", ignoreCase = true)
        }
        val wantsFatLoss = goals.any {
            it == "fat_loss" || it.contains("Giảm mỡ") || it.contains("fat", ignoreCase = true)
        }
        return when (exerciseId) {
            "pushup", "squat", "splitsquat" -> if (wantsStrength) 1.2 else 1.0
            "jumpingjack" -> if (wantsFatLoss) 1.3 else 1.0
            else -> 1.0
        }
    }

    private fun getExercisesForProfile(profile: UserProfile, dayIndex: Int): List<UserExercise> {
        val weekMultiplier = when {
            dayIndex <= 7 -> 1.0
            dayIndex <= 14 -> 1.2
            dayIndex <= 21 -> 1.4
            else -> 1.6
        }
        val levelMultiplier = fitnessLevelMultiplier(profile.fitnessLevel)

        val pushupBaseline = parseBaselineFromRange(profile.pushupsRange, fallback = 12)
        val squatBaseline = parseBaselineFromRange(profile.squatsRange, fallback = 14)

        fun target(exerciseId: String, baseline: Int): Int {
            val bias = goalsBiasFor(exerciseId, profile.goals)
            return (baseline * weekMultiplier * levelMultiplier * bias).toInt().coerceAtLeast(5)
        }

        fun timedTarget(exerciseId: String, baseline: Int): Int {
            val bias = goalsBiasFor(exerciseId, profile.goals)
            return (baseline * weekMultiplier * levelMultiplier * bias).toInt().coerceAtLeast(10)
        }

        return when (profile.bmiType) {
            "GAY" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", target("pushup", pushupBaseline)),
                        UserExercise("squat", target("squat", squatBaseline)),
                        UserExercise("plank", timedTarget("plank", 30))
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", target("splitsquat", squatBaseline)),
                        UserExercise("situp", target("situp", 12)),
                        UserExercise("sideplank", timedTarget("sideplank", 30))
                    )
                }
            }
            "CAN DOI" -> {
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("pushup", target("pushup", pushupBaseline)),
                        UserExercise("squat", target("squat", squatBaseline)),
                        UserExercise("jumpingjack", target("jumpingjack", 25)),
                        UserExercise("plank", timedTarget("plank", 40))
                    )
                } else {
                    listOf(
                        UserExercise("splitsquat", target("splitsquat", squatBaseline)),
                        UserExercise("situp", target("situp", 15)),
                        UserExercise("sideplank", timedTarget("sideplank", 40)),
                        UserExercise("jumpingjack", target("jumpingjack", 25))
                    )
                }
            }
            else -> { // THUA CAN
                if (dayIndex % 2 != 0) {
                    listOf(
                        UserExercise("jumpingjack", target("jumpingjack", 30)),
                        UserExercise("squat", target("squat", squatBaseline)),
                        UserExercise("situp", target("situp", 20)),
                        UserExercise("plank", timedTarget("plank", 45))
                    )
                } else {
                    listOf(
                        UserExercise("jumpingjack", target("jumpingjack", 30)),
                        UserExercise("splitsquat", target("splitsquat", squatBaseline)),
                        UserExercise("sideplank", timedTarget("sideplank", 45)),
                        UserExercise("pushup", target("pushup", pushupBaseline))
                    )
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.infoProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnChatContinue.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _wheelBinding = null
        _binding = null
    }
}
