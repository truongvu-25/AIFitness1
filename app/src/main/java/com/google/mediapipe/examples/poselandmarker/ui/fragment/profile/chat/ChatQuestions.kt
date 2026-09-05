package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

import android.content.Context
import com.google.mediapipe.examples.poselandmarker.R

object ChatQuestions {

    fun create(context: Context) = listOf(
        ChatQuestion(
            id = "name",
            botText = context.getString(R.string.question_name),
            answerType = AnswerType.TEXT_INPUT,
            inputHint = context.getString(R.string.question_name_hint)
        ),
        ChatQuestion(
            id = "age",
            botText = context.getString(R.string.question_age),
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = context.getString(R.string.question_age_hint),
            allowDecimal = false
        ),
        ChatQuestion(
            id = "height",
            botText = context.getString(R.string.question_height),
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = context.getString(R.string.question_height_hint),
            allowDecimal = true
        ),
        ChatQuestion(
            id = "weight",
            botText = context.getString(R.string.question_weight),
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = context.getString(R.string.question_weight_hint),
            allowDecimal = true
        ),
        ChatQuestion(
            id = "fitness_level",
            botText = context.getString(R.string.question_fitness_level),
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf(
                context.getString(R.string.option_beginner),
                context.getString(R.string.option_intermediate),
                context.getString(R.string.option_advanced)
            ),
            optionValues = listOf("beginner", "intermediate", "advanced")
        ),
        ChatQuestion(
            id = "goals",
            botText = context.getString(R.string.question_goals),
            answerType = AnswerType.MULTI_CHOICE,
            options = listOf(
                context.getString(R.string.option_strength),
                context.getString(R.string.option_muscle),
                context.getString(R.string.option_fat_loss),
                context.getString(R.string.option_technique)
            ),
            optionValues = listOf("strength", "muscle", "fat_loss", "technique")
        ),
        ChatQuestion(
            id = "pullups",
            botText = context.getString(R.string.question_pullups),
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf(
                context.getString(R.string.option_under_5),
                context.getString(R.string.option_5_10),
                context.getString(R.string.option_10_20),
                context.getString(R.string.option_over_20)
            ),
            optionValues = listOf("under_5", "5_10", "10_20", "over_20")
        ),
        ChatQuestion(
            id = "pushups",
            botText = context.getString(R.string.question_pushups),
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf(
                context.getString(R.string.option_under_10),
                context.getString(R.string.option_10_30),
                context.getString(R.string.option_30_50),
                context.getString(R.string.option_over_50)
            ),
            optionValues = listOf("under_10", "10_30", "30_50", "over_50")
        ),
        ChatQuestion(
            id = "squats",
            botText = context.getString(R.string.question_squats),
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf(
                context.getString(R.string.option_under_10),
                context.getString(R.string.option_10_30),
                context.getString(R.string.option_30_50),
                context.getString(R.string.option_over_50)
            ),
            optionValues = listOf("under_10", "10_30", "30_50", "over_50")
        )
    )
}
