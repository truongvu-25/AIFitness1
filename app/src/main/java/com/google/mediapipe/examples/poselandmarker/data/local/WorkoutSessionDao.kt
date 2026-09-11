package com.google.mediapipe.examples.poselandmarker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PendingWorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(session: PendingWorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingWorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PendingWorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getRecent(userId: String, limit: Int = 100): List<PendingWorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND (firestoreSynced = 0 OR healthSynced = 0) ORDER BY completedAt ASC")
    suspend fun getPending(userId: String): List<PendingWorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET firestoreSynced = 1, lastSyncError = '' WHERE id = :id AND difficulty = :difficulty")
    suspend fun markFirestoreSynced(id: String, difficulty: String)

    @Query("UPDATE workout_sessions SET healthSynced = 1 WHERE id = :id")
    suspend fun markHealthSynced(id: String)

    @Query("UPDATE workout_sessions SET difficulty = :difficulty, firestoreSynced = 0 WHERE id = :id")
    suspend fun updateDifficulty(id: String, difficulty: String)

    @Query("UPDATE workout_sessions SET lastSyncError = :message WHERE id = :id")
    suspend fun setSyncError(id: String, message: String)
}
