package com.google.mediapipe.examples.poselandmarker.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.examples.poselandmarker.FitnessApplication
import com.google.mediapipe.examples.poselandmarker.MainActivity
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.model.UserProfile
import com.google.mediapipe.examples.poselandmarker.model.WorkoutDay

class WorkoutReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Reschedule alarm for the next day
        NotificationHelper.scheduleDailyReminder(context)

        val pendingResult = goAsync()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val profile = document.toObject(UserProfile::class.java)
                        if (profile != null) {
                            val createdTime = profile.createdTime
                            val diffMs = System.currentTimeMillis() - createdTime
                            val dayIndex = (diffMs / (24 * 60 * 60 * 1000)).toInt() + 1

                            if (dayIndex in 1..30) {
                                // Fetch workout list for the calculated dayIndex
                                db.collection("users").document(currentUser.uid)
                                    .collection("workouts").document("day_$dayIndex").get()
                                    .addOnSuccessListener { workoutDoc ->
                                        if (workoutDoc.exists()) {
                                            val workoutDay = workoutDoc.toObject(WorkoutDay::class.java)
                                            val hasPending = workoutDay?.exercises?.any { it.status == 0 } ?: false
                                            if (hasPending) {
                                                showNotification(context, dayIndex)
                                            }
                                        }
                                        pendingResult.finish()
                                    }
                                    .addOnFailureListener {
                                        pendingResult.finish()
                                    }
                            } else {
                                pendingResult.finish()
                            }
                        } else {
                            pendingResult.finish()
                        }
                    } else {
                        pendingResult.finish()
                    }
                }
                .addOnFailureListener {
                    pendingResult.finish()
                }
        } else {
            pendingResult.finish()
        }
    }

    private fun showNotification(context: Context, dayIndex: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open MainActivity when clicking notification
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, flags)

        val notification = NotificationCompat.Builder(context, FitnessApplication.Companion.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_workout_title, dayIndex))
            .setContentText(context.getString(R.string.notification_workout_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
