package com.google.mediapipe.examples.poselandmarker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionAdvisorTest {

    @Test
    fun tooEasyWithCompletedTargetAndGoodForm_increasesByTwentyPercent() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 48,
            actualCount = 48,
            formScore = 85,
            difficulty = ProgressionAdvisor.TOO_EASY
        )

        assertEquals(58, result)
    }

    @Test
    fun tooEasyWithWeakForm_keepsCurrentTarget() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 20,
            actualCount = 20,
            formScore = 55,
            difficulty = ProgressionAdvisor.TOO_EASY
        )

        assertEquals(20, result)
    }

    @Test
    fun tooEasyWithoutFormData_doesNotIncreaseTarget() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 20,
            actualCount = 24,
            formScore = 0,
            difficulty = ProgressionAdvisor.TOO_EASY
        )

        assertEquals(20, result)
    }

    @Test
    fun tooHard_reducesByTwentyPercent() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 30,
            actualCount = 18,
            formScore = 60,
            difficulty = ProgressionAdvisor.TOO_HARD
        )

        assertEquals(24, result)
    }

    @Test
    fun weakForm_reducesTargetEvenWhenEffortFeelsRight() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 20,
            actualCount = 20,
            formScore = 42,
            difficulty = ProgressionAdvisor.JUST_RIGHT
        )

        assertEquals(17, result)
    }

    @Test
    fun justRight_keepsCurrentTarget() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 15,
            actualCount = 15,
            formScore = 90,
            difficulty = ProgressionAdvisor.JUST_RIGHT
        )

        assertEquals(15, result)
    }

    @Test
    fun tinyHardTarget_neverDropsBelowOne() {
        val result = ProgressionAdvisor.recommendTarget(
            currentTarget = 1,
            actualCount = 0,
            formScore = 0,
            difficulty = ProgressionAdvisor.TOO_HARD
        )

        assertEquals(1, result)
    }
}
