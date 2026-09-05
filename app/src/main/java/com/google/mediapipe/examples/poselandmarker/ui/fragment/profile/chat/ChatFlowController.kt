package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

class ChatFlowController(private val questions: List<ChatQuestion>) {

    private val answers = mutableMapOf<String, String>()
    var currentIndex = 0
        private set

    fun currentQuestion(): ChatQuestion? = questions.getOrNull(currentIndex)

    fun isFinished(): Boolean = currentIndex >= questions.size

    fun submitAnswer(rawValue: String) {
        val question = currentQuestion() ?: return
        answers[question.id] = rawValue
        currentIndex++
    }

    fun editAnswerAt(questionIndex: Int) {
        if (questionIndex !in questions.indices) return
        for (i in questionIndex until questions.size) {
            answers.remove(questions[i].id)
        }
        currentIndex = questionIndex
    }

    /** Dùng cho chế độ Chỉnh sửa hồ sơ: nạp sẵn toàn bộ câu trả lời đã có, coi như đã hỏi xong hết. */
    fun preload(existingAnswers: Map<String, String>) {
        answers.clear()
        answers.putAll(existingAnswers)
        currentIndex = questions.size
    }

    fun getAnswer(questionId: String): String? = answers[questionId]

    fun getAllAnswers(): Map<String, String> = answers.toMap()

    fun allQuestions(): List<ChatQuestion> = questions
}
