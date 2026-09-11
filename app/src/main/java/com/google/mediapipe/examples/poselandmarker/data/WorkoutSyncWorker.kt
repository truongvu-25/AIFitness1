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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

        val canWriteHealth = try {
            HealthConnectManager.hasPermissions(applicationContext)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            healthFailed = true
            false
        }
        pending.forEach { local ->
            currentCoroutineContext().ensureActive()
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
            val session = local.toModel()
            if (!local.firestoreSynced) {
                runCatching { syncToFirestore(uid, session) }
                    .onSuccess { dao.markFirestoreSynced(local.id, local.difficulty) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        cloudFailed = true
                        dao.setSyncError(local.id, it.localizedMessage.orEmpty().take(300))
                    }
            }

            if (!local.healthSynced && canWriteHealth) {
                runCatching { HealthConnectManager.writeWorkout(applicationContext, session) }
                    .onSuccess { written -> if (written) dao.markHealthSynced(local.id) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        healthFailed = true
                    }
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
            val profileSnapshot = transaction.get(userRef)
            val existingSession = transaction.get(sessionRef)
            val daySnapshot = transaction.get(dayRef)
            val historySnapshot = transaction.get(historyRef)

            val isLatest = session.completedAt >= (historySnapshot.getLong("lastCompletedAt") ?: 0L)
            if (!existingSession.getString("exerciseId").isNullOrBlank()) {
                if (session.difficulty.isNotBlank()) {
                    transaction.set(
                        sessionRef,
                        mapOf("difficulty" to session.difficulty),
                        SetOptions.merge()
                    )
                    if (isLatest) transaction.set(
                        historyRef,
                        mapOf("lastDifficulty" to session.difficulty),
                        SetOptions.merge()
                    )
                }
                return@runTransaction null
            }

            daySnapshot.toObject(WorkoutDay::class.java)?.let { day ->
                val planStartedAt = profileSnapshot.getLong("createdTime") ?: 0L
                val updated = day.exercises.map { exercise ->
                    if (session.completedAt >= planStartedAt && exercise.exerciseId == session.exerciseId) exercise.copy(status = 1)
                    else exercise
                }
                transaction.update(dayRef, "exercises", updated)
            }
            transaction.set(sessionRef, session)
            val historyUpdates = mutableMapOf<String, Any>(
                "totalSessions" to ((historySnapshot.getLong("totalSessions") ?: 0L) + 1L)
            )
            if (isLatest) historyUpdates.putAll(mapOf(
                "exerciseId" to session.exerciseId,
                "exerciseName" to session.exerciseName,
                "lastActualCount" to session.actualCount,
                "lastFormScore" to session.formScore,
                "lastCompletedAt" to session.completedAt,
                "lastDifficulty" to session.difficulty
            ))
            transaction.set(historyRef, historyUpdates, SetOptions.merge())
            null
        }.await()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
