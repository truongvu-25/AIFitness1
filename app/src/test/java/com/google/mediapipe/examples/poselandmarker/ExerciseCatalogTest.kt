package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.examples.poselandmarker.model.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCatalogTest {

    @Test
    fun catalogKeepsExactlyTheSevenSupportedExercises() {
        assertEquals(
            listOf(
                "pushup",
                "situp",
                "squat",
                "plank",
                "sideplank",
                "jumpingjack",
                "splitsquat"
            ),
            ExerciseCatalog.supportedIds
        )
    }

    @Test
    fun legacyExerciseIdsResolveToCanonicalIds() {
        val aliases = mapOf(
            "push_up" to "pushup",
            "sit_up" to "situp",
            "jumping_jacks" to "jumpingjack",
            "side_plank" to "sideplank",
            "lunges" to "splitsquat"
        )

        aliases.forEach { (legacyId, canonicalId) ->
            assertEquals(canonicalId, ExerciseCatalog.canonicalId(legacyId))
        }
    }

    @Test
    fun presetPlansOnlyUseSupportedExercisesAndHaveValidStructure() {
        val presetPlans = ExerciseCatalog.presetPlans
        assertEquals(3, presetPlans.size)
        val validDays = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

        val expectedIds = listOf("preset_fullbody", "preset_core", "preset_hiit")
        assertEquals(expectedIds, presetPlans.map { it.id })

        presetPlans.forEach { plan ->
            assertEquals(validDays, plan.schedule.map { it.first })
            plan.schedule.forEach { (dayKey, exercises) ->
                exercises.forEach { (exerciseId, targetCount) ->
                    assert(exerciseId in ExerciseCatalog.supportedIds) {
                        "Unknown exercise ID $exerciseId in preset plan ${plan.id} on $dayKey"
                    }
                    assert(targetCount > 0) {
                        "Target count must be > 0 for $exerciseId in preset plan ${plan.id}"
                    }
                }
            }
            assertEquals(plan, ExerciseCatalog.findPresetPlan(plan.id))
        }
        assertEquals(null, ExerciseCatalog.findPresetPlan("unknown_plan"))
    }

    @Test
    fun weekdayNameResMapsAllWeekdays() {
        val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        days.forEach { dayKey ->
            val resId = ExerciseCatalog.weekdayNameRes(dayKey)
            assert(resId != 0) { "Resource id for $dayKey must not be 0" }
        }
    }
}
