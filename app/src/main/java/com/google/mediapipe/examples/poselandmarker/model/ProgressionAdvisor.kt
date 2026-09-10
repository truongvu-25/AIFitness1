package com.google.mediapipe.examples.poselandmarker.model

import kotlin.math.roundToInt

object ProgressionAdvisor {

    const val TOO_EASY = "TOO_EASY"
    const val JUST_RIGHT = "JUST_RIGHT"
    const val TOO_HARD = "TOO_HARD"

    const val TOO_EASY_ADJUSTMENT_PERCENT = 20
    const val TOO_HARD_ADJUSTMENT_PERCENT = 20
    const val LOW_FORM_ADJUSTMENT_PERCENT = 15

    fun recommendTarget(
        currentTarget: Int,
        actualCount: Int,
        formScore: Int,
        difficulty: String
    ): Int {
        if (currentTarget <= 0) return 0

        return when {
            formScore in 1..54 -> currentTarget.adjustByPercent(
                percent = LOW_FORM_ADJUSTMENT_PERCENT,
                increase = false
            )
            difficulty == TOO_EASY &&
                actualCount >= currentTarget &&
                formScore >= 70 -> currentTarget.adjustByPercent(
                    percent = TOO_EASY_ADJUSTMENT_PERCENT,
                    increase = true
                )
            difficulty == TOO_HARD -> currentTarget.adjustByPercent(
                percent = TOO_HARD_ADJUSTMENT_PERCENT,
                increase = false
            )
            else -> currentTarget
        }
    }

    private fun Int.adjustByPercent(percent: Int, increase: Boolean): Int {
        val adjustment = (this * percent / 100f).roundToInt().coerceAtLeast(1)
        return if (increase) this + adjustment else (this - adjustment).coerceAtLeast(1)
    }
}
