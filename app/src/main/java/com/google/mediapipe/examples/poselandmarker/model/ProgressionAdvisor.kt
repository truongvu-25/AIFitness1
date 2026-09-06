package com.google.mediapipe.examples.poselandmarker.model

import kotlin.math.roundToInt

object ProgressionAdvisor {

    const val TOO_EASY = "TOO_EASY"
    const val JUST_RIGHT = "JUST_RIGHT"
    const val TOO_HARD = "TOO_HARD"

    fun recommendTarget(
        currentTarget: Int,
        actualCount: Int,
        formScore: Int,
        difficulty: String
    ): Int {
        if (currentTarget <= 0) return 0

        val adjustment = (currentTarget * 0.1f).roundToInt().coerceAtLeast(1)
        return when {
            formScore in 1..54 -> (currentTarget - adjustment).coerceAtLeast(1)
            difficulty == TOO_EASY &&
                actualCount >= currentTarget &&
                formScore >= 70 -> currentTarget + adjustment
            difficulty == TOO_HARD -> (currentTarget - adjustment).coerceAtLeast(1)
            else -> currentTarget
        }
    }
}
