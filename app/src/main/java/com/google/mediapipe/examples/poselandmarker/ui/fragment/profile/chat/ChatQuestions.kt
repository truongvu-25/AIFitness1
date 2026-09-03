package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

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
            options = listOf("🌱 Mới bắt đầu", "🔥 Trung bình", "⚡ Nâng cao")
        ),
        ChatQuestion(
            id = "goals",
            botText = "Bạn muốn TRI FORCE giúp gì cho bạn? Cứ tham lam chọn nhiều cũng được 😏",
            answerType = AnswerType.MULTI_CHOICE,
            options = listOf("💪 Tăng sức mạnh", "🏋️ Tăng cơ bắp", "🔥 Giảm mỡ", "🎯 Học kỹ thuật")
        ),
        ChatQuestion(
            id = "pullups",
            botText = "Thử thách nhỏ: bạn hít xà đơn được bao nhiêu cái liên tục mà không bỏ cuộc?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 5", "💪 5 - 10", "🔥 10 - 20", "🏆 Trên 20")
        ),
        ChatQuestion(
            id = "pushups",
            botText = "Còn hít đất — cánh tay bạn trụ được bao lâu?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 10", "💪 10 - 30", "🔥 30 - 50", "🏆 Trên 50")
        ),
        ChatQuestion(
            id = "squats",
            botText = "Câu cuối cùng rồi! Ngồi xổm liên tục thì bạn trụ được bao nhiêu cái?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("😅 Dưới 10", "💪 10 - 30", "🔥 30 - 50", "🏆 Trên 50")
        )
    )
}