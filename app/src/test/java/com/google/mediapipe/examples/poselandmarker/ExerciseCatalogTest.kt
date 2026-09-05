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
}
