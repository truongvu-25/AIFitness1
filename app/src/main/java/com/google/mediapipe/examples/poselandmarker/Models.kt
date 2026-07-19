package com.google.mediapipe.examples.poselandmarker

// Master static exercise details stored in global Firestore collection 'exercises'
data class ExerciseDetails(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val isTimed: Boolean = false,
    val unit: String = "lần"
)

// Relational exercise log saved per user per day in sub-collection 'workouts'
data class UserExercise(
    val exerciseId: String = "",
    val targetCount: Int = 0,
    val status: Int = 0 // 0: Pending, 1: Completed
)

// Daily workout plan container saved per day in sub-collection 'workouts'
data class WorkoutDay(
    val dayIndex: Int = 0,
    val exercises: List<UserExercise> = emptyList(),
    val isRestDay: Boolean = false
)

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
data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val age: Int = 0,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val bmi: Double = 0.0,
    val bmiType: String = "",
    val createdTime: Long = 0L,
    val lastBmiUpdatedTime: Long = 0L
)
