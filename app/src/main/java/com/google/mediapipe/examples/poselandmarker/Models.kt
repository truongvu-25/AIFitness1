package com.google.mediapipe.examples.poselandmarker

data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val age: Int = 0,
    val height: Double = 0.0, // in cm
    val weight: Double = 0.0, // in kg
    val bmi: Double = 0.0,
    val bmiType: String = "", // GAY, CAN DOI, THUA CAN
    val createdTime: Long = 0L
)

data class Exercise(
    val id: String = "",
    val name: String = "",
    val targetCount: Int = 0,
    var status: Int = 0, // 0: Pending, 1: Completed
    val videoUrl: String = "",
    val description: String = ""
)

data class WorkoutDay(
    val dayIndex: Int = 0, // 1 to 30
    val exercises: List<Exercise> = emptyList()
)
