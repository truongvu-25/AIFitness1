package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

sealed class ChatItem {
    data class BotMessage(val text: String) : ChatItem()
    data class UserAnswer(val text: String, val questionIndex: Int) : ChatItem()
    object TypingIndicator : ChatItem()
}

enum class AnswerType {
    TEXT_INPUT,
    NUMBER_INPUT,
    SINGLE_CHOICE,
    MULTI_CHOICE
}

data class ChatQuestion(
    val id: String,
    val botText: String,
    val answerType: AnswerType,
    val options: List<String> = emptyList(),
    val optionValues: List<String> = options,
    val inputHint: String = "",
    val allowDecimal: Boolean = false
) {
    fun displayValue(rawValue: String): String {
        if (options.isEmpty() || optionValues.size != options.size) return rawValue
        val labelsByValue = optionValues.zip(options).toMap()
        return rawValue.split(",")
            .map { it.trim() }
            .joinToString(", ") { labelsByValue[it] ?: it }
    }
}
