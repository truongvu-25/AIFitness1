package com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFlowControllerTest {

    @Test
    fun updateAnswer_inEditMode_preservesLaterAnswersAndFinishedState() {
        val controller = ChatFlowController()
        val existingAnswers = controller.allQuestions()
            .associate { question -> question.id to "old-${question.id}" }

        controller.preload(existingAnswers)
        controller.updateAnswer(questionIndex = 1, rawValue = "23")

        assertEquals("23", controller.getAnswer("age"))
        assertEquals("old-squats", controller.getAnswer("squats"))
        assertEquals(existingAnswers.size, controller.getAllAnswers().size)
        assertTrue(controller.isFinished())
    }
}
