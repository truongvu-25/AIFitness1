package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.config.FirebaseConfig
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper

class FitnessApplication : Application() {

    companion object {
        const val CHANNEL_ID = "workout_reminder_channel"
        private const val TAG = "FitnessApplication"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseConfig.initialize(this)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = getString(R.string.notification_workout_channel)
                val descriptionText = getString(R.string.notification_workout_channel_description)
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
