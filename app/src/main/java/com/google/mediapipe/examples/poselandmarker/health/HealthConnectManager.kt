package com.google.mediapipe.examples.poselandmarker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.mediapipe.examples.poselandmarker.model.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object HealthConnectManager {

    const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return client(context).permissionController
            .getGrantedPermissions()
            .containsAll(permissions)
    }

    suspend fun readTodaySteps(context: Context): Long {
        if (!hasPermissions(context)) return 0L

        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = Instant.now()
        val result = client(context).aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun writeWorkout(context: Context, session: WorkoutSession): Boolean {
        if (!hasPermissions(context) || session.id.isBlank()) return false

        val end = Instant.ofEpochMilli(session.completedAt.coerceAtLeast(1L))
        val start = end.minusSeconds(session.durationSeconds.coerceAtLeast(1).toLong())
        val zoneRules = ZoneId.systemDefault().rules
        val resultLabel = if (session.targetCount > 0) {
            "${session.actualCount}/${session.targetCount} ${session.unit}"
        } else {
            "${session.actualCount} ${session.unit}"
        }
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zoneRules.getOffset(start),
            endTime = end,
            endZoneOffset = zoneRules.getOffset(end),
            metadata = Metadata.manualEntry(
                clientRecordId = "tri_force_${session.id}",
                clientRecordVersion = 1L
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
            title = session.exerciseName.ifBlank { "TRI FORCE Workout" },
            notes = "Kết quả: $resultLabel • Điểm form: ${session.formScore}"
        )
        client(context).insertRecords(listOf(record))
        return true
    }
}
