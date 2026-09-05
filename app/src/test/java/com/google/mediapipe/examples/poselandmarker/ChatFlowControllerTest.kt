package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.AnswerType
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatFlowController
import com.google.mediapipe.examples.poselandmarker.ui.fragment.profile.chat.ChatQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFlowControllerTest {

    private val sampleQuestions = listOf(
        ChatQuestion(
            id = "name",
            botText = "What's your name?",
            answerType = AnswerType.TEXT_INPUT
        ),
        ChatQuestion(
            id = "age",
            botText = "How old are you?",
            answerType = AnswerType.NUMBER_INPUT
        ),
        ChatQuestion(
            id = "fitness_level",
            botText = "What is your fitness level?",
            answerType = AnswerType.SINGLE_CHOICE,
            options = listOf("Beginner", "Advanced"),
            optionValues = listOf("beginner", "advanced")
        )
    )

    @Test
    fun initialStateStartsAtFirstQuestion() {
        val controller = ChatFlowController(sampleQuestions)
        assertEquals(0, controller.currentIndex)
        assertEquals("name", controller.currentQuestion()?.id)
        assertFalse(controller.isFinished())
        assertTrue(controller.getAllAnswers().isEmpty())
    }

    @Test
    fun submittingAnswersAdvancesIndexUntilFinished() {
        val controller = ChatFlowController(sampleQuestions)

        controller.submitAnswer("Nam")
        assertEquals(1, controller.currentIndex)
        assertEquals("age", controller.currentQuestion()?.id)
        assertEquals("Nam", controller.getAnswer("name"))

        controller.submitAnswer("25")
        assertEquals(2, controller.currentIndex)
        assertEquals("fitness_level", controller.currentQuestion()?.id)
        assertEquals("25", controller.getAnswer("age"))

        controller.submitAnswer("beginner")
        assertEquals(3, controller.currentIndex)
        assertNull(controller.currentQuestion())
        assertTrue(controller.isFinished())

        val answers = controller.getAllAnswers()
        assertEquals("Nam", answers["name"])
        assertEquals("25", answers["age"])
        assertEquals("beginner", answers["fitness_level"])
    }

    @Test
    fun resetClearsAllAnswersAndResetsIndexToZero() {
        val controller = ChatFlowController(sampleQuestions)

        controller.submitAnswer("Nam")
        controller.submitAnswer("25")
        assertEquals(2, controller.currentIndex)
        assertEquals(2, controller.getAllAnswers().size)

        controller.reset()

        assertEquals(0, controller.currentIndex)
        assertEquals("name", controller.currentQuestion()?.id)
        assertFalse(controller.isFinished())
        assertTrue(controller.getAllAnswers().isEmpty())
        assertNull(controller.getAnswer("name"))
        assertNull(controller.getAnswer("age"))
    }

    @Test
    fun editAnswerAtRewindsToTargetQuestionAndRemovesSubsequentAnswers() {
        val controller = ChatFlowController(sampleQuestions)

        controller.submitAnswer("Nam")
        controller.submitAnswer("25")
        controller.submitAnswer("beginner")
        assertTrue(controller.isFinished())

        controller.editAnswerAt(1)
        assertEquals(1, controller.currentIndex)
        assertEquals("age", controller.currentQuestion()?.id)
        assertEquals("Nam", controller.getAnswer("name"))
        assertNull(controller.getAnswer("age"))
        assertNull(controller.getAnswer("fitness_level"))
    }
}
