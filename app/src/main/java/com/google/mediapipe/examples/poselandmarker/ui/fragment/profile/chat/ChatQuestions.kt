package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

import android.content.Context
import com.google.mediapipe.examples.poselandmarker.R

object ChatQuestions {

    val list = listOf(
        ChatQuestion(
            id = "name",
            botText = "Chào chiến binh 👋 Mình là trợ lý AI của TRI FORCE, sẽ đồng hành cùng bạn trong 30 ngày tới. Trước tiên, xưng danh nào — bạn tên gì?",
            answerType = AnswerType.TEXT_INPUT,
            inputHint = "Nhập họ và tên"
        ),
        ChatQuestion(
            id = "age",
            botText = "Ấn tượng đó! Bật mí cho mình bạn bao nhiêu \"tuổi đời\" rồi?",
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = "Nhập tuổi",
            allowDecimal = false
        ),
        ChatQuestion(
            id = "height",
            botText = "Giờ cuộn nhẹ ngón tay để chọn chiều cao của bạn nhé 📏",
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = "Chọn chiều cao (cm)",
            allowDecimal = true
        ),
        ChatQuestion(
            id = "weight",
            botText = "Cân nặng hiện tại thì sao? Đừng ngại, số này chỉ để tính toán cho chuẩn thôi 😄",
            answerType = AnswerType.NUMBER_INPUT,
            inputHint = "Nhập cân nặng (kg)",
            allowDecimal = true
        ),
        ChatQuestion(
            id = "fitness_level",
            botText = "Thật lòng đi — trình độ thể lực của bạn đang ở mức nào?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("🌱 Mới bắt đầu", "🔥 Trung bình", "⚡ Nâng cao"),
            optionValues = listOf("beginner", "intermediate", "advanced")
        ),
        ChatQuestion(
            id = "goals",
            botText = "Bạn muốn TRI FORCE giúp gì cho bạn? Cứ tham lam chọn nhiều cũng được 😏",
            answerType = AnswerType.MULTI_CHOICE,
            options = listOf("💪 Tăng sức mạnh", "🏋️ Tăng cơ bắp", "🔥 Giảm mỡ", "🎯 Học kỹ thuật"),
            optionValues = listOf("strength", "muscle", "fat_loss", "technique")
        ),
        ChatQuestion(
            id = "pullups",
            botText = "Thử thách nhỏ: bạn hít xà đơn được bao nhiêu cái liên tục mà không bỏ cuộc?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 5", "💪 5 - 10", "🔥 10 - 20", "🏆 Trên 20"),
            optionValues = listOf("under_5", "5_10", "10_20", "over_20")
        ),
        ChatQuestion(
            id = "pushups",
            botText = "Còn hít đất — cánh tay bạn trụ được bao lâu?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 10", "💪 10 - 30", "🔥 30 - 50", "🏆 Trên 50"),
            optionValues = listOf("under_10", "10_30", "30_50", "over_50")
        ),
        ChatQuestion(
            id = "squats",
            botText = "Câu cuối cùng rồi! Ngồi xổm liên tục thì bạn trụ được bao nhiêu cái?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 10", "💪 10 - 30", "🔥 30 - 50", "🏆 Trên 50"),
            optionValues = listOf("under_10", "10_30", "30_50", "over_50")
        )
    )

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
