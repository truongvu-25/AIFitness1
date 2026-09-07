package com.google.mediapipe.examples.poselandmarker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.mediapipe.examples.poselandmarker.model.WorkoutSession
import org.json.JSONArray

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["userId", "completedAt"]),
        Index(value = ["userId", "firestoreSynced", "healthSynced"])
    ]
)
data class PendingWorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val exerciseId: String,
    val exerciseName: String,
    val dayIndex: Int,
    val targetCount: Int,
    val actualCount: Int,
    val unit: String,
    val durationSeconds: Int,
    val formScore: Int,
    val formIssuesJson: String,
    val difficulty: String,
    val completedAutomatically: Boolean,
    val completedAt: Long,
    val firestoreSynced: Boolean = false,
    val healthSynced: Boolean = false,
    val lastSyncError: String = ""
)

fun WorkoutSession.toLocalEntity(
    userId: String,
    firestoreSynced: Boolean = false,
    healthSynced: Boolean = false
) = PendingWorkoutSessionEntity(
    id = id,
    userId = userId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    dayIndex = dayIndex,
    targetCount = targetCount,
    actualCount = actualCount,
    unit = unit,
    durationSeconds = durationSeconds,
    formScore = formScore,
    formIssuesJson = JSONArray(formIssues).toString(),
    difficulty = difficulty,
    completedAutomatically = completedAutomatically,
    completedAt = completedAt,
    firestoreSynced = firestoreSynced,
    healthSynced = healthSynced
)

fun PendingWorkoutSessionEntity.toModel(): WorkoutSession {
    val issues = runCatching {
        val array = JSONArray(formIssuesJson)
        List(array.length()) { index -> array.optString(index) }
    }.getOrDefault(emptyList())
    return WorkoutSession(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        dayIndex = dayIndex,
        targetCount = targetCount,
        actualCount = actualCount,
        unit = unit,
        durationSeconds = durationSeconds,
        formScore = formScore,
        formIssues = issues,
        difficulty = difficulty,
        completedAutomatically = completedAutomatically,
        completedAt = completedAt
    )
}
