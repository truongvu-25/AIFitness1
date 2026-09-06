package com.google.mediapipe.examples.poselandmarker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingWorkoutSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TriForceDatabase : RoomDatabase() {
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile private var instance: TriForceDatabase? = null

        fun getInstance(context: Context): TriForceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TriForceDatabase::class.java,
                    "tri_force_offline.db"
                ).build().also { instance = it }
            }
    }
}
