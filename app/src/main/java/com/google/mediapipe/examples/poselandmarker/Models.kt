package com.google.mediapipe.examples.poselandmarker

data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val age: Int = 0,
    val height: Double = 0.0, // in cm
    val weight: Double = 0.0, // in kg
    val bmi: Double = 0.0,
    val bmiType: String = "", // GAY, CAN DOI, THUA CAN
    val createdTime: Long = 0L,
    val lastBmiUpdatedTime: Long = 0L
)

// Master static database entry for exercises in global 'exercises' collection
data class ExerciseDetails(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val videoUrl: String = ""
)

// User-specific progress entry for each workout day
data class UserExercise(
    val exerciseId: String = "",
    val targetCount: Int = 0,
    var status: Int = 0 // 0: Pending, 1: Completed
)

data class WorkoutDay(
    val dayIndex: Int = 0,
    val exercises: List<UserExercise> = emptyList()
)

// Helper class to map relational data for the UI adapter and camera
data class Exercise(
    val id: String = "",
    val name: String = "",
    val targetCount: Int = 0,
    var status: Int = 0, // 0: Pending, 1: Completed
    val description: String = "",
    val videoUrl: String = ""
)
