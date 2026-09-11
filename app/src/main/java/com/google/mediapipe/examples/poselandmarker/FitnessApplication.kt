package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.config.FirebaseConfig
import com.google.mediapipe.examples.poselandmarker.data.WorkoutSyncScheduler
import com.google.mediapipe.examples.poselandmarker.voice.VoiceCoachManager

class FitnessApplication : Application() {

    companion object {
        const val CHANNEL_ID = "workout_reminder_channel"
        private const val TAG = "FitnessApplication"
    }

    override fun onCreate() {
        super.onCreate()
        VoiceCoachManager.initialize(this)
        try {
            FirebaseConfig.initialize(this)
            createNotificationChannel()
            WorkoutSyncScheduler.enqueue(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Nhắc nhở tập luyện"
                val descriptionText = "Nhắc nhở bài tập hàng ngày lúc 8:00 sáng"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel", e)
        }
    }

}
