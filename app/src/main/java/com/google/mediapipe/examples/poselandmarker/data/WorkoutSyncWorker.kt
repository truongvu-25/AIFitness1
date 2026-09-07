package com.google.mediapipe.examples.poselandmarker.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.mediapipe.examples.poselandmarker.data.local.TriForceDatabase
import com.google.mediapipe.examples.poselandmarker.data.local.toModel
import com.google.mediapipe.examples.poselandmarker.health.HealthConnectManager
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay
import kotlinx.coroutines.tasks.await

class WorkoutSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val dao = TriForceDatabase.getInstance(applicationContext).workoutSessionDao()
        val pending = dao.getPending(uid)
        var cloudFailed = false
        var healthFailed = false

        pending.forEach { local ->
            val session = local.toModel()
            if (!local.firestoreSynced) {
                runCatching { syncToFirestore(uid, session) }
                    .onSuccess { dao.markFirestoreSynced(local.id) }
                    .onFailure {
                        cloudFailed = true
                        dao.setSyncError(local.id, it.localizedMessage.orEmpty().take(300))
                    }
            }

            val canWriteHealth = runCatching {
                HealthConnectManager.hasPermissions(applicationContext)
            }.getOrDefault(false)
            if (!local.healthSynced && canWriteHealth) {
                runCatching { HealthConnectManager.writeWorkout(applicationContext, session) }
                    .onSuccess { written -> if (written) dao.markHealthSynced(local.id) }
                    .onFailure { healthFailed = true }
            }
        }
        return when {
            !cloudFailed && !healthFailed -> Result.success()
            runAttemptCount >= MAX_RETRY_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }
    }

    private suspend fun syncToFirestore(uid: String, session: com.google.mediapipe.examples.poselandmarker.model.WorkoutSession) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)
        val sessionRef = userRef.collection("workout_sessions").document(session.id)
        val dayRef = userRef.collection("workouts").document("day_${session.dayIndex}")
        val historyRef = userRef.collection("exercise_history").document(session.exerciseId)

        db.runTransaction { transaction ->
            val existingSession = transaction.get(sessionRef)
            val daySnapshot = transaction.get(dayRef)
            val historySnapshot = transaction.get(historyRef)

            if (existingSession.exists()) {
                if (session.difficulty.isNotBlank()) {
                    transaction.set(
                        sessionRef,
                        mapOf("difficulty" to session.difficulty),
                        SetOptions.merge()
                    )
                    transaction.set(
                        historyRef,
                        mapOf("lastDifficulty" to session.difficulty),
                        SetOptions.merge()
                    )
                }
                return@runTransaction null
            }

            daySnapshot.toObject(WorkoutDay::class.java)?.let { day ->
                val updated = day.exercises.map { exercise ->
                    if (exercise.exerciseId == session.exerciseId) exercise.copy(status = 1)
                    else exercise
                }
                transaction.update(dayRef, "exercises", updated)
            }
            transaction.set(sessionRef, session)
            transaction.set(
                historyRef,
                mapOf(
                    "exerciseId" to session.exerciseId,
                    "exerciseName" to session.exerciseName,
                    "lastActualCount" to session.actualCount,
                    "lastFormScore" to session.formScore,
                    "lastCompletedAt" to session.completedAt,
                    "lastDifficulty" to session.difficulty,
                    "totalSessions" to ((historySnapshot.getLong("totalSessions") ?: 0L) + 1L)
                ),
                SetOptions.merge()
            )
            null
        }.await()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
