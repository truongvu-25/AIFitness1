package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class FitnessApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseConfig.initialize(this)
        // Create Notification Channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Workout Reminders"
            val descriptionText = "Notifications to remind you to work out at 8:00 AM"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "workout_reminders"
    }
}
