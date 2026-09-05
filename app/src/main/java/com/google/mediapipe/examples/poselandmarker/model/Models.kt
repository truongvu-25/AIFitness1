package com.google.mediapipe.examples.poselandmarker.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

// Master static exercise details stored in global Firestore collection 'exercises'
@IgnoreExtraProperties
data class ExerciseDetails(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val videoUrl: String = "",
    @field:PropertyName("isTimed")
    @field:JvmField
    var isTimed: Boolean = false,
    val unit: String = "lần"
) {
    @get:PropertyName("timed")
    @set:PropertyName("timed")
    var timed: Boolean
        get() = isTimed
        set(value) { isTimed = value }
}

// Relational exercise log saved per user per day in sub-collection 'workouts'
@IgnoreExtraProperties
data class UserExercise(
    val exerciseId: String = "",
    val targetCount: Int = 0,
    val status: Int = 0 // 0: Pending, 1: Completed
)

// Daily workout plan container saved per day in sub-collection 'workouts'
@IgnoreExtraProperties
data class WorkoutDay(
    val dayIndex: Int = 0,
    val exercises: List<UserExercise> = emptyList(),
    @field:PropertyName("isRestDay")
    @field:JvmField
    var isRestDay: Boolean = false
) {
    @get:PropertyName("restDay")
    @set:PropertyName("restDay")
    var restDay: Boolean
        get() = isRestDay
        set(value) { isRestDay = value }
}

// Helper class to map relational data for the UI adapter and camera
data class Exercise(
    val id: String = "",
    val name: String = "",
    val targetCount: Int = 0,
    val status: Int = 0,
    val description: String = "",
    val videoUrl: String = "",
    val isTimed: Boolean = false,
    val unit: String = "lần"
)

// User Profile model stored in 'users' collection
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val age: Int = 0,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val bmi: Double = 0.0,
    val bmiType: String = "",
    val createdTime: Long = 0L,
    val lastBmiUpdatedTime: Long = 0L,
    val customPlanName: String = "",
    val activeCustomPlanJson: String = "",
    val fitnessLevel: String = "",
    val goals: List<String> = emptyList(),
    val pullupsRange: String = "",
    val pushupsRange: String = "",
    val squatsRange: String = ""
)
